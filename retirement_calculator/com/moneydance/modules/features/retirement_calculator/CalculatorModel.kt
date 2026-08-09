package com.moneydance.modules.features.retirement_calculator

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.cos
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 1. Domain structures & Constants
data class TaxBracket(val rate: Double, val maxIncome: Double)

data class StockLot(
    var name: String,
    var costBasis: Double,
    var currentVal: Double
) {
    val basisRatio: Double
        get() = if (currentVal > 0.0) costBasis / currentVal else 0.0
}

val TAX_RATES_FED = listOf(
    TaxBracket(0.10, 24800.0),
    TaxBracket(0.12, 100800.0),
    TaxBracket(0.22, 211400.0),
    TaxBracket(0.24, 403550.0),
    TaxBracket(0.32, 512450.0),
    TaxBracket(0.35, 768700.0),
    TaxBracket(0.37, Double.MAX_VALUE)
)

val CAP_GAINS_RATES_FED = listOf(
    TaxBracket(0.0, 98900.0),
    TaxBracket(0.15, 613700.0),
    TaxBracket(0.20, Double.MAX_VALUE)
)

val SS_BRACKETS = listOf(
    TaxBracket(0.0, 32000.0),
    TaxBracket(0.50, 44000.0),
    TaxBracket(0.85, Double.MAX_VALUE)
)

const val STD_DEDUCTION = 32200.0
const val OBBBA_DEDUCTION = 12000.0
const val DIVIDEND_RATE = 0.01
const val INTEREST_RATE = 0.03
const val MAJOR_GUARDRAIL_VIOLATION = 0.2
const val YOUNG_PENSION_AGE = 60.0
const val OLD_PENSION_AGE = 70.0
const val MILLISEC_PER_YEAR = 1000.0 * 60 * 60 * 24 * 365.25

// Date conversion extensions
fun LocalDate.toDate(): Date = Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
fun Date.toLocalDate(): LocalDate = this.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

fun getYearsDifference(d1: LocalDate, d2: LocalDate): Int {
    val start = if (d1.isBefore(d2)) d1 else d2
    val end = if (d1.isBefore(d2)) d2 else d1
    return ChronoUnit.YEARS.between(start, end).toInt()
}

fun addFractionalYears(date: LocalDate, years: Double): LocalDate {
    val wholeYears = years.toInt()
    val testDate = date.plusYears(wholeYears.toLong())
    val testMs = testDate.toDate().time
    val fractional = years - wholeYears
    val msToAdd = (fractional * MILLISEC_PER_YEAR).toLong()
    return Date(testMs + msToAdd).toLocalDate()
}

// Map parsers
fun Map<String, String>.getDouble(key: String, default: Double = 0.0): Double {
    return this[key]?.toDoubleOrNull() ?: default
}

fun Map<String, String>.getInt(key: String, default: Int = 0): Int {
    return this[key]?.toIntOrNull() ?: default
}

fun Map<String, String>.getLocalDate(key: String, default: LocalDate = LocalDate.now()): LocalDate {
    val s = this[key] ?: return default
    return try {
        if (s.contains("/")) {
            val parts = s.split("/")
            if (parts.size == 3) {
                val m = parts[0].toInt()
                val d = parts[1].toInt()
                val y = parts[2].toInt()
                LocalDate.of(y, m, d)
            } else default
        } else {
            LocalDate.parse(s)
        }
    } catch (e: Exception) {
        default
    }
}

fun Map<String, String>.getBoolean(key: String, default: Boolean = false): Boolean {
    return this[key]?.toBoolean() ?: default
}

class YearRow(val formData: Map<String, String>, val previousYear: YearRow?) {
    val year: Int
    val yearStartDate: LocalDate
    val yearEndDate: LocalDate
    val yearStartTime: Long
    val yearEndTime: Long
    
    var inflationAdjustmentFactor: Double = 1.0

    val birthDateSelf: LocalDate
    val birthDateSpouse: LocalDate
    
    val ageSelf: Int
    val ageSpouse: Int
    val ageOldest: Int

    val retirementAgeSelf: Double
    val retirementAgeSpouse: Double
    val retirementDateSelf: LocalDate
    val retirementDateSpouse: LocalDate

    val piaSelf: Double
    val piaSpouse: Double
    val socSecStartAgeSelf: Double
    val socSecStartAgeSpouse: Double
    val socSecStartDateSelf: LocalDate
    val socSecStartDateSpouse: LocalDate

    val pensionStartAgeSelf: Double
    val pensionStartAgeSpouse: Double
    val pensionStartDateSelf: LocalDate
    val pensionStartDateSpouse: LocalDate

    val investReturnPct: Double
    val inflationPct: Double
    val raisePct: Double
    val guardrailPct: Double
    val dafExcessPct: Double

    var iraSavings: Double = 0.0
    var rothSavings: Double = 0.0
    var taxableSavings: Double = 0.0
    var taxableCostBasis: Double = 0.0
    var dafSavings: Double = 0.0

    var iraCash: Double = 0.0
    var rothCash: Double = 0.0
    var taxableCash: Double = 0.0

    var taxableLots = mutableListOf<StockLot>()

    var iraSavingsEnd: Double = 0.0
    var iraCashEnd: Double = 0.0
    var rothSavingsEnd: Double = 0.0
    var rothCashEnd: Double = 0.0
    var taxableSavingsEnd: Double = 0.0
    var taxableCashEnd: Double = 0.0
    var taxableCostBasisEnd: Double = 0.0
    var taxableLotsEnd = mutableListOf<StockLot>()
    var taxableLotsStart = mutableListOf<StockLot>()
    var dafSavingsEnd: Double = 0.0
    val actionLogs = mutableListOf<String>()

    var iraDistribution: Double = 0.0
    var rothDistribution: Double = 0.0
    var taxableDistribution: Double = 0.0
    var dafDistribution: Double = 0.0
    var dafContribution: Double = 0.0
    var rothConversion: Double = 0.0
    var realizedGain: Double = 0.0
    var fedTaxableSocialSecurity: Double = 0.0

    var salarySelf: Double = 0.0
    var salarySpouse: Double = 0.0
    var socSecSelf: Double = 0.0
    var socSecSpouse: Double = 0.0
    var pensionSelf: Double = 0.0
    var pensionSpouse: Double = 0.0
    var dividends: Double = 0.0
    var interest: Double = 0.0

    var otherExpenses: Double = 0.0
    var mortgage: Double = 0.0
    var elderCare: Double = 0.0
    var travel: Double = 0.0

    var percentBelowGuardrail: Double = 0.0
    var guardrailAdjustmentMessage: String? = null
    var fedOrdinaryBracketRate: Double = 0.0
    var fedOrdinaryBracketLimit: Double = 0.0
    var fedCapGainsBracketRate: Double = 0.0
    var fedCapGainsBracketLimit: Double = 0.0
    var fedTaxableOrdinaryIncome: Double = 0.0
    var fedTotalTaxableIncome: Double = 0.0

    var standardDeduction: Double = 0.0
    var payrollTaxes: Double = 0.0
    var propertyTaxes: Double = 0.0
    var fedTaxes: Double = 0.0
    var stateTaxes: Double = 0.0
    var ordinaryTax: Double = 0.0
    var capitalGainsTax: Double = 0.0
    var taxes: Double = 0.0
    var taxLimitReason: String? = null

    // For display / graph comparisons
    var bySsStart: Double? = null
    var byPensionStart: Double? = null
    var bySsSelfStart: Double? = null
    var bySsSpouseStart: Double? = null
    var byPensionSelfStart: Double? = null
    var byPensionSpouseStart: Double? = null

