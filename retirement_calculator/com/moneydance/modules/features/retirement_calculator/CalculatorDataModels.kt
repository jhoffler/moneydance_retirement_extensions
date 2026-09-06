package com.moneydance.modules.features.retirement_calculator

import java.text.DecimalFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

// 1. Domain Structures
data class TaxBracket(val rate: Double, val maxIncome: Double)

data class StockLot(
    var name: String,
    var costBasis: Double,
    var currentVal: Double
) {
    val basisRatio: Double
        get() = if (currentVal > 0.0) costBasis / currentVal else 0.0
}

data class FinancialEvent(
    val name: String,
    val date: LocalDate,
    val type: String,
    val amount: Double,
    val isIncome: Boolean,
    val costBasis: Double = 0.0
)

data class PensionDefinition(
    val name: String,
    var startAge: Double,
    val benefitSchedule: Map<Double, Double>, // Age -> Monthly Benefit
    val isLocked: Boolean = false
) {
    fun getMonthlyBenefit(age: Double): Double {
        if (benefitSchedule.isEmpty()) return 0.0
        if (benefitSchedule.size == 1) return benefitSchedule.values.first()
        
        val sortedAges = benefitSchedule.keys.sorted()
        val lowAge: Double
        val highAge: Double
        
        if (age < sortedAges.first()) {
            lowAge = sortedAges[0]
            highAge = sortedAges[1]
        } else if (age > sortedAges.last()) {
            lowAge = sortedAges[sortedAges.size - 2]
            highAge = sortedAges[sortedAges.size - 1]
        } else {
            var idx = 0
            for (i in 0 until sortedAges.size - 1) {
                if (age >= sortedAges[i] && age <= sortedAges[i+1]) {
                    idx = i
                    break
                }
            }
            lowAge = sortedAges[idx]
            highAge = sortedAges[idx+1]
        }
        
        val lowBenefit = benefitSchedule[lowAge] ?: 0.0
        val highBenefit = benefitSchedule[highAge] ?: 0.0
        
        val fraction = (age - lowAge) / (highAge - lowAge)
        val benefit = lowBenefit + fraction * (highBenefit - lowBenefit)
        return kotlin.math.max(0.0, benefit)
    }
}

data class ConversionResult(
    val valid: Boolean,
    val limitReason: String,
    val newTax: Double = 0.0,
    val newRealizedGain: Double = 0.0,
    val newRealizedLoss: Double = 0.0,
    val newTaxableDistribution: Double = 0.0
)

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
    val fedCapGainsBracketLimit: Double = 0.0,
    val totalDeductions: Double = 0.0,
    val deductibleMedical: Double = 0.0
)

// 2. Tax Rates & Constants
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
const val DIVIDEND_RATE = 0.005
const val INTEREST_RATE = 0.03
const val MAJOR_GUARDRAIL_VIOLATION = 0.2
const val YOUNG_PENSION_AGE = 60.0
const val OLD_PENSION_AGE = 70.0
const val MILLISEC_PER_YEAR = 1000.0 * 60 * 60 * 24 * 365.25

// 3. Date conversion extensions
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

// 4. Map parse extensions
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
    } catch (_: Exception) {
        default
    }
}

fun Map<String, String>.getBoolean(key: String, default: Boolean = false): Boolean {
    return this[key]?.toBoolean() ?: default
}

internal fun formatDollar(value: Double): String {
    val df = DecimalFormat("#,##0.##")
    return df.format(value)
}
