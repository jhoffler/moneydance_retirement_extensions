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

// 1. Core Simulation Model
class YearRow(
    val formData: Map<String, String>, 
    val previousYear: YearRow?, 
    val deathAgeSelf: Double = Double.MAX_VALUE, 
    val deathAgeSpouse: Double = Double.MAX_VALUE
) {
    val year: Int
    val yearStartDate: LocalDate
    val yearEndDate: LocalDate
    val yearStartTime: Long
    val yearEndTime: Long
    
    var inflationAdjustmentFactor: Double = 1.0
    var ssInflationAdjustmentFactor: Double = 1.0

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

    val pensionsSelf: List<PensionDefinition>
    val pensionsSpouse: List<PensionDefinition>

    val investReturnPct: Double
    val inflationPct: Double
    val raisePct: Double
    val guardrailPct: Double
    val dafExcessPct: Double
    val interestRate: Double
    val dividendRate: Double

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
    var rothLimitReason: String = ""
    var qcdAmount: Double = 0.0
    var realizedGain: Double = 0.0
    var isRoiNegative: Boolean = false
    var shouldRebalance: Boolean = true
    var fedTaxableSocialSecurity: Double = 0.0

    var baseSalarySelf: Double = 0.0
    var baseSalarySpouse: Double = 0.0

    val financialEvents: List<FinancialEvent>
    val yearEvents: List<FinancialEvent>
    var oneTimeOrdinaryIncome: Double = 0.0
    var oneTimeNonTaxableIncome: Double = 0.0
    var oneTimeSalarySelf: Double = 0.0
    var oneTimeSalarySpouse: Double = 0.0
    var oneTimeGeneralExpense: Double = 0.0
    var oneTimeMedicalExpense: Double = 0.0
    var oneTimeIraDistribution: Double = 0.0
    var oneTimeRothDistribution: Double = 0.0
    var oneTimeTaxableDistribution: Double = 0.0
    var oneTimeDafDistribution: Double = 0.0
    var oneTimeIraContribution: Double = 0.0
    var oneTimeRothContribution: Double = 0.0
    var oneTimeTaxableContribution: Double = 0.0
    var oneTimeDafContribution: Double = 0.0
    var oneTimeNuaTransfer: Double = 0.0
    var oneTimeNuaBasis: Double = 0.0

    var salarySelf: Double = 0.0
    var salarySpouse: Double = 0.0
    var socSecSelf: Double = 0.0
    var socSecSpouse: Double = 0.0
    var pensionSelf: Double = 0.0
    var pensionSpouse: Double = 0.0
    var dividends: Double = 0.0
    var interest: Double = 0.0
    var taxableDividends: Double = 0.0
    var taxableInterest: Double = 0.0
    var taxableMmfEnd: Double = 0.0

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
    var deductibleMedical: Double = 0.0
    var totalDeductionsClaimed: Double = 0.0
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

        pensionsSelf = parsePensions(true)
        pensionsSpouse = parsePensions(false)

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
        guardrailPct = formData.getDouble("guardrail_percent", 100.0)

        val excessStr = formData["daf_excess_pct"]
        dafExcessPct = if (excessStr != null && excessStr.isNotEmpty()) excessStr.toDouble() / 100.0 else 0.5

        interestRate = formData.getDouble("interest_rate", 3.0) / 100.0
        dividendRate = formData.getDouble("dividend_rate", 0.5) / 100.0

        financialEvents = parseFinancialEvents()
        yearEvents = financialEvents.filter { it.date.year == year }

        for (ev in yearEvents) {
            if (ev.isIncome) {
                when (ev.type) {
                    "Bonus / Salary (Self)" -> oneTimeSalarySelf += ev.amount
                    "Bonus / Salary (Spouse)" -> oneTimeSalarySpouse += ev.amount
                    "Taxable Ordinary Income" -> oneTimeOrdinaryIncome += ev.amount
                    "Non-Taxable Income" -> oneTimeNonTaxableIncome += ev.amount
                    "Traditional IRA Contribution" -> oneTimeIraContribution += ev.amount
                    "Roth IRA Contribution" -> oneTimeRothContribution += ev.amount
                    "Taxable Brokerage Contribution" -> oneTimeTaxableContribution += ev.amount
                    "DAF Contribution" -> oneTimeDafContribution += ev.amount
                    "NUA Transfer (IRA to Brokerage)" -> {
                        oneTimeNuaTransfer += ev.amount
                        val basis = if (ev.costBasis > 0.0) ev.costBasis else ev.amount
                        oneTimeNuaBasis += basis
                        oneTimeOrdinaryIncome += basis
                    }
                }
            } else {
                when (ev.type) {
                    "General Expense" -> oneTimeGeneralExpense += ev.amount
                    "Medical / Eldercare" -> oneTimeMedicalExpense += ev.amount
                    "Traditional IRA Distribution" -> {
                        oneTimeIraDistribution += ev.amount
                        oneTimeOrdinaryIncome += ev.amount
                        oneTimeNonTaxableIncome += ev.amount
                    }
                    "Roth IRA Distribution" -> {
                        oneTimeRothDistribution += ev.amount
                        oneTimeNonTaxableIncome += ev.amount
                    }
                    "Taxable Brokerage Distribution" -> {
                        oneTimeTaxableDistribution += ev.amount
                    }
                    "DAF Distribution" -> oneTimeDafDistribution += ev.amount
                }
            }
        }

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
            val asOfDate = formData.getLocalDate("as_of_date", formData.getLocalDate("start_date", LocalDate.of(year, 1, 1)))
            val fullMonthsRemaining = when {
                asOfDate.year < year -> 12
                asOfDate.year > year -> 0
                asOfDate.dayOfMonth == 1 -> 12 - asOfDate.monthValue + 1
                else -> 12 - asOfDate.monthValue
            }
            val fullMonths = max(0, min(12, fullMonthsRemaining))
            val baseDafDistro = formData.getDouble("daf_distro", 0.0)
            dafDistribution = baseDafDistro * (fullMonths / 12.0)
            
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
                        } catch (_: Exception) {
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
            dafDistribution = formData.getDouble("daf_distro", 0.0) * inflationAdjustmentFactor

            // Clone and grow previous year's stock lots
            for (prevLot in previousYear.taxableLotsEnd) {
                taxableLots.add(StockLot(name = prevLot.name, costBasis = prevLot.costBasis, currentVal = prevLot.currentVal * (1.0 + investReturnPct)))
            }
        }

        val maxDafSavings = dafSavings * (1.0 + investReturnPct)
        dafDistribution = min(dafDistribution + oneTimeDafDistribution, maxDafSavings)

        // Calculate incomes
        calculateBaseIncome(previousYear)
        
        // Calculate property taxes first so they are available for spending guardrails
        if (previousYear == null) {
            propertyTaxes = formData.getDouble("prop_taxes", 8000.0)
        } else {
            propertyTaxes = previousYear.propertyTaxes * (1.0 + previousYear.inflationPct)
        }

        // Calculate guardrail spending impacts
        applySpendingGuardrails(formData)

        // Calculate taxes
        payrollTaxes = (salarySelf + salarySpouse) * formData.getDouble("payroll_tax_rate", 7.65) / 100.0

        val isSingle = (ageSelf > deathAgeSelf) || (ageSpouse > deathAgeSpouse)
        val survivorAge = if (ageSelf <= deathAgeSelf) ageSelf else ageSpouse
        standardDeduction = if (isSingle) {
            (STD_DEDUCTION / 2.0) * inflationAdjustmentFactor + (if (survivorAge >= 65) OBBBA_DEDUCTION else 0.0)
        } else {
            STD_DEDUCTION * inflationAdjustmentFactor + (if (ageOldest >= 65) OBBBA_DEDUCTION else 0.0)
        }

        calcTaxes()
    }

    private fun calcInflationAdjustment(): Double {
        var factor = 1.0
        var ssFactor = 1.0
        var cur = previousYear
        while (cur != null) {
            factor *= (1.0 + cur.inflationPct)
            ssFactor *= (1.0 + max(0.0, cur.inflationPct))
            cur = cur.previousYear
        }
        inflationAdjustmentFactor = factor
        ssInflationAdjustmentFactor = ssFactor
        return factor
    }

    private fun calculateBaseIncome(previousYear: YearRow?) {
        if (previousYear == null) {
            salarySelf = if (ageSelf <= deathAgeSelf) formData.getDouble("salary", 60000.0) * fractionOfYearBefore(retirementDateSelf) else 0.0
            salarySpouse = if (ageSpouse <= deathAgeSpouse) formData.getDouble("salary_spouse", 30000.0) * fractionOfYearBefore(retirementDateSpouse) else 0.0
        } else {
            salarySelf = if (ageSelf <= deathAgeSelf) previousYear.baseSalarySelf * (1.0 + previousYear.raisePct) * fractionOfYearBefore(retirementDateSelf) else 0.0
            salarySpouse = if (ageSpouse <= deathAgeSpouse) previousYear.baseSalarySpouse * (1.0 + previousYear.raisePct) * fractionOfYearBefore(retirementDateSpouse) else 0.0
        }
        baseSalarySelf = salarySelf
        baseSalarySpouse = salarySpouse
        salarySelf += oneTimeSalarySelf
        salarySpouse += oneTimeSalarySpouse

        val baseSsSelf = if (ageSelf <= deathAgeSelf) calcBaseSocSecIncome(true) else 0.0
        val baseSsSpouse = if (ageSpouse <= deathAgeSpouse) calcBaseSocSecIncome(false) else 0.0

        if (ageSelf <= deathAgeSelf && ageSpouse <= deathAgeSpouse) {
            socSecSelf = baseSsSelf
            socSecSpouse = baseSsSpouse
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
        } else if (ageSelf <= deathAgeSelf) {
            val spouseFullPotential = calcBaseSocSecIncome(false)
            socSecSelf = max(baseSsSelf, spouseFullPotential)
            socSecSpouse = 0.0
        } else if (ageSpouse <= deathAgeSpouse) {
            val selfFullPotential = calcBaseSocSecIncome(true)
            socSecSpouse = max(baseSsSpouse, selfFullPotential)
            socSecSelf = 0.0
        } else {
            socSecSelf = 0.0
            socSecSpouse = 0.0
        }

        pensionSelf = if (ageSelf <= deathAgeSelf) {
            pensionsSelf.sumOf { p ->
                val startDate = addFractionalYears(birthDateSelf, p.startAge)
                val monthly = p.getMonthlyBenefit(p.startAge)
                monthly * fractionOfYearAfter(startDate) * 12.0
            }
        } else 0.0
        pensionSpouse = if (ageSpouse <= deathAgeSpouse) {
            pensionsSpouse.sumOf { p ->
                val startDate = addFractionalYears(birthDateSpouse, p.startAge)
                val monthly = p.getMonthlyBenefit(p.startAge)
                monthly * fractionOfYearAfter(startDate) * 12.0
            }
        } else 0.0
        val iraInterest = iraCash * interestRate
        val rothInterest = rothCash * interestRate
        val taxableInterest = taxableCash * interestRate
        interest = iraInterest + rothInterest + taxableInterest

        val iraNonCash = max(0.0, iraSavings - iraCash)
        val rothNonCash = max(0.0, rothSavings - rothCash)
        val taxableNonCash = max(0.0, taxableSavings - taxableCash)
        dividends = (iraNonCash * dividendRate) + (rothNonCash * dividendRate) + (taxableNonCash * dividendRate)
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
        val lightYears = formData.getInt("eldercare_light_years", 2)
        mortgage = formData.getDouble("mortgage", 2000.0) * fractionOfYearBefore(mortgageEnd) * 12.0

        val lastRetirementDate = if (retirementDateSelf.isAfter(retirementDateSpouse)) retirementDateSelf else retirementDateSpouse
        travel = formData.getDouble("travel", 25000.0) * fractionOfYearBefore(travelEnd) * fractionOfYearAfter(lastRetirementDate) * inflationAdjustmentFactor
        val lightCost = formData.getDouble("eldercare_light_cost", 40000.0)
        val acuityYears = formData.getInt("eldercare_acuity_years", 1)
        val acuityCost = formData.getDouble("eldercare_acuity_cost", 180000.0)
        val facilityYears = formData.getInt("eldercare_facility_years", 2)
        val facilityCost = formData.getDouble("eldercare_facility_cost", 120000.0)

        fun calculatePersonEldercare(age: Int, deathAge: Double): Double {
            if (age > deathAge) return 0.0
            val ageD = age.toDouble()
            val facilityBound = deathAge - facilityYears
            if (ageD > facilityBound) {
                return facilityCost
            }
            val acuityBound = facilityBound - acuityYears
            if (ageD > acuityBound) {
                return acuityCost
            }
            val lightBound = acuityBound - lightYears
            if (ageD > lightBound) {
                return lightCost
            }
            return 0.0
        }

        val selfCareToday = calculatePersonEldercare(ageSelf, deathAgeSelf)
        val spouseCareToday = calculatePersonEldercare(ageSpouse, deathAgeSpouse)

        elderCare = (selfCareToday + spouseCareToday) * inflationAdjustmentFactor + oneTimeMedicalExpense

        val neitherAlive = (ageSelf > deathAgeSelf) && (ageSpouse > deathAgeSpouse)
        val isSingle = (ageSelf > deathAgeSelf) || (ageSpouse > deathAgeSpouse)

        if (neitherAlive) {
            travel = 0.0
            elderCare = 0.0
            otherExpenses = 2.0 * propertyTaxes
        } else {
            if (isSingle) {
                otherExpenses *= 0.70
                travel *= 0.50
            }

            val isRetired = yearEndDate.isAfter(retirementDateSelf) && yearEndDate.isAfter(retirementDateSpouse)
            percentBelowGuardrail = 0.0
            guardrailAdjustmentMessage = null
            if (isRetired && guardrailPct >= 0.0) {
                val totalsavings = iraSavings + rothSavings + taxableSavings
                val netCash = mortgage + elderCare + otherExpenses + travel - getOtherIncome() - socSecSelf - socSecSpouse - pensionSelf - pensionSpouse - oneTimeNonTaxableIncome
                val baseTargetSavings = netCash / 0.04
                val triggerThreshold = baseTargetSavings * (guardrailPct / 100.0)
                if (totalsavings < triggerThreshold) {
                    percentBelowGuardrail = (triggerThreshold - totalsavings) / triggerThreshold
                    val percentOfThreshold = if (baseTargetSavings > 0.0) (totalsavings / baseTargetSavings) * 100.0 else 0.0
                    if (totalsavings < triggerThreshold * 0.8) {
                        guardrailAdjustmentMessage = String.format(
                            "Total savings $%,.2f is %.2f%% of guardrail threshold $%,.2f, cutting eldercare in half, travel by 95%%, and other expenses by 25%%.",
                            totalsavings, percentOfThreshold, baseTargetSavings
                        )
                        travel *= 0.05
                        otherExpenses *= 0.75
                        elderCare *= 0.5
                    } else {
                        guardrailAdjustmentMessage = String.format(
                            "Total savings $%,.2f is %.2f%% of guardrail threshold $%,.2f, cutting eldercare in half, travel by 90%%, and other expenses by 10%%.",
                            totalsavings, percentOfThreshold, baseTargetSavings
                        )
                        travel *= 0.10
                        otherExpenses *= 0.90
                        elderCare *= 0.5
                    }
                }
            }
        }
        otherExpenses += oneTimeGeneralExpense
    }

    fun getTotalSavings(): Double {
        return iraSavings + rothSavings + taxableSavings
    }

    private fun calcTaxes() {
        optimizeWithdrawalsForNetCash()
        taxes = fedTaxes + stateTaxes + payrollTaxes + propertyTaxes
    }

    private fun optimizeWithdrawalsForNetCash() {
        dafContribution = oneTimeDafContribution
        qcdAmount = 0.0
        val targetNetCash = mortgage + elderCare + otherExpenses + travel
        val estimatedGrossNeeded = targetNetCash + payrollTaxes + propertyTaxes + 5000.0
        val ssSelfPotential = calcPotentialSocSec(true)
        val ssSpousePotential = calcPotentialSocSec(false)
        val pensionSelfPotential = calcPotentialPension(true)
        val pensionSpousePotential = calcPotentialPension(false)
        val cashThreshold = max(0.0, estimatedGrossNeeded - ssSelfPotential - ssSpousePotential - pensionSelfPotential - pensionSpousePotential)
        
        var cumulativeRoi = 0.0
        var cur = previousYear
        while (cur != null) {
            val iraNonCash = max(0.0, cur.iraSavings - cur.iraCash)
            val rothNonCash = max(0.0, cur.rothSavings - cur.rothCash)
            val taxableNonCash = max(0.0, cur.taxableSavings - cur.taxableCash)
            val curTotalRoi = (iraNonCash + rothNonCash + taxableNonCash + cur.dafSavings) * cur.investReturnPct
            cumulativeRoi += curTotalRoi
            cur = cur.previousYear
        }
        this.isRoiNegative = investReturnPct < 0.0 || cumulativeRoi < 0.0
        this.shouldRebalance = investReturnPct >= 0.0 && !this.isRoiNegative
        
        fun getStartCashAndStock(savings: Double, cashVal: Double): Pair<Double, Double> {
            var cashStart = cashVal
            var nonCashStart = max(0.0, savings - cashVal)
            if (shouldRebalance) {
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
        
        val taxableMmfStart = if (previousYear == null) {
            formData.getDouble("start_taxable_mmf", 0.0)
        } else {
            previousYear.taxableMmfEnd
        }
        val mmfRatio = if (taxableCash > 0.0) min(1.0, max(0.0, taxableMmfStart / taxableCash)) else 0.0
        val mmfCash = taxCS * mmfRatio
        val pureCash = max(0.0, taxCS - mmfCash)
        taxableMmfEnd = mmfCash

        val iraInterest = iraCS * interestRate
        val rothInterest = rothCS * interestRate
        val taxableInterest = pureCash * interestRate
        
        val iraDividends = iraNCS * dividendRate
        val rothDividends = rothNCS * dividendRate
        val taxableDividends = (taxNCS * dividendRate) + (mmfCash * interestRate)
        
        interest = iraInterest + rothInterest + taxableInterest
        dividends = iraDividends + rothDividends + taxableDividends
        this.taxableInterest = taxableInterest
        this.taxableDividends = taxableDividends
        
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
        val fixedCash = getOtherIncome() + socSecSelf + socSecSpouse + oneTimeNonTaxableIncome
        
        val isPreRmdRetirement = (ageSelf >= 59.5 || ageSpouse >= 59.5) && suggestRmd() < 0.01

        var currentIRA = rmdValue
        var currentBrokerageSale = 0.0
        var surplus = 0.0

        if (this.isRoiNegative) {
            var totalTax = 0.0
            var iraDistributionVal = rmdValue
            var taxableDistributionVal = 0.0
            var rothDistributionVal = 0.0
            
            for (iter in 0 until 10) {
                val guaranteedOrdinary = salarySelf + salarySpouse + pensionSelf + pensionSpouse + taxableInterest + rmdValue + oneTimeOrdinaryIncome
                val unusedDeduction = max(0.0, standardDeduction - guaranteedOrdinary)
                
                val shortfall = targetNetCash + totalTax - (fixedCash + rmdValue)
                if (shortfall <= 0.0) {
                    iraDistributionVal = rmdValue
                    taxableDistributionVal = 0.0
                    rothDistributionVal = 0.0
                    val result = calculateRetirementTax(rmdValue, 0.0)
                    totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
                    continue
                }
                
                var remaining = shortfall
                
                val rmdFromCash = min(iraCashPre, rmdValue)
                val rmdFromStock = rmdValue - rmdFromCash
                val postRmdIraCash = max(0.0, iraCashPre - rmdFromCash)
                val postRmdIraStock = max(0.0, iraNonCashPre - rmdFromStock)
                
                // 1. Traditional IRA cash & stock up to standard deduction room
                val lvl1Cash = min(postRmdIraCash, min(remaining, unusedDeduction))
                val lvl1Stock = min(postRmdIraStock, min(remaining - lvl1Cash, unusedDeduction - lvl1Cash))
                val lvl1Total = lvl1Cash + lvl1Stock
                
                remaining -= lvl1Total
                
                // 2. Taxable Cash and Roth Cash (both 100% tax-free)
                val taxCashUsed = min(taxableCashPre, remaining)
                remaining -= taxCashUsed
                
                val rothCashUsed = min(rothCashPre, remaining)
                remaining -= rothCashUsed
                
                // 2.5 Remaining IRA cash up to 12% ordinary tax bracket ceiling (safety cap)
                val cap12Ceiling = TAX_RATES_FED[1].maxIncome * inflationAdjustmentFactor
                val currentGrossOrdinary = salarySelf + salarySpouse + pensionSelf + pensionSpouse + taxableInterest + rmdValue + lvl1Total
                val room12 = max(0.0, (cap12Ceiling + standardDeduction) - currentGrossOrdinary)
                
                val postLvl1IraCash = max(0.0, postRmdIraCash - lvl1Cash)
                val lvl25Cash = min(postLvl1IraCash, min(remaining, room12))
                remaining -= lvl25Cash
                
                // 3. Taxable Stock (least appreciated) and Roth Stock
                val taxStockUsed = min(taxableNonCashPre, remaining)
                remaining -= taxStockUsed
                
                val rothStockUsed = min(rothNonCashPre, remaining)
                remaining -= rothStockUsed
                
                // 4. Remaining Traditional IRA cash & stock (fallback above 12% ceiling)
                val postLvl25IraCash = max(0.0, postLvl1IraCash - lvl25Cash)
                val postLvl1IraStock = max(0.0, postRmdIraStock - lvl1Stock)
                
                val lvl4Cash = min(postLvl25IraCash, remaining)
                remaining -= lvl4Cash
                
                val lvl4Stock = min(postLvl1IraStock, remaining)
                remaining -= lvl4Stock
                
                // Sum distributions
                iraDistributionVal = rmdValue + lvl1Total + lvl25Cash + lvl4Cash + lvl4Stock
                taxableDistributionVal = taxCashUsed + taxStockUsed
                rothDistributionVal = rothCashUsed + rothStockUsed
                
                val simulatedGain = simulateStockSale(taxStockUsed)
                val result = calculateRetirementTax(iraDistributionVal, simulatedGain)
                totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
            }
            
            currentIRA = iraDistributionVal
            currentBrokerageSale = taxableDistributionVal
            rothConversion = 0.0
            iraDistribution = iraDistributionVal
            rothDistribution = rothDistributionVal
            taxableDistribution = taxableDistributionVal
            surplus = 0.0
        } else {
            if (isPreRmdRetirement) {
            var cumulativeRoi = 0.0
            var cur = previousYear
            while (cur != null) {
                val iraNonCash = max(0.0, cur.iraSavings - cur.iraCash)
                val rothNonCash = max(0.0, cur.rothSavings - cur.rothCash)
                val taxableNonCash = max(0.0, cur.taxableSavings - cur.taxableCash)
                val curTotalRoi = (iraNonCash + rothNonCash + taxableNonCash + cur.dafSavings) * cur.investReturnPct
                cumulativeRoi += curTotalRoi
                cur = cur.previousYear
            }
            val isRoiNegative = investReturnPct < 0.0 || cumulativeRoi < 0.0

            val guaranteedOrdinary = salarySelf + salarySpouse + pensionSelf + pensionSpouse + taxableInterest + oneTimeOrdinaryIncome
            
            var taxableDistributionVal = 0.0
            var iraDistributionVal = 0.0
            var totalTax = 0.0
            var netCash = 0.0
            var currentDeduction = standardDeduction
            
            for (iter in 0 until 10) {
                val unusedDeduction = max(0.0, currentDeduction - guaranteedOrdinary)
                val currentShortfall = targetNetCash + totalTax - fixedCash
                if (currentShortfall <= 0.0) {
                    taxableDistributionVal = 0.0
                    iraDistributionVal = 0.0
                    
                    val netCapitalGains = 0.0
                    val result = calculateRetirementTax(0.0, netCapitalGains)
                    totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
                    netCash = fixedCash - totalTax
                    surplus = max(0.0, netCash - targetNetCash)
                    currentDeduction = result.totalDeductions
                    break
                }
                
                val simulatedGain = simulateStockSale(currentShortfall)
                val isNetLoss = simulatedGain < 0.0
                
                if (isNetLoss) {
                    taxableDistributionVal = min(maxTaxable, currentShortfall)
                    iraDistributionVal = 0.0
                    if (taxableDistributionVal < currentShortfall) {
                        iraDistributionVal = min(maxIra, currentShortfall - taxableDistributionVal)
                    }
                    
                    val netCapitalGains = simulateStockSale(taxableDistributionVal)
                    val result = calculateRetirementTax(iraDistributionVal, netCapitalGains)
                    totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
                    netCash = (iraDistributionVal + taxableDistributionVal + fixedCash) - totalTax
                    surplus = max(0.0, netCash - targetNetCash)
                    currentDeduction = result.totalDeductions
                } else {
                    val taxFreeIraWithdrawal = min(currentShortfall, unusedDeduction)
                    val remainingShortfall = currentShortfall - taxFreeIraWithdrawal
                    taxableDistributionVal = min(maxTaxable, remainingShortfall)
                    val taxableIraWithdrawal = min(maxIra - taxFreeIraWithdrawal, max(0.0, remainingShortfall - taxableDistributionVal))
                    iraDistributionVal = taxFreeIraWithdrawal + taxableIraWithdrawal
                    
                    val netCapitalGains = simulateStockSale(taxableDistributionVal)
                    val result = calculateRetirementTax(iraDistributionVal, netCapitalGains)
                    totalTax = result.totalFederalTax + result.totalStateTax + propertyTaxes + payrollTaxes
                    netCash = (iraDistributionVal + taxableDistributionVal + fixedCash) - totalTax
                    surplus = max(0.0, netCash - targetNetCash)
                    currentDeduction = result.totalDeductions
                }
            }
            
            if (netCash < targetNetCash) {
                val additionalNeeded = targetNetCash - netCash
                val rothPull = min(additionalNeeded, maxRoth)
                rothDistribution = rothPull
                netCash += rothPull
            } else {
                rothDistribution = 0.0
            }
            
            currentIRA = iraDistributionVal
            currentBrokerageSale = taxableDistributionVal
            rothConversion = 0.0
            iraDistribution = iraDistributionVal
            rothDistribution = rothDistribution
            taxableDistribution = taxableDistributionVal
        } else {
            currentBrokerageSale = max(0.0, estimatedGrossNeeded - rmdValue - fixedCash)
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

            dafContribution = 0.0
            qcdAmount = 0.0

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
                    val charityAmount = surplus * dafExcessPct
                    val gainOnStock = calculateGainForDonation(charityAmount)
                    val isDonationAdvantageous = (gainOnStock * 0.15) > (standardDeduction * result.fedOrdinaryBracketRate)
                    if (isDonationAdvantageous) {
                        dafContribution = charityAmount
                        qcdAmount = 0.0
                    } else {
                        dafContribution = 0.0
                        qcdAmount = charityAmount
                    }
                } else {
                    dafContribution = 0.0
                    qcdAmount = 0.0
                    break
                }
            }
        }
        }

        if (surplus > 0.0) {
            fundSavings(surplus)
        }

        val finalStockSold = if (this.isRoiNegative) {
            max(0.0, taxableDistribution - taxableCashPre)
        } else {
            min(taxableNonCashPre, taxableDistribution)
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
        fedTotalTaxableIncome = finalResult.taxableOrdinaryIncome + finalRealizedGain + taxableDividends
        deductibleMedical = finalResult.deductibleMedical
        totalDeductionsClaimed = finalResult.totalDeductions
        
        // ----------------- ROTH CONVERSIONS -----------------
        val rothEnabled = formData.getBoolean("roth_enabled", false)
        if (rothEnabled) {
            val rothStartAge = formData.getDouble("roth_start_age", 60.0)
            val rothEndAge = formData.getDouble("roth_end_age", 75.0)
            val oldestAge = max(ageSelf.toDouble(), ageSpouse.toDouble())
            
            if (oldestAge in rothStartAge..rothEndAge) {
                val maxConvert = max(0.0, iraSavings - max(0.0, iraDistribution))
                if (maxConvert <= 0.01) {
                    rothLimitReason = "No IRA Balance"
                } else {
                    val baseTaxes = finalResult.totalFederalTax + finalResult.totalStateTax
                    
                    var low = 0.0
                    var high = maxConvert
                    var bestC = 0.0
                    
                    fun evaluateCandidate(C: Double): ConversionResult {
                        var currentTaxIncrease = 0.0
                        var netRealizedGain = finalRealizedGain
                        var realizedLoss = 0.0
                        var finalTaxableDist = taxableDistribution
                        var result: TaxCalculationResult? = null
                        
                        for (iter in 0 until 5) {
                            val candidateTaxableDist = taxableDistribution + currentTaxIncrease
                            val sortedLots = taxableLots.map { StockLot(it.name, it.costBasis, it.currentVal) }
                                .filter { it.currentVal > 0.0 }
                                .sortedByDescending { it.basisRatio }
                                
                            var remaining = candidateTaxableDist
                            var totalRealizedGain = 0.0
                            var totalRealizedLoss = 0.0
                            
                            val lossLimitStr = formData["roth_max_realized_loss"] ?: ""
                            var remainingLossLimit = lossLimitStr.toDoubleOrNull() ?: Double.MAX_VALUE
                            
                            for (lot in sortedLots) {
                                if (remaining <= 0.0) break
                                
                                if (lot.basisRatio > 1.0) {
                                    if (remainingLossLimit <= 0.001) {
                                        continue
                                    }
                                    val soldFromLot = min(lot.currentVal, remaining)
                                    val loss = soldFromLot * (lot.basisRatio - 1.0)
                                    if (loss <= remainingLossLimit + 0.001) {
                                        totalRealizedLoss += loss
                                        remaining -= soldFromLot
                                        remainingLossLimit -= loss
                                    } else {
                                        val portion = remainingLossLimit / (lot.basisRatio - 1.0)
                                        val portionSold = min(soldFromLot, portion)
                                        val portionLoss = portionSold * (lot.basisRatio - 1.0)
                                        totalRealizedLoss += portionLoss
                                        remaining -= portionSold
                                        remainingLossLimit = 0.0
                                    }
                                } else {
                                    val soldFromLot = min(lot.currentVal, remaining)
                                    val gain = soldFromLot * (1.0 - lot.basisRatio)
                                    totalRealizedGain += gain
                                    remaining -= soldFromLot
                                }
                            }
                            if (remaining > 0.0) {
                                totalRealizedGain += remaining
                            }
                            
                            netRealizedGain = totalRealizedGain - totalRealizedLoss
                            realizedLoss = totalRealizedLoss
                            finalTaxableDist = candidateTaxableDist
                            
                            result = calculateRetirementTax(iraDistribution + C, netRealizedGain)
                            val newTotalTax = result.totalFederalTax + result.totalStateTax
                            currentTaxIncrease = max(0.0, newTotalTax - baseTaxes)
                        }
                        
                        if (result == null) return ConversionResult(false, "Error")
                        
                        val maxOrdinaryBracket = formData["roth_max_ordinary_bracket"] ?: "12%"
                        if (maxOrdinaryBracket != "None") {
                            val isSingle = (ageSelf > deathAgeSelf) || (ageSpouse > deathAgeSpouse)
                            val scaleBracket = if (isSingle) 0.5 else 1.0
                            
                            val ceiling = when (maxOrdinaryBracket) {
                                "Std Ded" -> result.totalDeductions
                                "10%" -> TAX_RATES_FED[0].maxIncome * inflationAdjustmentFactor * scaleBracket
                                "12%" -> TAX_RATES_FED[1].maxIncome * inflationAdjustmentFactor * scaleBracket
                                "22%" -> TAX_RATES_FED[2].maxIncome * inflationAdjustmentFactor * scaleBracket
                                "24%" -> TAX_RATES_FED[3].maxIncome * inflationAdjustmentFactor * scaleBracket
                                "32%" -> TAX_RATES_FED[4].maxIncome * inflationAdjustmentFactor * scaleBracket
                                "35%" -> TAX_RATES_FED[5].maxIncome * inflationAdjustmentFactor * scaleBracket
                                "37%" -> TAX_RATES_FED[6].maxIncome * inflationAdjustmentFactor * scaleBracket
                                else -> Double.MAX_VALUE
                            }
                            if (result.taxableOrdinaryIncome > ceiling + 0.01) {
                                return ConversionResult(false, "Bracket Ceiling", result.totalFederalTax + result.totalStateTax, netRealizedGain, realizedLoss, finalTaxableDist)
                            }
                        }
                        
                        val maxCgRateStr = formData["roth_max_cg_rate"] ?: "0%"
                        val maxCgRate = when (maxCgRateStr) {
                            "0%" -> 0.0
                            "15%" -> 0.15
                            "20%" -> 0.20
                            else -> 0.20
                        }
                        if (result.fedCapGainsBracketRate > maxCgRate + 0.001) {
                            return ConversionResult(false, "CG Rate Limit", result.totalFederalTax + result.totalStateTax, netRealizedGain, realizedLoss, finalTaxableDist)
                        }
                        
                        val taxCapStr = formData["roth_tax_impact_cap"] ?: ""
                        if (taxCapStr.isNotEmpty()) {
                            val taxCapVal = taxCapStr.toDoubleOrNull()
                            if (taxCapVal != null) {
                                val adjustedCap = taxCapVal * inflationAdjustmentFactor
                                if (currentTaxIncrease > adjustedCap + 0.01) {
                                    return ConversionResult(false, "Tax Cap Limit", result.totalFederalTax + result.totalStateTax, netRealizedGain, realizedLoss, finalTaxableDist)
                                }
                            }
                        }
                        
                        val lossLimitStr = formData["roth_max_realized_loss"] ?: ""
                        if (lossLimitStr.isNotEmpty()) {
                            val lossLimitVal = lossLimitStr.toDoubleOrNull()
                            if (lossLimitVal != null) {
                                if (realizedLoss > lossLimitVal + 0.01) {
                                    return ConversionResult(false, "Stock Loss Limit", result.totalFederalTax + result.totalStateTax, netRealizedGain, realizedLoss, finalTaxableDist)
                                }
                            }
                        }
                        
                        return ConversionResult(true, "IRA Balance", result.totalFederalTax + result.totalStateTax, netRealizedGain, realizedLoss, finalTaxableDist)
                    }
                    
                    var bestResult = evaluateCandidate(0.0)
                    if (bestResult.valid) {
                        for (i in 0 until 15) {
                            val mid = (low + high) / 2.0
                            val res = evaluateCandidate(mid)
                            if (res.valid) {
                                bestC = mid
                                bestResult = res
                                low = mid
                            } else {
                                high = mid
                            }
                        }
                        if (bestC < maxConvert - 15.0) {
                            val failRes = evaluateCandidate(bestC + 10.0)
                            bestResult = bestResult.copy(limitReason = failRes.limitReason)
                        }
                    }
                    
                    if (bestC > 0.01) {
                        rothConversion = bestC
                        iraDistribution += bestC
                        rothDistribution -= bestC
                        taxableDistribution = bestResult.newTaxableDistribution
                        realizedGain = bestResult.newRealizedGain
                        
                        val finalConvResult = calculateRetirementTax(iraDistribution, realizedGain)
                        fedTaxableSocialSecurity = finalConvResult.taxableSocialSecurity
                        fedOrdinaryBracketRate = finalConvResult.fedOrdinaryBracketRate
                        fedOrdinaryBracketLimit = finalConvResult.fedOrdinaryBracketLimit
                        fedCapGainsBracketRate = finalConvResult.fedCapGainsBracketRate
                        fedCapGainsBracketLimit = finalConvResult.fedCapGainsBracketLimit
                        fedTaxableOrdinaryIncome = finalConvResult.taxableOrdinaryIncome
                        fedTotalTaxableIncome = finalConvResult.taxableOrdinaryIncome + realizedGain + taxableDividends
                        deductibleMedical = finalConvResult.deductibleMedical
                        totalDeductionsClaimed = finalConvResult.totalDeductions
                    }
                    rothLimitReason = bestResult.limitReason
                }
            } else {
                rothLimitReason = "Outside Age Window"
            }
        } else {
            rothLimitReason = ""
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
            
        val lossLimitStr = formData["roth_max_realized_loss"] ?: ""
        var remainingLossLimit = lossLimitStr.toDoubleOrNull() ?: Double.MAX_VALUE
        
        var remaining = amountToSell
        var totalRealizedGain = 0.0
        
        for (lot in sortedLots) {
            if (remaining <= 0.0) break
            
            if (lot.basisRatio > 1.0) {
                if (remainingLossLimit <= 0.001) {
                    continue
                }
                val soldFromLot = min(lot.currentVal, remaining)
                val loss = soldFromLot * (lot.basisRatio - 1.0)
                if (loss <= remainingLossLimit + 0.001) {
                    totalRealizedGain -= loss
                    remaining -= soldFromLot
                    remainingLossLimit -= loss
                } else {
                    val portion = remainingLossLimit / (lot.basisRatio - 1.0)
                    val portionSold = min(soldFromLot, portion)
                    val portionLoss = portionSold * (lot.basisRatio - 1.0)
                    totalRealizedGain -= portionLoss
                    remaining -= portionSold
                    remainingLossLimit = 0.0
                }
            } else {
                val soldFromLot = min(lot.currentVal, remaining)
                val gain = soldFromLot * (1.0 - lot.basisRatio)
                totalRealizedGain += gain
                remaining -= soldFromLot
            }
        }
        if (remaining > 0.0) {
            totalRealizedGain += remaining
        }
        return totalRealizedGain
    }

    private fun calculateGainForDonation(amount: Double): Double {
        if (amount <= 0.0) return 0.0
        val activeLots = taxableLots.map { StockLot(it.name, it.costBasis, it.currentVal) }.toMutableList()
        activeLots.sortBy { it.basisRatio } // Most appreciated (lowest basis ratio) first
        var remainingAmt = amount
        var totalGain = 0.0
        for (lot in activeLots) {
            if (remainingAmt <= 0.0) break
            val toDeplete = min(lot.currentVal, remainingAmt)
            val realizedBasis = toDeplete * lot.basisRatio
            totalGain += (toDeplete - realizedBasis)
            remainingAmt -= toDeplete
        }
        return totalGain
    }

    private fun calculateEndingBalances(
        iraCashPre: Double, iraNonCashPre: Double,
        rothCashPre: Double, rothNonCashPre: Double,
        taxableCashPre: Double, taxableNonCashPre: Double,
        taxCS: Double
    ) {
        if (oneTimeNuaTransfer > 0.0) {
            iraDistribution += oneTimeNuaTransfer
            taxableDistribution -= oneTimeNuaTransfer
            actionLogs.add("NUA Transfer \$${String.format("%,.2f", oneTimeNuaTransfer)} of company stock from IRA to Taxable Brokerage (Cost Basis: \$${String.format("%,.2f", oneTimeNuaBasis)}, Taxable Ordinary Income: \$${String.format("%,.2f", oneTimeNuaBasis)}).")
        }
        if (oneTimeIraDistribution > 0.0) {
            iraDistribution += oneTimeIraDistribution
            actionLogs.add("One-time distribution \$${String.format("%,.2f", oneTimeIraDistribution)} from IRA.")
        }
        if (oneTimeRothDistribution > 0.0) {
            rothDistribution += oneTimeRothDistribution
            actionLogs.add("One-time distribution \$${String.format("%,.2f", oneTimeRothDistribution)} from Roth.")
        }
        if (oneTimeTaxableDistribution > 0.0) {
            taxableDistribution += oneTimeTaxableDistribution
            actionLogs.add("One-time distribution \$${String.format("%,.2f", oneTimeTaxableDistribution)} from Taxable Brokerage.")
        }
        if (oneTimeIraContribution > 0.0) {
            iraDistribution -= oneTimeIraContribution
            actionLogs.add("One-time contribution \$${String.format("%,.2f", oneTimeIraContribution)} to IRA.")
        }
        if (oneTimeRothContribution > 0.0) {
            rothDistribution -= oneTimeRothContribution
            actionLogs.add("One-time contribution \$${String.format("%,.2f", oneTimeRothContribution)} to Roth.")
        }
        if (oneTimeTaxableContribution > 0.0) {
            taxableDistribution -= oneTimeTaxableContribution
            actionLogs.add("One-time contribution \$${String.format("%,.2f", oneTimeTaxableContribution)} to Taxable Brokerage.")
        }

        fun applyDist(cashPre: Double, nonCashPre: Double, dist: Double): Pair<Double, Double> {
            if (dist > 0.0) {
                return if (isRoiNegative) {
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
            val cashDep = if (isRoiNegative) min(iraCashPre, iraDistribution) else max(0.0, iraDistribution - min(iraNonCashPre, iraDistribution))
            val nonCashDep = iraDistribution - cashDep
            
            val convCash = min(rothConversion, cashDep)
            val convStock = rothConversion - convCash
            
            var spendCash = cashDep - convCash
            var spendStock = nonCashDep - convStock
            
            var remainingQcd = qcdAmount
            if (remainingQcd > 0.0) {
                val qcdFromCash = min(spendCash, remainingQcd)
                spendCash -= qcdFromCash
                remainingQcd -= qcdFromCash
            }
            if (remainingQcd > 0.0) {
                val qcdFromStock = min(spendStock, remainingQcd)
                spendStock -= qcdFromStock
                remainingQcd -= qcdFromStock
            }
            
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
        
        if (qcdAmount > 0.01) {
            actionLogs.add("QCD \$${String.format("%,.2f", qcdAmount)} of IRA Qualified Charitable Distribution to reduce taxes on RMD.")
        }
        
        val rothDeposit = -rothDistribution
        if (rothDeposit > 0.0) {
            val convCash = min(rothConversion, if (iraDistribution > 0.0) {
                if (isRoiNegative) min(iraCashPre, iraDistribution) else max(0.0, iraDistribution - min(iraNonCashPre, iraDistribution))
            } else 0.0)
            
            rothCashEnd = rothCashPre + convCash
            rothSavingsEnd = rothCashPre + rothNonCashPre + rothDeposit
        } else {
            val (rothC, rothN) = applyDist(rothCashPre, rothNonCashPre, rothDistribution)
            rothCashEnd = rothC
            rothSavingsEnd = rothC + rothN
            if (rothDistribution > 0.0) {
                val cashDep = if (isRoiNegative) min(rothCashPre, rothDistribution) else max(0.0, rothDistribution - min(rothNonCashPre, rothDistribution))
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
        if (oneTimeNuaTransfer > 0.0) {
            val basis = if (oneTimeNuaBasis > 0.0) oneTimeNuaBasis else oneTimeNuaTransfer
            activeLots.add(StockLot("NUA Stock", basis, oneTimeNuaTransfer))
        }
        if (oneTimeTaxableContribution > 0.0) {
            activeLots.add(StockLot("Contribution", oneTimeTaxableContribution, oneTimeTaxableContribution))
        }
        val lossLimitStr = formData["roth_max_realized_loss"] ?: ""
        var remainingLossLimit = lossLimitStr.toDoubleOrNull() ?: Double.MAX_VALUE
        
        val rebalBuy = taxableCash - taxCS
        val rebalBuyWithRoi = rebalBuy * (1.0 + investReturnPct)
        
        val taxableCashWithdrawn = if (isRoiNegative) min(taxableCashPre, taxableDistribution) else max(0.0, taxableDistribution - min(taxableNonCashPre, taxableDistribution))
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
                
                val scale = 1.0 + investReturnPct
                if (lot.basisRatio > 1.0) {
                    if (remainingLossLimit <= 0.001) {
                        continue
                    }
                    val toDeplete = min(lot.currentVal, remainingSale)
                    val loss = toDeplete * (lot.basisRatio - 1.0)
                    if (loss <= remainingLossLimit + 0.001) {
                        val realizedBasis = toDeplete * lot.basisRatio
                        actionLogs.add("Sell \$${String.format("%,.2f", toDeplete / scale)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", realizedBasis / scale)}, realized gain: -\$${String.format("%,.2f", loss / scale)}) to manage cash.")
                        
                        lot.costBasis -= realizedBasis
                        lot.currentVal -= toDeplete
                        remainingSale -= toDeplete
                        remainingLossLimit -= loss
                    } else {
                        val portion = remainingLossLimit / (lot.basisRatio - 1.0)
                        val portionSold = min(toDeplete, portion)
                        val portionRealizedBasis = portionSold * lot.basisRatio
                        val portionLoss = portionRealizedBasis - portionSold
                        
                        actionLogs.add("Sell \$${String.format("%,.2f", portionSold / scale)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", portionRealizedBasis / scale)}, realized gain: -\$${String.format("%,.2f", portionLoss / scale)}) to manage cash.")
                        
                        lot.costBasis -= portionRealizedBasis
                        lot.currentVal -= portionSold
                        remainingSale -= portionSold
                        remainingLossLimit = 0.0
                    }
                } else {
                    val toDeplete = min(lot.currentVal, remainingSale)
                    val realizedBasis = toDeplete * lot.basisRatio
                    val gain = toDeplete - realizedBasis
                    actionLogs.add("Sell \$${String.format("%,.2f", toDeplete / scale)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", realizedBasis / scale)}, realized gain: \$${String.format("%,.2f", gain / scale)}) to manage cash.")
                    
                    lot.costBasis -= realizedBasis
                    lot.currentVal -= toDeplete
                    remainingSale -= toDeplete
                }
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
                
                if (lot.basisRatio > 1.0) {
                    if (remainingLossLimit <= 0.001) {
                        continue
                    }
                    val toDeplete = min(lot.currentVal, remainingSale)
                    val loss = toDeplete * (lot.basisRatio - 1.0)
                    if (loss <= remainingLossLimit + 0.001) {
                        val realizedBasis = toDeplete * lot.basisRatio
                        actionLogs.add("Sell \$${String.format("%,.2f", toDeplete)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", realizedBasis)}, realized gain: -\$${String.format("%,.2f", loss)}) to cover spending.")
                        
                        lot.costBasis -= realizedBasis
                        lot.currentVal -= toDeplete
                        remainingSale -= toDeplete
                        remainingLossLimit -= loss
                    } else {
                        val portion = remainingLossLimit / (lot.basisRatio - 1.0)
                        val portionSold = min(toDeplete, portion)
                        val portionRealizedBasis = portionSold * lot.basisRatio
                        val portionLoss = portionRealizedBasis - portionSold
                        
                        actionLogs.add("Sell \$${String.format("%,.2f", portionSold)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", portionRealizedBasis)}, realized gain: -\$${String.format("%,.2f", portionLoss)}) to cover spending.")
                        
                        lot.costBasis -= portionRealizedBasis
                        lot.currentVal -= portionSold
                        remainingSale -= portionSold
                        remainingLossLimit = 0.0
                    }
                } else {
                    val toDeplete = min(lot.currentVal, remainingSale)
                    val realizedBasis = toDeplete * lot.basisRatio
                    val gain = toDeplete - realizedBasis
                    actionLogs.add("Sell \$${String.format("%,.2f", toDeplete)} of ${lot.name} stock (cost basis: \$${String.format("%,.2f", realizedBasis)}, realized gain: \$${String.format("%,.2f", gain)}) to cover spending.")
                    
                    lot.costBasis -= realizedBasis
                    lot.currentVal -= toDeplete
                    remainingSale -= toDeplete
                }
            }
        }
        
        taxableLotsEnd = activeLots.filter { it.currentVal > 0.01 }.toMutableList()
        taxableCostBasisEnd = taxableLotsEnd.sumOf { it.costBasis } + taxableCashEnd
        
        dafSavingsEnd = dafSavings * (1.0 + investReturnPct) - dafDistribution + dafContribution
    }

    fun getOrdinaryIncome(): Double {
        return salarySelf + salarySpouse + pensionSelf + pensionSpouse + taxableInterest + oneTimeOrdinaryIncome
    }

    fun getOtherIncome(): Double {
        return salarySelf + salarySpouse + pensionSelf + pensionSpouse + taxableInterest + taxableDividends + oneTimeOrdinaryIncome
    }

    fun calculateRetirementTax(taxDeferredDist: Double, longTermGains: Double): TaxCalculationResult {
        val ordinaryIraDist = if (taxDeferredDist < 0.0) taxDeferredDist else max(0.0, taxDeferredDist - qcdAmount)
        val otherIncome = getOtherIncome() + longTermGains + ordinaryIraDist
        val ssBenefits = socSecSelf + socSecSpouse
        val combinedIncome = (0.5 * ssBenefits) + otherIncome

        var taxableSS = 0.0
        val isSingle = (ageSelf > deathAgeSelf) || (ageSpouse > deathAgeSpouse)
        val scaleBracket = if (isSingle) 0.5 else 1.0
        val ssLimit1 = (if (isSingle) 25000.0 else 32000.0) * inflationAdjustmentFactor
        val ssLimit2 = (if (isSingle) 34000.0 else 44000.0) * inflationAdjustmentFactor

        if (combinedIncome > ssLimit2) {
            taxableSS = min(
                0.85 * ssBenefits,
                (0.85 * (combinedIncome - ssLimit1)) + (ssLimit2 - ssLimit1)
            )
        } else if (combinedIncome > ssLimit1) {
            taxableSS = min(
                0.5 * ssBenefits,
                0.5 * (combinedIncome - ssLimit1)
            )
        }

        val grossOrdinaryIncome = getOrdinaryIncome() + ordinaryIraDist + taxableSS
        val totalGainsAndQualified = longTermGains + taxableDividends
        
        val agi = grossOrdinaryIncome + totalGainsAndQualified
        val medicalThreshold = agi * 0.075
        val deductibleMedical = max(0.0, elderCare - medicalThreshold)

        val itemizedDeductions = dafContribution + deductibleMedical
        val totalDeductions = max(standardDeduction, itemizedDeductions)
        val taxableOrdinaryIncome = max(0.0, grossOrdinaryIncome - totalDeductions)

        var ordTax = 0.0
        for (idx in TAX_RATES_FED.indices) {
            val bracket = TAX_RATES_FED[idx]
            val prevMax = if (idx > 0) TAX_RATES_FED[idx - 1].maxIncome * inflationAdjustmentFactor * scaleBracket else 0.0
            val currentMax = if (bracket.maxIncome == Double.MAX_VALUE) Double.MAX_VALUE else bracket.maxIncome * inflationAdjustmentFactor * scaleBracket
            if (taxableOrdinaryIncome > currentMax) {
                ordTax += (currentMax - prevMax) * bracket.rate
            } else {
                ordTax += (taxableOrdinaryIncome - prevMax) * bracket.rate
                break
            }
        }

        val totalTaxableIncome = taxableOrdinaryIncome + totalGainsAndQualified

        var capGainsTax = 0.0
        var baseTax = 0.0
        var totalTax = 0.0
        for (idx in CAP_GAINS_RATES_FED.indices) {
            val bracket = CAP_GAINS_RATES_FED[idx]
            val prevMax = if (idx > 0) CAP_GAINS_RATES_FED[idx - 1].maxIncome * inflationAdjustmentFactor * scaleBracket else 0.0
            val currentMax = if (bracket.maxIncome == Double.MAX_VALUE) Double.MAX_VALUE else bracket.maxIncome * inflationAdjustmentFactor * scaleBracket

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

        val stateStandard = (if (isSingle) formData.getDouble("state_std_deduction", 22500.0) / 2.0 else formData.getDouble("state_std_deduction", 22500.0)) * inflationAdjustmentFactor
        val stateDeduction = max(stateStandard, itemizedDeductions)
        val stateIncome = max(0.0, grossOrdinaryIncome - stateDeduction)
        stateTaxes = stateIncome * formData.getDouble("state_tax_rate", 4.5) / 100.0
        ordinaryTax = ordTax
        capitalGainsTax = capGainsTax
        fedTaxes = ordTax + capGainsTax

        var ordRate = 0.10
        var ordLimit = Double.MAX_VALUE
        for (idx in TAX_RATES_FED.indices) {
            val bracket = TAX_RATES_FED[idx]
            val currentMax = if (bracket.maxIncome == Double.MAX_VALUE) Double.MAX_VALUE else bracket.maxIncome * inflationAdjustmentFactor * scaleBracket
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
            fedCapGainsBracketLimit = cgLimit,
            totalDeductions = totalDeductions,
            deductibleMedical = deductibleMedical
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
            annual *= ssInflationAdjustmentFactor
        }
        return annual
    }

    private fun fundSavings(surplusVal: Double) {
        val isPreRmd = (ageSelf >= 59.5 || ageSpouse >= 59.5) && suggestRmd() < 0.01
        if (!isPreRmd) {
            rothConversion = 0.0
            qcdAmount = 0.0
        }
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
        if (rmdVal > 0.0 && iraDistribution > rmdVal) {
            val rothFromIRA = min(iraDistribution - rmdVal, surplus)
            rothDistribution -= rothFromIRA
            surplus -= rothFromIRA
            rothConversion = rothFromIRA
        }

        if (rmdVal > 0.0 && surplus > 0.0) {
            val charityAmount = surplus * dafExcessPct
            val taxableContrib = surplus * (1.0 - dafExcessPct)
            taxableDistribution -= taxableContrib
            
            val gainOnStock = calculateGainForDonation(charityAmount)
            val isDonationAdvantageous = (gainOnStock * 0.15) > (standardDeduction * fedOrdinaryBracketRate)
            if (isDonationAdvantageous) {
                dafContribution = charityAmount
                qcdAmount = 0.0
            } else {
                dafContribution = 0.0
                qcdAmount = charityAmount
            }
        } else {
            taxableDistribution -= surplus
            dafContribution = 0.0
            qcdAmount = 0.0
        }
    }

    private fun parsePensions(isSelf: Boolean): List<PensionDefinition> {
        val prefix = if (isSelf) "self" else "spouse"
        val countKey = "pension_count_$prefix"
        
        if (!formData.containsKey(countKey) || formData[countKey].isNullOrEmpty()) {
            val ageKey = if (isSelf) "pension_age" else "pension_age_spouse"
            val earlyKey = if (isSelf) "pension_early" else "pension_early_spouse"
            val lateKey = if (isSelf) "pension_late" else "pension_late_spouse"
            
            val age = formData.getDouble(ageKey, -1.0)
            val early = formData.getDouble(earlyKey, 0.0)
            val late = formData.getDouble(lateKey, 0.0)
            
            if (age >= 0.0 && (early > 0.0 || late > 0.0)) {
                return listOf(
                    PensionDefinition(
                        name = "Default Pension",
                        startAge = age,
                        benefitSchedule = mapOf(60.0 to early, 70.0 to late),
                        isLocked = formData.getBoolean(if (isSelf) "pension_age_locked" else "pension_age_spouse_locked", false)
                    )
                )
            }
            return emptyList()
        }
        
        val count = formData[countKey]?.toIntOrNull() ?: 0
        val list = mutableListOf<PensionDefinition>()
        for (i in 0 until count) {
            val name = formData["pension_name_${prefix}_$i"] ?: "Pension ${i+1}"
            val startAge = formData.getDouble("pension_start_age_${prefix}_$i", 65.0)
            val scheduleStr = formData["pension_schedule_${prefix}_$i"] ?: ""
            val isLocked = formData.getBoolean("pension_locked_${prefix}_$i", false)
            
            val schedule = scheduleStr.split(";").mapNotNull { part ->
                val sub = part.split(":")
                if (sub.size == 2) {
                    val a = sub[0].toDoubleOrNull()
                    val b = sub[1].toDoubleOrNull()
                    if (a != null && b != null) a to b else null
                } else null
            }.toMap()
            
            list.add(PensionDefinition(name, startAge, schedule, isLocked))
        }
        return list
    }

    private fun parseFinancialEvents(): List<FinancialEvent> {
        val list = mutableListOf<FinancialEvent>()
        
        val incomeCount = formData["event_count_income"]?.toIntOrNull() ?: 0
        for (i in 0 until incomeCount) {
            val name = formData["event_name_income_$i"] ?: "Income Event ${i + 1}"
            val date = formData.getLocalDate("event_date_income_$i", LocalDate.of(year, 1, 1))
            val type = formData["event_type_income_$i"] ?: "Taxable Ordinary Income"
            val amt = formData.getDouble("event_amount_income_$i", 0.0)
            val basis = formData.getDouble("event_basis_income_$i", 0.0)
            if (name.isNotEmpty() && amt != 0.0) {
                list.add(FinancialEvent(name, date, type, amt, true, basis))
            }
        }
        
        val expenseCount = formData["event_count_expense"]?.toIntOrNull() ?: 0
        for (i in 0 until expenseCount) {
            val name = formData["event_name_expense_$i"] ?: "Expense Event ${i + 1}"
            val date = formData.getLocalDate("event_date_expense_$i", LocalDate.of(year, 1, 1))
            val type = formData["event_type_expense_$i"] ?: "General Expense"
            val amt = formData.getDouble("event_amount_expense_$i", 0.0)
            if (name.isNotEmpty() && amt != 0.0) {
                list.add(FinancialEvent(name, date, type, amt, false))
            }
        }
        return list
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
        return monthly * 12.0 * ssInflationAdjustmentFactor
    }

    private fun calcPotentialPension(isSelf: Boolean): Double {
        val pensions = if (isSelf) pensionsSelf else pensionsSpouse
        return pensions.sumOf { it.getMonthlyBenefit(it.startAge) * 12.0 }
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

    // Convert row results to map for clean table generation & saving
    fun toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["year"] = year
        result["age"] = ageSelf
        result["age_spouse"] = ageSpouse
        result["death_age_self"] = deathAgeSelf
        result["death_age_spouse"] = deathAgeSpouse
        result["savings"] = getTotalSavings()
        result["cost_basis"] = taxableCostBasis
        result["salary"] = salarySelf + salarySpouse
        result["dividends"] = dividends
        result["interest"] = interest
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
        result["roth_conversion"] = rothConversion
        result["roth_limit_reason"] = rothLimitReason
        result["interest_rate"] = interestRate
        result["dividend_rate"] = dividendRate
        result["taxable_dividends"] = taxableDividends
        result["taxable_interest"] = taxableInterest
        result["daf_distro"] = dafDistribution
        result["daf_contrib"] = dafContribution
        result["qcd_amount"] = qcdAmount
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
        result["deductible_medical"] = deductibleMedical
        result["total_deductions"] = totalDeductionsClaimed
        result["fed_taxable_ss"] = fedTaxableSocialSecurity
        result["inflation_pct"] = inflationPct
        result["guardrail_msg"] = guardrailAdjustmentMessage ?: ""
        result["stock_lots"] = taxableLotsStart.map { StockLot(it.name, it.costBasis, it.currentVal) }
        result["one_time_events"] = yearEvents.map { ev ->
            mapOf(
                "name" to ev.name,
                "date" to ev.date.toString(),
                "type" to ev.type,
                "amount" to ev.amount,
                "isIncome" to ev.isIncome,
                "costBasis" to ev.costBasis
            )
        }
        result["has_event_salary"] = yearEvents.any { it.type.startsWith("Bonus / Salary") }
        result["has_event_income"] = yearEvents.any { it.type == "Taxable Ordinary Income" || it.type == "Non-Taxable Income" || it.type.startsWith("Bonus / Salary") || it.type == "NUA Transfer (IRA to Brokerage)" }
        result["has_event_expenses"] = yearEvents.any { it.type == "General Expense" || it.type == "Medical / Eldercare" }
        result["has_event_ira"] = yearEvents.any { it.type == "Traditional IRA Contribution" || it.type == "Traditional IRA Distribution" || it.type == "NUA Transfer (IRA to Brokerage)" }
        result["has_event_roth"] = yearEvents.any { it.type == "Roth IRA Contribution" || it.type == "Roth IRA Distribution" }
        result["has_event_taxable"] = yearEvents.any { it.type == "Taxable Brokerage Contribution" || it.type == "Taxable Brokerage Distribution" || it.type == "NUA Transfer (IRA to Brokerage)" }
        result["has_event_daf"] = yearEvents.any { it.type == "DAF Contribution" || it.type == "DAF Distribution" }
        result["has_event_savings"] = (result["has_event_ira"] == true || result["has_event_roth"] == true || result["has_event_taxable"] == true || result["has_event_daf"] == true)
        result["has_event_distro"] = yearEvents.any { it.type.endsWith("Distribution") }
        result["nua_transfer"] = oneTimeNuaTransfer
        result["nua_basis"] = oneTimeNuaBasis
        return result
    }
}