    init {
        // Initialize dates
        if (previousYear == null) {
            year = formData.getInt("start_year", 2026)
        } else {
            year = previousYear.year + 1
        }
        
        yearStartDate = LocalDate.of(year, 1, 1)
        yearEndDate = LocalDate.of(year, 12, 31)
        yearStartTime = yearStartDate.toDate().time
        yearEndTime = yearEndDate.toDate().time + 86399999L // include last day fully
        
        calcInflationAdjustment()

        birthDateSelf = formData.getLocalDate("birthdate", LocalDate.of(1968, 1, 1))
        birthDateSpouse = formData.getLocalDate("birthdate_spouse", LocalDate.of(1970, 1, 1))
        ageSelf = getYearsDifference(birthDateSelf, yearEndDate)
        ageSpouse = getYearsDifference(birthDateSpouse, yearEndDate)
        ageOldest = max(ageSelf, ageSpouse)

        retirementAgeSelf = formData.getDouble("retirement_age", 62.0)
        retirementAgeSpouse = formData.getDouble("retirement_age_spouse", 63.0)
        retirementDateSelf = addFractionalYears(birthDateSelf, retirementAgeSelf)
        retirementDateSpouse = addFractionalYears(birthDateSpouse, retirementAgeSpouse)

        piaSelf = formData.getDouble("ss_pia", 1000.0)
        piaSpouse = formData.getDouble("ss_pia_spouse", 1000.0)
        socSecStartAgeSelf = formData.getDouble("ss_age", 67.0)
        socSecStartAgeSpouse = formData.getDouble("ss_age_spouse", 67.0)
        socSecStartDateSelf = addFractionalYears(birthDateSelf, socSecStartAgeSelf)
        socSecStartDateSpouse = addFractionalYears(birthDateSpouse, socSecStartAgeSpouse)

        pensionStartAgeSelf = formData.getDouble("pension_age", 67.0)
        pensionStartAgeSpouse = formData.getDouble("pension_age_spouse", 67.0)
        pensionStartDateSelf = addFractionalYears(birthDateSelf, pensionStartAgeSelf)
        pensionStartDateSpouse = addFractionalYears(birthDateSpouse, pensionStartAgeSpouse)

        val baseInvestReturn = formData.getDouble("investment_return", 6.0) / 100.0
        investReturnPct = if (previousYear == null) {
            val asOfDate = formData.getLocalDate("as_of_date", LocalDate.of(year, 1, 1))
            val startMs = asOfDate.toDate().time
            val endMs = yearEndDate.toDate().time + 86400000L
            val fraction = min(1.0, max(0.0, (endMs - startMs).toDouble() / MILLISEC_PER_YEAR))
            baseInvestReturn * fraction
        } else {
            baseInvestReturn
        }
        inflationPct = formData.getDouble("inflation", 2.25) / 100.0
        raisePct = formData.getDouble("raise", 3.0) / 100.0
        guardrailPct = formData.getDouble("guardrail_percent", 10.0) / 100.0

        val excessStr = formData["daf_excess_pct"]
        dafExcessPct = if (excessStr != null && excessStr.isNotEmpty()) excessStr.toDouble() / 100.0 else 0.5

        if (previousYear == null) {
            iraSavings = formData.getDouble("start_ira_savings", 1000000.0)
            rothSavings = formData.getDouble("start_roth_savings", 1000000.0)
            taxableSavings = formData.getDouble("start_other_savings", 1000000.0)
            
            val initBasis = formData["start_taxable_cost_basis"]
            if (initBasis != null && initBasis.isNotEmpty()) {
                taxableCostBasis = min(taxableSavings, initBasis.toDouble())
            } else {
                taxableCostBasis = taxableSavings * 0.5
            }
            dafSavings = formData.getDouble("start_daf_savings", 0.0)
            dafDistribution = formData.getDouble("daf_distro", 0.0)
            
            iraCash = formData.getDouble("start_ira_cash", iraSavings * 0.10)
            rothCash = formData.getDouble("start_roth_cash", rothSavings * 0.10)
            taxableCash = formData.getDouble("start_other_cash", taxableSavings * 0.10)
            
            iraCash = max(0.0, min(iraSavings, iraCash))
            rothCash = max(0.0, min(rothSavings, rothCash))
            taxableCash = max(0.0, min(taxableSavings, taxableCash))

            val lotsStr = formData["start_taxable_lots"]
            if (lotsStr != null && lotsStr.isNotEmpty()) {
                val lotParts = lotsStr.split(";")
                for (part in lotParts) {
                    val subParts = part.split(",")
                    if (subParts.size == 3) {
                        val decodedName = try {
                            java.net.URLDecoder.decode(subParts[0], "UTF-8")
                        } catch (e: Exception) {
                            "Stock"
                        }
                        val basis = subParts[1].toDoubleOrNull() ?: 0.0
                        val value = subParts[2].toDoubleOrNull() ?: 0.0
                        if (value > 0.0) {
                            taxableLots.add(StockLot(name = decodedName, costBasis = basis, currentVal = value))
                        }
                    } else if (subParts.size == 2) {
                        val basis = subParts[0].toDoubleOrNull() ?: 0.0
                        val value = subParts[1].toDoubleOrNull() ?: 0.0
                        if (value > 0.0) {
                            taxableLots.add(StockLot(name = "Stock Lot", costBasis = basis, currentVal = value))
                        }
                    }
                }
            }
            val stockVal = max(0.0, taxableSavings - taxableCash)
            val stockBasis = max(0.0, taxableCostBasis - taxableCash)
            if (taxableLots.isEmpty() && stockVal > 0.0) {
                val lotVal = stockVal / 5.0
                val avgRatio = if (stockVal > 0.0) stockBasis / stockVal else 0.5
                val ratios = listOf(0.4, 0.7, 1.0, 1.3, 1.6)
                val lotNames = listOf("Stock Lot A", "Stock Lot B", "Stock Lot C", "Stock Lot D", "Stock Lot E")
                var basisSum = 0.0
                for (i in ratios.indices) {
                    val rMultiplier = ratios[i]
                    val ratio = max(0.05, min(1.0, avgRatio * rMultiplier))
                    val basis = lotVal * ratio
                    taxableLots.add(StockLot(name = lotNames[i], costBasis = basis, currentVal = lotVal))
                    basisSum += basis
                }
                if (basisSum > 0.0) {
                    val scale = stockBasis / basisSum
                    for (lot in taxableLots) {
                        lot.costBasis = max(0.0, min(lot.currentVal, lot.costBasis * scale))
                    }
                }
            }
        } else {
            iraSavings = previousYear.iraSavingsEnd
            iraCash = previousYear.iraCashEnd
            rothSavings = previousYear.rothSavingsEnd
            rothCash = previousYear.rothCashEnd
            taxableSavings = previousYear.taxableSavingsEnd
            taxableCash = previousYear.taxableCashEnd
            taxableCostBasis = previousYear.taxableCostBasisEnd
            dafSavings = previousYear.dafSavingsEnd
            dafDistribution = previousYear.dafDistribution * (1.0 + previousYear.inflationPct)

            // Clone and grow previous year's stock lots
            for (prevLot in previousYear.taxableLotsEnd) {
                taxableLots.add(StockLot(name = prevLot.name, costBasis = prevLot.costBasis, currentVal = prevLot.currentVal * (1.0 + investReturnPct)))
            }
        }

        val maxDafSavings = dafSavings * (1.0 + investReturnPct)
        dafDistribution = min(dafDistribution, maxDafSavings)

        // Calculate incomes
        calculateBaseIncome(previousYear)
        
        // Calculate guardrail spending impacts
        applySpendingGuardrails(formData)

        // Calculate taxes
        payrollTaxes = (salarySelf + salarySpouse) * formData.getDouble("payroll_tax_rate", 7.65) / 100.0
        if (previousYear == null) {
            propertyTaxes = formData.getDouble("prop_taxes", 8000.0)
        } else {
            propertyTaxes = previousYear.propertyTaxes * (1.0 + previousYear.inflationPct)
        }

        standardDeduction = STD_DEDUCTION * inflationAdjustmentFactor + (if (ageOldest >= 65) OBBBA_DEDUCTION else 0.0)

        calcTaxes()
    }

    private fun calcInflationAdjustment(): Double {
        var factor = 1.0
        var cur = previousYear
        while (cur != null) {
            factor *= (1.0 + cur.inflationPct)
            cur = cur.previousYear
        }
        inflationAdjustmentFactor = factor
        return factor
    }

    private fun calculateBaseIncome(previousYear: YearRow?) {
        if (previousYear == null) {
            salarySelf = formData.getDouble("salary", 60000.0) * fractionOfYearBefore(retirementDateSelf)
            salarySpouse = formData.getDouble("salary_spouse", 30000.0) * fractionOfYearBefore(retirementDateSpouse)
        } else {
            salarySelf = previousYear.salarySelf * (1.0 + previousYear.raisePct) * fractionOfYearBefore(retirementDateSelf)
            salarySpouse = previousYear.salarySpouse * (1.0 + previousYear.raisePct) * fractionOfYearBefore(retirementDateSpouse)
        }

        socSecSelf = calcBaseSocSecIncome(true)
        socSecSpouse = calcBaseSocSecIncome(false)

        if (socSecSelf > 0.0 && socSecSpouse > 0.0) {
            val bothStartDate = if (socSecStartDateSelf.isAfter(socSecStartDateSpouse)) socSecStartDateSelf else socSecStartDateSpouse
            val topUpSpouse = calculateSpousalTopUp(piaSelf, piaSpouse, socSecSpouse, birthDateSpouse, bothStartDate)
            if (topUpSpouse > 0.0) {
                socSecSpouse += topUpSpouse
            } else {
                val topUpSelf = calculateSpousalTopUp(piaSpouse, piaSelf, socSecSelf, birthDateSelf, bothStartDate)
                if (topUpSelf > 0.0) {
                    socSecSelf += topUpSelf
                }
            }
        }

        pensionSelf = calcBasePensionIncome(true)
        pensionSpouse = calcBasePensionIncome(false)
        val iraInterest = iraCash * INTEREST_RATE
        val rothInterest = rothCash * INTEREST_RATE
        val taxableInterest = taxableCash * INTEREST_RATE
        interest = iraInterest + rothInterest + taxableInterest

        val iraNonCash = max(0.0, iraSavings - iraCash)
        val rothNonCash = max(0.0, rothSavings - rothCash)
        val taxableNonCash = max(0.0, taxableSavings - taxableCash)
        dividends = (iraNonCash * DIVIDEND_RATE) + (rothNonCash * DIVIDEND_RATE) + (taxableNonCash * DIVIDEND_RATE)
    }

    private fun calculateSpousalTopUp(primaryPia: Double, secondaryPia: Double, secondaryBenefit: Double, birthDate: LocalDate, startDate: LocalDate): Double {
        if (primaryPia * 0.5 > max(secondaryPia, secondaryBenefit / 12.0)) {
            var topUp = primaryPia * 0.5 - secondaryPia
            val spousalBenefitStartAge = getMsDifference(startDate, birthDate) / MILLISEC_PER_YEAR
            val ageDiff = Math.round((spousalBenefitStartAge - 67.0) * 12.0).toInt()
            if (ageDiff < 0) {
                val reductionMonths = min(-ageDiff, 36)
                var reduction = reductionMonths * 25.0 / 36.0
                if (-ageDiff > 36) {
                    val extraMonths = -ageDiff - 36
                    reduction += extraMonths * 5.0 / 12.0
                }
                topUp *= (1.0 - reduction / 100.0)
            }
            topUp = min(topUp, primaryPia * 0.5 - secondaryBenefit / 12.0)
            return topUp * 12.0
        }
        return 0.0
    }

    private fun getMsDifference(d1: LocalDate, d2: LocalDate): Double {
        return (d1.toDate().time - d2.toDate().time).toDouble()
    }

    private fun applySpendingGuardrails(formData: Map<String, String>) {
        otherExpenses = formData.getDouble("start_other_spending", 30000.0) * inflationAdjustmentFactor
        val mortgageEnd = formData.getLocalDate("mortgage_end", LocalDate.of(2034, 12, 31))
        val travelEnd = formData.getLocalDate("travel_end", LocalDate.of(2045, 12, 31))
        val elderCareStart = formData.getLocalDate("eldercare_start", LocalDate.of(2050, 1, 1))

        mortgage = formData.getDouble("mortgage", 2000.0) * fractionOfYearBefore(mortgageEnd) * 12.0

        val lastRetirementDate = if (retirementDateSelf.isAfter(retirementDateSpouse)) retirementDateSelf else retirementDateSpouse
        travel = formData.getDouble("travel", 25000.0) * fractionOfYearBefore(travelEnd) * fractionOfYearAfter(lastRetirementDate) * inflationAdjustmentFactor
        
        elderCare = formData.getDouble("eldercare", 10000.0) * fractionOfYearAfter(elderCareStart) * inflationAdjustmentFactor * 12.0

        val lifetime = formData.getInt("lifetime", 100)
        if (ageOldest > lifetime - 3) {
            elderCare *= 4.0
        } else if (ageOldest > lifetime - 8) {
            elderCare *= 2.0
        }

        val isRetired = yearEndDate.isAfter(retirementDateSelf) && yearEndDate.isAfter(retirementDateSpouse)
        percentBelowGuardrail = 0.0
        if (isRetired && guardrailPct >= 0.0) {
            val totalsavings = iraSavings + rothSavings + taxableSavings
            val netCash = mortgage + elderCare + otherExpenses + travel - getOtherIncome() - socSecSelf - socSecSpouse - pensionSelf - pensionSpouse
            val targetSavings = netCash * (1.0 - guardrailPct) / 0.04
            if (totalsavings < targetSavings) {
                percentBelowGuardrail = (targetSavings - totalsavings) / targetSavings
                if (percentBelowGuardrail > MAJOR_GUARDRAIL_VIOLATION) {
                    guardrailAdjustmentMessage = String.format(
                        "Total savings $%,.2f is %.2f%% below guardrail threshold $%,.2f, cutting eldercare in half, travel by 95%%, and other expenses by 25%%.",
                        totalsavings, percentBelowGuardrail * 100.0, targetSavings
                    )
                    travel *= 0.05
                    otherExpenses *= 0.75
                    elderCare *= 0.5
                } else {
                    guardrailAdjustmentMessage = String.format(
                        "Total savings $%,.2f is %.2f%% below guardrail threshold $%,.2f, cutting eldercare in half, travel by 90%%, and other expenses by 10%%.",
                        totalsavings, percentBelowGuardrail * 100.0, targetSavings
                    )
                    travel *= 0.10
                    otherExpenses *= 0.90
                    elderCare *= 0.5
                }
            }
        }
    }

    fun getTotalSavings(): Double {
        return iraSavings + rothSavings + taxableSavings
    }

    private fun calcTaxes() {
        optimizeWithdrawalsForNetCash()
        taxes = fedTaxes + stateTaxes + payrollTaxes + propertyTaxes
    }

    private fun optimizeWithdrawalsForNetCash() {
        val targetNetCash = mortgage + elderCare + otherExpenses + travel
        val estimatedGrossNeeded = targetNetCash + payrollTaxes + propertyTaxes + 5000.0
        val ssSelfPotential = calcPotentialSocSec(true)
        val ssSpousePotential = calcPotentialSocSec(false)
        val pensionSelfPotential = calcPotentialPension(true)
        val pensionSpousePotential = calcPotentialPension(false)
        val cashThreshold = max(0.0, estimatedGrossNeeded - ssSelfPotential - ssSpousePotential - pensionSelfPotential - pensionSpousePotential)
        
        val isRoiPositive = investReturnPct >= 0.0
        
        fun getStartCashAndStock(savings: Double, cashVal: Double): Pair<Double, Double> {
            var cashStart = cashVal
            var nonCashStart = max(0.0, savings - cashVal)
            if (isRoiPositive) {
                val targetCash = min(savings, max(cashThreshold, savings * 0.05))
                val shift = targetCash - cashStart
                cashStart += shift
                nonCashStart -= shift
            } else {
                if (cashStart < cashThreshold && nonCashStart > 0.0) {
                    val toMove = min(nonCashStart, cashThreshold - cashStart)
                    cashStart += toMove
                    nonCashStart -= toMove
                }
            }
            return Pair(cashStart, nonCashStart)
        }
        
        val (iraCS, iraNCS) = getStartCashAndStock(iraSavings, iraCash)
        val (rothCS, rothNCS) = getStartCashAndStock(rothSavings, rothCash)
        val (taxCS, taxNCS) = getStartCashAndStock(taxableSavings, taxableCash)
        
        val iraInterest = iraCS * INTEREST_RATE
        val rothInterest = rothCS * INTEREST_RATE
        val taxableInterest = taxCS * INTEREST_RATE
        
        val iraDividends = iraNCS * DIVIDEND_RATE
        val rothDividends = rothNCS * DIVIDEND_RATE
        val taxableDividends = taxNCS * DIVIDEND_RATE
        
        val iraRoi = iraNCS * investReturnPct
        val rothRoi = rothNCS * investReturnPct
        val taxableRoi = taxNCS * investReturnPct
        
        var iraCashPre = iraCS + iraInterest + iraDividends
        var iraNonCashPre = max(0.0, iraNCS + iraRoi)
        
        var rothCashPre = rothCS + rothInterest + rothDividends
        var rothNonCashPre = max(0.0, rothNCS + rothRoi)
        
        var taxableCashPre = taxCS + taxableInterest + taxableDividends
        var taxableNonCashPre = max(0.0, taxNCS + taxableRoi)
        
        // Increase taxable cost basis by interest and dividends
        taxableCostBasis = min(taxableSavings + taxableInterest + taxableDividends + taxableRoi, taxableCostBasis + taxableInterest + taxableDividends)
        
        val maxTaxable = max(0.0, taxableCashPre + taxableNonCashPre)
        val maxIra = max(0.0, iraCashPre + iraNonCashPre)
        val maxRoth = max(0.0, rothCashPre + rothNonCashPre)
        
        val rmdValue = min(suggestRmd(), maxIra)
        val fixedCash = getOtherIncome() + socSecSelf + socSecSpouse
        
        var currentIRA = rmdValue
        var currentBrokerageSale = max(0.0, estimatedGrossNeeded - rmdValue - fixedCash)
        currentBrokerageSale = min(currentBrokerageSale, maxTaxable)
        var totalTax = 0.0
        var netCash = 0.0

        val loopMaxIra = min(estimatedGrossNeeded, maxIra)
        var ira = rmdValue
        while (ira <= loopMaxIra) {
            var brokerageNeeded = max(0.0, estimatedGrossNeeded - ira - fixedCash)
            brokerageNeeded = min(brokerageNeeded, maxTaxable)
            val stockSold = if (investReturnPct >= 0.0) {
                min(taxableNonCashPre, brokerageNeeded)
            } else {
                max(0.0, brokerageNeeded - taxableCashPre)
            }
            val realizedGain = simulateStockSale(stockSold)

            val result = calculateRetirementTax(ira, realizedGain)

            if (result.capitalGainsTax == 0.0 || ira == rmdValue) {
                currentIRA = ira
                currentBrokerageSale = brokerageNeeded
                totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
                netCash = (ira + currentBrokerageSale + fixedCash) - totalTax
                taxLimitReason = null
            } else if (salarySelf + salarySpouse > 0.0) {
                currentIRA = ira
                currentBrokerageSale = brokerageNeeded
                totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
                netCash = (ira + currentBrokerageSale + fixedCash) - totalTax
                taxLimitReason = "0% Capital Gains Bracket"
                break
            } else if (result.taxableOrdinaryIncome > 0.0) {
                taxLimitReason = "0% Income Tax Bracket"
                break
            } else {
                if (taxLimitReason == null) taxLimitReason = "0% Capital Gains Bracket"
            }
            ira += 100.0
        }

        if (currentIRA >= loopMaxIra - 100.0) {
            taxLimitReason = null
        }

        if (rmdValue > estimatedGrossNeeded) {
            currentIRA = rmdValue
            currentBrokerageSale = 0.0
            val result = calculateRetirementTax(rmdValue, 0.0)
            totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
            netCash = (currentIRA + currentBrokerageSale + fixedCash) - totalTax
        }

        var iterations = 0
        while (netCash < targetNetCash && currentBrokerageSale < maxTaxable) {
            var additionalNeeded = targetNetCash - netCash
            if (currentBrokerageSale + additionalNeeded > maxTaxable) {
                additionalNeeded = maxTaxable - currentBrokerageSale
            }

            currentBrokerageSale += additionalNeeded
            val stockSold = if (investReturnPct >= 0.0) {
                min(taxableNonCashPre, currentBrokerageSale)
            } else {
                max(0.0, currentBrokerageSale - taxableCashPre)
            }
            val realizedGain = simulateStockSale(stockSold)
            val result = calculateRetirementTax(currentIRA, realizedGain)
            totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
            netCash = (currentIRA + currentBrokerageSale + fixedCash) - totalTax

            iterations++
            if (iterations > 20) break
        }

        if (netCash < targetNetCash) {
            val additionalNeeded = targetNetCash - netCash
            val rothPull = min(additionalNeeded, maxRoth)
            rothDistribution += rothPull
        } else {
            rothDistribution = 0.0
        }

        if (netCash + rothDistribution < targetNetCash && currentIRA < maxIra) {
            taxLimitReason = null
            var iraIterations = 0
            val adjustedTarget = targetNetCash - rothDistribution
            while (netCash < adjustedTarget && currentIRA < maxIra) {
                var additionalNeeded = adjustedTarget - netCash
                if (currentIRA + additionalNeeded > maxIra) {
                    additionalNeeded = maxIra - currentIRA
                }
                currentIRA += additionalNeeded
                val stockSold = if (investReturnPct >= 0.0) {
                    min(taxableNonCashPre, currentBrokerageSale)
                } else {
                    max(0.0, currentBrokerageSale - taxableCashPre)
                }
                val realizedGain = simulateStockSale(stockSold)
                val result = calculateRetirementTax(currentIRA, realizedGain)
                totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
                netCash = (currentIRA + currentBrokerageSale + fixedCash) - totalTax

                iraIterations++
                if (iraIterations > 20) break
            }
        }

        rothDistribution = min(rothDistribution, maxRoth)
        currentIRA = min(currentIRA, maxIra)

        if (netCash + rothDistribution < targetNetCash) {
            val additionalNeeded = targetNetCash - (netCash + rothDistribution)
            currentBrokerageSale += additionalNeeded
            netCash += additionalNeeded
        }

        iraDistribution = currentIRA
        taxableDistribution = currentBrokerageSale

        var surplus = 0.0
        dafContribution = 0.0

        for (iter in 0 until 5) {
            val stockSold = if (investReturnPct >= 0.0) {
                min(taxableNonCashPre, taxableDistribution)
            } else {
                max(0.0, taxableDistribution - taxableCashPre)
            }
            val realizedGain = simulateStockSale(stockSold)
            val result = calculateRetirementTax(iraDistribution, realizedGain)
            totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
            netCash = (iraDistribution + taxableDistribution + fixedCash) - totalTax
            surplus = netCash + rothDistribution - targetNetCash

            if (suggestRmd() > 0.0 && surplus > 0.0) {
                dafContribution = surplus * dafExcessPct
            } else {
                dafContribution = 0.0
                break
            }
        }
        
        val finalStockSold = if (investReturnPct >= 0.0) {
            min(taxableNonCashPre, taxableDistribution)
        } else {
            max(0.0, taxableDistribution - taxableCashPre)
        }
        val finalRealizedGain = simulateStockSale(finalStockSold)
        realizedGain = finalRealizedGain
        val finalResult = calculateRetirementTax(iraDistribution, finalRealizedGain)
        
        fedTaxableSocialSecurity = finalResult.taxableSocialSecurity
        fedOrdinaryBracketRate = finalResult.fedOrdinaryBracketRate
        fedOrdinaryBracketLimit = finalResult.fedOrdinaryBracketLimit
        fedCapGainsBracketRate = finalResult.fedCapGainsBracketRate
        fedCapGainsBracketLimit = finalResult.fedCapGainsBracketLimit
        fedTaxableOrdinaryIncome = finalResult.taxableOrdinaryIncome
        fedTotalTaxableIncome = finalResult.taxableOrdinaryIncome + finalRealizedGain + dividends

        if (surplus > 0.0) {
            fundSavings(surplus)
        }
        
        calculateEndingBalances(
            iraCashPre, iraNonCashPre,
            rothCashPre, rothNonCashPre,
            taxableCashPre, taxableNonCashPre,
            taxCS
        )
    }

    private fun simulateStockSale(amountToSell: Double): Double {
        if (amountToSell <= 0.0) return 0.0
        val sortedLots = taxableLots.map { StockLot(it.name, it.costBasis, it.currentVal) }
            .filter { it.currentVal > 0.0 }
            .sortedByDescending { it.basisRatio }
            
        var remaining = amountToSell
        var totalRealizedGain = 0.0
        
        for (lot in sortedLots) {
            if (remaining <= 0.0) break
            val soldFromLot = min(lot.currentVal, remaining)
            val basisRatio = lot.basisRatio
            val realizedBasis = soldFromLot * basisRatio
            val gain = soldFromLot - realizedBasis
            totalRealizedGain += gain
            remaining -= soldFromLot
        }
        if (remaining > 0.0) {
            totalRealizedGain += remaining
        }
        return totalRealizedGain
    }

    private fun calculateEndingBalances(
        iraCashPre: Double, iraNonCashPre: Double,
        rothCashPre: Double, rothNonCashPre: Double,
        taxableCashPre: Double, taxableNonCashPre: Double,
        taxCS: Double
    ) {
        fun applyDist(cashPre: Double, nonCashPre: Double, dist: Double): Pair<Double, Double> {
            if (dist > 0.0) {
                return if (investReturnPct < 0.0) {
                    val cashDep = min(cashPre, dist)
                    val nonCashDep = dist - cashDep
                    Pair(max(0.0, cashPre - cashDep), max(0.0, nonCashPre - nonCashDep))
                } else {
                    val nonCashDep = min(nonCashPre, dist)
                    val cashDep = dist - nonCashDep
                    Pair(max(0.0, cashPre - cashDep), max(0.0, nonCashPre - nonCashDep))
                }
            } else {
                return Pair(cashPre, nonCashPre - dist)
            }
        }
        
        val (iraC, iraN) = applyDist(iraCashPre, iraNonCashPre, iraDistribution)
        iraCashEnd = iraC
        iraSavingsEnd = iraC + iraN
        if (iraDistribution > 0.0) {
            val cashDep = if (investReturnPct < 0.0) min(iraCashPre, iraDistribution) else max(0.0, iraDistribution - min(iraNonCashPre, iraDistribution))
            val nonCashDep = iraDistribution - cashDep
            
            val convCash = min(rothConversion, cashDep)
            val convStock = rothConversion - convCash
            
            val spendCash = cashDep - convCash
            val spendStock = nonCashDep - convStock
            
            if (convCash > 0.01) {
                actionLogs.add("Convert \$${String.format("%,.2f", convCash)} from IRA cash to Roth cash.")
            }
            if (convStock > 0.01) {
                actionLogs.add("Convert \$${String.format("%,.2f", convStock)} from IRA stock to Roth stock.")
            }
            if (spendCash > 0.01) {
                actionLogs.add("Withdraw \$${String.format("%,.2f", spendCash)} from IRA cash to cover expenses.")
            }
            if (spendStock > 0.01) {
                actionLogs.add("Withdraw \$${String.format("%,.2f", spendStock)} from IRA stock (non-cash) to cover expenses or satisfy RMD.")
            }
        }
        
        val rothDeposit = -rothDistribution
        if (rothDeposit > 0.0) {
            val convCash = min(rothConversion, if (iraDistribution > 0.0) {
                if (investReturnPct < 0.0) min(iraCashPre, iraDistribution) else max(0.0, iraDistribution - min(iraNonCashPre, iraDistribution))
            } else 0.0)
            
            rothCashEnd = rothCashPre + convCash
            rothSavingsEnd = rothCashPre + rothNonCashPre + rothDeposit
        } else {
            val (rothC, rothN) = applyDist(rothCashPre, rothNonCashPre, rothDistribution)
            rothCashEnd = rothC
            rothSavingsEnd = rothC + rothN
            if (rothDistribution > 0.0) {
                val cashDep = if (investReturnPct < 0.0) min(rothCashPre, rothDistribution) else max(0.0, rothDistribution - min(rothNonCashPre, rothDistribution))
                val nonCashDep = rothDistribution - cashDep
                if (cashDep > 0.0) {
                    actionLogs.add("Withdraw \$${String.format("%,.2f", cashDep)} from Roth cash to cover expenses.")
                }
                if (nonCashDep > 0.0) {
                    actionLogs.add("Withdraw \$${String.format("%,.2f", nonCashDep)} from Roth stock (non-cash) to cover expenses.")
                }
            }
        }
        
        val (taxC, taxN) = applyDist(taxableCashPre, taxableNonCashPre, taxableDistribution)
        taxableCashEnd = taxC
        taxableSavingsEnd = taxC + taxN
        
        val activeLots = taxableLots.map { StockLot(it.name, it.costBasis, it.currentVal) }.toMutableList()
        
        val rebalBuy = taxableCash - taxCS
        val rebalBuyWithRoi = rebalBuy * (1.0 + investReturnPct)
        
        val taxableCashWithdrawn = if (investReturnPct < 0.0) min(taxableCashPre, taxableDistribution) else max(0.0, taxableDistribution - min(taxableNonCashPre, taxableDistribution))
        val taxableStockWithdrawn = taxableDistribution - taxableCashWithdrawn
        
        var netRebalBuy = 0.0
        var netRebalBuyWithRoi = 0.0
        var netStockWithdrawn = 0.0
        
        if (rebalBuy > 0.01 && taxableStockWithdrawn > 0.01) {
            if (rebalBuyWithRoi > taxableStockWithdrawn) {
                netRebalBuyWithRoi = rebalBuyWithRoi - taxableStockWithdrawn
                netRebalBuy = netRebalBuyWithRoi / (1.0 + investReturnPct)
                netStockWithdrawn = 0.0
            } else {
                netRebalBuy = 0.0
                netRebalBuyWithRoi = 0.0
                netStockWithdrawn = taxableStockWithdrawn - rebalBuyWithRoi
            }
        } else {
            netRebalBuy = max(0.0, rebalBuy)
            netRebalBuyWithRoi = max(0.0, rebalBuyWithRoi)
            netStockWithdrawn = max(0.0, taxableStockWithdrawn)
        }

        val actualCashWithdrawn = taxableDistribution - netStockWithdrawn
        if (actualCashWithdrawn > 0.01) {
            actionLogs.add("Withdraw \$${String.format("%,.2f", actualCashWithdrawn)} from Taxable cash to cover expenses.")
        }

        // 1. Rebalancing stock transaction (start-of-year)
        if (netRebalBuy > 0.01) {
            val lotName = "Stock purchased in $year"
            actionLogs.add("Purchase \$${String.format("%,.2f", netRebalBuy)} of stock named '$lotName' to reinvest cash.")
            activeLots.add(StockLot(name = lotName, costBasis = netRebalBuy, currentVal = netRebalBuyWithRoi))
        } else if (rebalBuy < -0.01) {
            val rebalSellWithRoi = -rebalBuyWithRoi
            activeLots.sortByDescending { it.basisRatio }
            var remainingSale = rebalSellWithRoi
            for (lot in activeLots) {
                if (remainingSale <= 0.0) break
                val toDeplete = min(lot.currentVal, remainingSale)
                val realizedBasis = toDeplete * lot.basisRatio
                val gain = toDeplete - realizedBasis
                
                val scale = 1.0 + investReturnPct
                actionLogs.add("Sell \$${String.format("%,.2f", toDeplete / scale)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", realizedBasis / scale)}, realized gain: \$${String.format("%,.2f", gain / scale)}) to manage cash.")
                
                lot.costBasis -= realizedBasis
                lot.currentVal -= toDeplete
                remainingSale -= toDeplete
            }
        }
        
        // Capture year-start stock lots right after rebalancing (before spending/donations)
        taxableLotsStart = activeLots.filter { it.currentVal > 0.01 }.map { StockLot(it.name, it.costBasis, it.currentVal) }.toMutableList()
        
        // 2. DAF Donation (most appreciated / lowest ratio first)
        if (dafContribution > 0.0) {
            activeLots.sortBy { it.basisRatio }
            var remainingDaf = dafContribution
            for (lot in activeLots) {
                if (remainingDaf <= 0.0) break
                val toDeplete = min(lot.currentVal, remainingDaf)
                val realizedBasis = toDeplete * lot.basisRatio
                
                actionLogs.add("Donate \$${String.format("%,.2f", toDeplete)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", realizedBasis)}) directly to Donor Advised Fund (DAF).")
                
                lot.costBasis -= realizedBasis
                lot.currentVal -= toDeplete
                remainingDaf -= toDeplete
            }
        }
        
        // 3. Taxable distribution (spending) stock sale
        if (netStockWithdrawn > 0.01) {
            activeLots.sortByDescending { it.basisRatio }
            var remainingSale = netStockWithdrawn
            for (lot in activeLots) {
                if (remainingSale <= 0.0) break
                val toDeplete = min(lot.currentVal, remainingSale)
                val realizedBasis = toDeplete * lot.basisRatio
                val gain = toDeplete - realizedBasis
                
                actionLogs.add("Sell \$${String.format("%,.2f", toDeplete)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", realizedBasis)}, realized gain: \$${String.format("%,.2f", gain)}) to cover spending.")
                
                lot.costBasis -= realizedBasis
                lot.currentVal -= toDeplete
                remainingSale -= toDeplete
            }
        }
        
        taxableLotsEnd = activeLots.filter { it.currentVal > 0.01 }.toMutableList()
        taxableCostBasisEnd = taxableLotsEnd.sumOf { it.costBasis } + taxableCashEnd
        
        dafSavingsEnd = dafSavings * (1.0 + investReturnPct) - dafDistribution + dafContribution
    }

    fun getOrdinaryIncome(): Double {
        return salarySelf + salarySpouse + pensionSelf + pensionSpouse + interest
    }

    fun getOtherIncome(): Double {
        return salarySelf + salarySpouse + pensionSelf + pensionSpouse + interest + dividends
    }

    data class TaxCalculationResult(
        val taxableSocialSecurity: Double,
        val taxableOrdinaryIncome: Double,
        val taxableStateIncome: Double,
        val ordinaryTax: Double,
        val capitalGainsTax: Double,
        val totalFederalTax: Double,
        val totalStateTax: Double,
        val fedOrdinaryBracketRate: Double = 0.0,
        val fedOrdinaryBracketLimit: Double = 0.0,
        val fedCapGainsBracketRate: Double = 0.0,
        val fedCapGainsBracketLimit: Double = 0.0
    )

    fun calculateRetirementTax(taxDeferredDist: Double, longTermGains: Double): TaxCalculationResult {
        val otherIncome = getOtherIncome() + longTermGains + taxDeferredDist
        val ssBenefits = socSecSelf + socSecSpouse
        val combinedIncome = (0.5 * ssBenefits) + otherIncome

        var taxableSS = 0.0

        if (combinedIncome > SS_BRACKETS[1].maxIncome * inflationAdjustmentFactor) {
            taxableSS = min(
                0.85 * ssBenefits,
                (0.85 * (combinedIncome - SS_BRACKETS[0].maxIncome * inflationAdjustmentFactor)) +
                        (SS_BRACKETS[1].maxIncome - SS_BRACKETS[0].maxIncome) * inflationAdjustmentFactor
            )
        } else if (combinedIncome > SS_BRACKETS[0].maxIncome * inflationAdjustmentFactor) {
            taxableSS = min(
                0.5 * ssBenefits,
                0.5 * (combinedIncome - SS_BRACKETS[0].maxIncome * inflationAdjustmentFactor)
            )
        }

        val grossOrdinaryIncome = getOrdinaryIncome() + taxDeferredDist + taxableSS
        val totalDeductions = standardDeduction + dafContribution
        val taxableOrdinaryIncome = max(0.0, grossOrdinaryIncome - totalDeductions)

        var ordTax = 0.0
        for (idx in TAX_RATES_FED.indices) {
            val bracket = TAX_RATES_FED[idx]
            val prevMax = if (idx > 0) TAX_RATES_FED[idx - 1].maxIncome * inflationAdjustmentFactor else 0.0
            val currentMax = bracket.maxIncome * inflationAdjustmentFactor
            if (taxableOrdinaryIncome > currentMax) {
                ordTax += (currentMax - prevMax) * bracket.rate
            } else {
                ordTax += (taxableOrdinaryIncome - prevMax) * bracket.rate
                break
            }
        }

        val totalGainsAndQualified = longTermGains + dividends
        val totalTaxableIncome = taxableOrdinaryIncome + totalGainsAndQualified

        var capGainsTax = 0.0
        var baseTax = 0.0
        var totalTax = 0.0
        for (idx in CAP_GAINS_RATES_FED.indices) {
            val bracket = CAP_GAINS_RATES_FED[idx]
            val prevMax = if (idx > 0) CAP_GAINS_RATES_FED[idx - 1].maxIncome * inflationAdjustmentFactor else 0.0
            val currentMax = bracket.maxIncome * inflationAdjustmentFactor

            if (taxableOrdinaryIncome > prevMax) {
                val amountInBracket = min(taxableOrdinaryIncome, currentMax) - prevMax
                baseTax += amountInBracket * bracket.rate
            }
            if (totalTaxableIncome > prevMax) {
                val amountInBracket = min(totalTaxableIncome, currentMax) - prevMax
                totalTax += amountInBracket * bracket.rate
            }
        }
        capGainsTax = totalTax - baseTax

        val stateIncome = max(0.0, grossOrdinaryIncome - formData.getDouble("state_std_deduction", 22500.0) * inflationAdjustmentFactor)
        stateTaxes = stateIncome * formData.getDouble("state_tax_rate", 4.5) / 100.0
        ordinaryTax = ordTax
        capitalGainsTax = capGainsTax
        fedTaxes = ordTax + capGainsTax

        var ordRate = 0.10
        var ordLimit = Double.MAX_VALUE
        for (idx in TAX_RATES_FED.indices) {
            val bracket = TAX_RATES_FED[idx]
            val currentMax = bracket.maxIncome * inflationAdjustmentFactor
            if (taxableOrdinaryIncome <= currentMax) {
                ordRate = bracket.rate
                ordLimit = currentMax
                break
            }
        }

        var cgRate = 0.0
        var cgLimit = Double.MAX_VALUE
        for (idx in CAP_GAINS_RATES_FED.indices) {
            val bracket = CAP_GAINS_RATES_FED[idx]
            val currentMax = bracket.maxIncome * inflationAdjustmentFactor
            if (totalTaxableIncome <= currentMax) {
                cgRate = bracket.rate
                cgLimit = currentMax
                break
            }
        }

        return TaxCalculationResult(
            taxableSocialSecurity = taxableSS,
            taxableOrdinaryIncome = taxableOrdinaryIncome,
            taxableStateIncome = stateIncome,
            ordinaryTax = ordTax,
            capitalGainsTax = capGainsTax,
            totalFederalTax = fedTaxes,
            totalStateTax = stateTaxes,
            fedOrdinaryBracketRate = ordRate,
            fedOrdinaryBracketLimit = ordLimit,
            fedCapGainsBracketRate = cgRate,
            fedCapGainsBracketLimit = cgLimit
        )
    }

    fun fractionOfYearBefore(testdate: LocalDate): Double {
        val testTime = testdate.toDate().time + 86400000L
        if (testTime < yearStartTime) return 0.0
        if (testTime > yearEndTime) return 1.0
        return (testTime - yearStartTime).toDouble() / MILLISEC_PER_YEAR
    }

    fun fractionOfYearAfter(testdate: LocalDate): Double {
        val testTime = testdate.toDate().time - 86400000L
        if (testTime < yearStartTime) return 1.0
        if (testTime > yearEndTime) return 0.0
        return (yearEndTime - testTime).toDouble() / MILLISEC_PER_YEAR
    }

    private fun calcBasePensionIncome(isSelf: Boolean): Double {
        val startAge: Double
        val startDate: LocalDate
        val early: Double
        val late: Double
        if (isSelf) {
            startAge = pensionStartAgeSelf
            startDate = pensionStartDateSelf
            early = formData.getDouble("pension_early", 0.0)
            late = formData.getDouble("pension_late", 0.0)
        } else {
            startAge = pensionStartAgeSpouse
            startDate = pensionStartDateSpouse
            early = formData.getDouble("pension_early_spouse", 0.0)
            late = formData.getDouble("pension_late_spouse", 0.0)
        }

        if (early < 0.0 || late < 0.0) {
            return calcStatePension(isSelf) * fractionOfYearAfter(startDate)
        }
        val maturity = min(startAge - YOUNG_PENSION_AGE, OLD_PENSION_AGE - YOUNG_PENSION_AGE)
        val increment = (late - early) * maturity / (OLD_PENSION_AGE - YOUNG_PENSION_AGE)
        val monthly = early + increment
        return monthly * fractionOfYearAfter(startDate) * 12.0
    }

    private fun calcBaseSocSecIncome(isSelf: Boolean): Double {
        val startAge: Double
        val startDate: LocalDate
        val pia: Double
        if (isSelf) {
            startAge = socSecStartAgeSelf
            startDate = socSecStartDateSelf
            pia = piaSelf
        } else {
            startAge = socSecStartAgeSpouse
            startDate = socSecStartDateSpouse
            pia = piaSpouse
        }
        if (startAge < 62.0) {
            return 0.0
        }
        val ageDiff = Math.round((startAge - 67.0) * 12.0).toInt()
        val monthly: Double
        if (ageDiff < 0) {
            val reductionMonths = min(-ageDiff, 36)
            var reduction = reductionMonths * 5.0 / 9.0
            if (-ageDiff > 36) {
                val extraMonths = -ageDiff - 36
                reduction += extraMonths * 5.0 / 12.0
            }
            monthly = pia * (1.0 - reduction / 100.0)
        } else {
            val creditMonths = min(ageDiff, 36)
            monthly = pia * (1.0 + (creditMonths * 2.0 / 3.0) / 100.0)
        }
        var annual = monthly * fractionOfYearAfter(startDate) * 12.0
        if (annual > 0.0) {
            annual *= inflationAdjustmentFactor
        }
        return annual
    }

    private fun fundSavings(surplusVal: Double) {
        rothConversion = 0.0
        var surplus = surplusVal
        
        var iraTarget = min(salarySelf * 0.06, surplus)
        iraDistribution -= iraTarget * 2.0
        surplus -= iraTarget

        iraTarget = min(salarySpouse * 0.06, surplus)
        iraDistribution -= iraTarget * 2.0
        surplus -= iraTarget

        var rothTarget = min(salarySelf * 0.10, surplus)
        rothDistribution -= rothTarget
        surplus -= rothTarget

        rothTarget = min(salarySpouse * 0.10, surplus)
        rothDistribution -= rothTarget
        surplus -= rothTarget

        iraTarget = min(salarySelf * 0.04, surplus)
        iraDistribution -= iraTarget
        surplus -= iraTarget

        iraTarget = min(salarySpouse * 0.04, surplus)
        iraDistribution -= iraTarget
        surplus -= iraTarget

        val rmdVal = suggestRmd()
        if (iraDistribution > rmdVal) {
            val rothFromIRA = min(iraDistribution - rmdVal, surplus)
            rothDistribution -= rothFromIRA
            surplus -= rothFromIRA
            rothConversion = rothFromIRA
        }

        if (rmdVal > 0.0 && surplus > 0.0) {
            val dafContrib = surplus * dafExcessPct
            val taxableContrib = surplus * (1.0 - dafExcessPct)
            taxableDistribution -= taxableContrib
            dafContribution = dafContrib
        } else {
            taxableDistribution -= surplus
            dafContribution = 0.0
        }
    }

    private fun calcStatePension(isSelf: Boolean): Double {
        val firstYear = getFirstYear()
        val startAge = if (isSelf) {
            getYearsDifference(firstYear.birthDateSelf, firstYear.yearEndDate).toDouble()
        } else {
            getYearsDifference(firstYear.birthDateSpouse, firstYear.yearEndDate).toDouble()
        }
        val pensionAge = if (isSelf) pensionStartAgeSelf else pensionStartAgeSpouse
        val retirementAge = if (isSelf) retirementAgeSelf else retirementAgeSpouse
        val retirementDate = if (isSelf) retirementDateSelf else retirementDateSpouse
        var salary = if (isSelf) formData.getDouble("salary", 60000.0) else formData.getDouble("salary_spouse", 30000.0)

        val salaries = mutableListOf<Double>()
        var age = startAge
        while (age < retirementAge) {
            salaries.add(salary)
            salary *= (1.0 + raisePct)
            age += 1.0
        }

        var totalEarnings = 0.0
        val len = min(salaries.size, 4)
        for (i in 0 until len) {
            totalEarnings += salaries[salaries.size - 1 - i]
        }
        val avgEarnings = if (len > 0) totalEarnings / len else 0.0
        var pension = avgEarnings * 0.0182
        
        val serviceYears = (retirementDate.toDate().time - LocalDate.of(2015, 4, 1).toDate().time).toDouble() / MILLISEC_PER_YEAR
        pension *= serviceYears

        if (pensionAge < 60.0) {
            pension = 0.0
        } else if (pensionAge < 61.0) {
            pension *= 0.85
        } else if (pensionAge < 62.0) {
            pension *= 0.88
        } else if (pensionAge < 63.0) {
            pension *= 0.91
        } else if (pensionAge < 64.0) {
            pension *= 0.94
        } else if (pensionAge < 65.0) {
            pension *= 0.97
        }
        return pension
    }

    private fun getFirstYear(): YearRow {
        var cur = this
        while (cur.previousYear != null) {
            cur = cur.previousYear!!
        }
        return cur
    }

    private fun calcPotentialSocSec(isSelf: Boolean): Double {
        val startAge: Double
        val pia: Double
        if (isSelf) {
            startAge = socSecStartAgeSelf
            pia = piaSelf
        } else {
            startAge = socSecStartAgeSpouse
            pia = piaSpouse
        }
        if (startAge < 62.0) {
            return 0.0
        }
        val ageDiff = Math.round((startAge - 67.0) * 12.0).toInt()
        val monthly: Double
        if (ageDiff < 0) {
            val reductionMonths = min(-ageDiff, 36)
            var reduction = reductionMonths * 5.0 / 9.0
            if (-ageDiff > 36) {
                val extraMonths = -ageDiff - 36
                reduction += extraMonths * 5.0 / 12.0
            }
            monthly = pia * (1.0 - reduction / 100.0)
        } else {
            val creditMonths = min(ageDiff, 36)
            monthly = pia * (1.0 + (creditMonths * 2.0 / 3.0) / 100.0)
        }
        return monthly * 12.0 * inflationAdjustmentFactor
    }

    private fun calcPotentialPension(isSelf: Boolean): Double {
        val startAge: Double
        val early: Double
        val late: Double
        if (isSelf) {
            startAge = pensionStartAgeSelf
            early = formData.getDouble("pension_early", 0.0)
            late = formData.getDouble("pension_late", 0.0)
        } else {
            startAge = pensionStartAgeSpouse
            early = formData.getDouble("pension_early_spouse", 0.0)
            late = formData.getDouble("pension_late_spouse", 0.0)
        }
        if (early < 0.0 || late < 0.0) {
            return calcStatePension(isSelf)
        }
        val maturity = min(startAge - YOUNG_PENSION_AGE, OLD_PENSION_AGE - YOUNG_PENSION_AGE)
        val increment = (late - early) * maturity / (OLD_PENSION_AGE - YOUNG_PENSION_AGE)
        val monthly = early + increment
        return monthly * 12.0
    }

    fun suggestRmd(): Double {
        val hasSpouse = birthDateSpouse != null
        var selfShare = 1.0
        var spouseShare = 0.0

        if (hasSpouse) {
            val totalPia = piaSelf + piaSpouse
            if (totalPia > 0.0) {
                selfShare = piaSelf / totalPia
                spouseShare = piaSpouse / totalPia
            } else {
                selfShare = 0.5
                spouseShare = 0.5
            }
        }

        val selfIRA = iraSavings * selfShare
        val spouseIRA = iraSavings * spouseShare

        val selfRmd = calculateIndividualRmd(ageSelf, birthDateSelf, selfIRA)
        val spouseRmd = if (hasSpouse) calculateIndividualRmd(ageSpouse, birthDateSpouse, spouseIRA) else 0.0

        return selfRmd + spouseRmd
    }

    private fun calculateIndividualRmd(age: Int, birthDate: LocalDate, shareOfIRA: Double): Double {
        val birthYear = birthDate.year
        val rmdStartAge = when {
            birthYear >= 1960 -> 75
            birthYear >= 1951 -> 73
            else -> 72
        }
        if (age < rmdStartAge) {
            return 0.0
        }
        val UNIFORM_LIFETIME_TABLE = mapOf(
            72 to 27.4, 73 to 26.5, 74 to 25.5, 75 to 24.6, 76 to 23.7, 77 to 22.9, 78 to 22.0, 79 to 21.1,
            80 to 20.2, 81 to 19.4, 82 to 18.5, 83 to 17.7, 84 to 16.8, 85 to 16.0, 86 to 15.2, 87 to 14.4,
            88 to 13.7, 89 to 12.9, 90 to 12.2, 91 to 11.5, 92 to 10.8, 93 to 10.1, 94 to 9.5, 95 to 8.9,
            96 to 8.4, 97 to 7.8, 98 to 7.3, 99 to 6.8, 100 to 6.4, 101 to 6.0, 102 to 5.6, 103 to 5.2,
            104 to 4.9, 105 to 4.6, 106 to 4.3, 107 to 4.1, 108 to 3.9, 109 to 3.7, 110 to 3.5, 111 to 3.4,
            112 to 3.3, 113 to 3.1, 114 to 3.0, 115 to 2.9, 116 to 2.8, 117 to 2.7, 118 to 2.5, 119 to 2.3
        )
        val distroPeriod = when {
            age < 72 -> 27.4
            age >= 120 -> 2.0
            else -> UNIFORM_LIFETIME_TABLE[age] ?: max(2.0, 100.0 - age)
        }
        return shareOfIRA / distroPeriod
    }

    // Convert row results to map for clean table generation & saving
    fun toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["year"] = year
        result["age"] = ageSelf
        result["age_spouse"] = ageSpouse
        result["savings"] = getTotalSavings()
        result["cost_basis"] = taxableCostBasis
        result["salary"] = salarySelf + salarySpouse
        result["dividends"] = dividends
        result["ss"] = socSecSelf + socSecSpouse + pensionSelf + pensionSpouse
        result["socsec"] = socSecSelf + socSecSpouse
        result["socsecSelf"] = socSecSelf
        result["socsecSpouse"] = socSecSpouse
        result["pension"] = pensionSelf + pensionSpouse
        result["pensionSelf"] = pensionSelf
        result["pensionSpouse"] = pensionSpouse
        result["taxes"] = fedTaxes + stateTaxes + payrollTaxes + propertyTaxes
        result["property_tax"] = propertyTaxes
        result["fed_income_tax"] = fedTaxes
        result["state_income_tax"] = stateTaxes
        result["payroll_tax"] = payrollTaxes
        result["other"] = otherExpenses
        result["travel"] = travel
        result["housing"] = mortgage
        result["eldercare"] = elderCare
        result["travel_eldercare"] = travel + elderCare
        val iraNonCash = max(0.0, iraSavings - iraCash)
        val rothNonCash = max(0.0, rothSavings - rothCash)
        val taxableNonCash = max(0.0, taxableSavings - taxableCash)
        val iraRoi = iraNonCash * investReturnPct
        val rothRoi = rothNonCash * investReturnPct
        val taxableRoi = taxableNonCash * investReturnPct
        val dafRoi = dafSavings * investReturnPct
        val totalRoi = iraRoi + rothRoi + taxableRoi + dafRoi

        result["ira_savings"] = iraSavings
        result["ira_cash"] = iraCash
        result["roth_savings"] = rothSavings
        result["roth_cash"] = rothCash
        result["other_savings"] = taxableSavings
        result["other_cash"] = taxableCash
        result["roi_pct"] = investReturnPct
        result["roi"] = totalRoi
        result["ira_roi"] = iraRoi
        result["roth_roi"] = rothRoi
        result["other_roi"] = taxableRoi
        result["daf_savings"] = dafSavings
        result["daf_roi"] = dafRoi
        result["distro"] = iraDistribution + rothDistribution + taxableDistribution
        result["ira_distro"] = iraDistribution
        result["roth_distro"] = rothDistribution
        result["other_distro"] = taxableDistribution
        result["realized_gain"] = realizedGain
        result["daf_distro"] = dafDistribution
        result["daf_contrib"] = dafContribution
        result["rmd"] = suggestRmd()
        result["tax_limit_reason"] = taxLimitReason ?: ""
        result["invest"] = (iraSavingsEnd + rothSavingsEnd + taxableSavingsEnd) - (iraSavings + rothSavings + taxableSavings)
        result["salarySelf"] = salarySelf
        result["salarySpouse"] = salarySpouse
        result["action_logs"] = actionLogs.toList()
        result["fedOrdinaryBracketRate"] = fedOrdinaryBracketRate
        result["fedOrdinaryBracketLimit"] = fedOrdinaryBracketLimit
        result["fedCapGainsBracketRate"] = fedCapGainsBracketRate
        result["fedCapGainsBracketLimit"] = fedCapGainsBracketLimit
        result["fedTaxableOrdinaryIncome"] = fedTaxableOrdinaryIncome
        result["fedTotalTaxableIncome"] = fedTotalTaxableIncome
        result["standard_deduction"] = standardDeduction
        result["fed_taxable_ss"] = fedTaxableSocialSecurity
        result["inflation_pct"] = inflationPct
        result["guardrail_msg"] = guardrailAdjustmentMessage ?: ""
        result["stock_lots"] = taxableLotsStart.map { StockLot(it.name, it.costBasis, it.currentVal) }
        return result
    }
}

// 2. Monte Carlo Simulation Engine
class MonteCarlo(val baseFormData: Map<String, String>) {
    
    fun simulate(): List<List<YearRow>> {
        val firstRow = YearRow(baseFormData, null)
        val lifetime = baseFormData.getInt("lifetime", 100)
        val numSimulations = baseFormData.getInt("num_simulations", 100)
        val investmentStdDev = baseFormData.getDouble("investment_std_dev", 15.0) / 100.0
        val inflationStdDev = baseFormData.getDouble("inflation_std_dev", 1.25) / 100.0

        val startAge = min(firstRow.ageSelf, firstRow.ageSpouse)
        val forecastYears = lifetime + 1 - startAge

        val roiPct = firstRow.investReturnPct
        val infPct = firstRow.inflationPct

        val returns = monteCarloSimulation(roiPct, investmentStdDev, numSimulations, forecastYears)
        val inflations = monteCarloSimulation(infPct, inflationStdDev, numSimulations, forecastYears)

        val runs = mutableListOf<List<YearRow>>()

        for (runIdx in 0 until numSimulations) {
            val runYears = mutableListOf<YearRow>()
            var curYear: YearRow? = null
            var i = 0
            while (true) {
                if (curYear != null && (curYear.ageSelf > lifetime || curYear.ageSpouse > lifetime)) {
                    break
                }
                if (i >= forecastYears) break // bounds safety
                val curForm = baseFormData.toMutableMap()
                curForm["investment_return"] = (returns[runIdx][i] * 100.0).toString()
                curForm["inflation"] = (inflations[runIdx][i] * 100.0).toString()

                val nextYear = YearRow(curForm, curYear)
                runYears.add(nextYear)
                curYear = nextYear
                i++
            }
            runs.add(runYears)
        }

        // Sort by final year's savings
        runs.sortBy { it.last().getTotalSavings() }
        return runs
    }

    private fun monteCarloSimulation(mean: Double, stdDev: Double, simulations: Int, years: Int): List<List<Double>> {
        val results = mutableListOf<List<Double>>()
        for (i in 0 until simulations) {
            val run = mutableListOf<Double>()
            for (j in 0 until years) {
                val rand = mean + stdDev * generateStandardNormalRandom()
                run.add(rand)
            }
            results.add(run)
        }
        return results
    }

    private fun generateStandardNormalRandom(): Double {
        var u = 0.0
        var v = 0.0
        while (u == 0.0) u = Math.random()
        while (v == 0.0) v = Math.random()
        return sqrt(-2.0 * log(u, Math.E)) * cos(2.0 * Math.PI * v)
    }
}
