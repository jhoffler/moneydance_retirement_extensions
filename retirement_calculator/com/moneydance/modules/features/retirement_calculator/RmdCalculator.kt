package com.moneydance.modules.features.retirement_calculator

import java.time.LocalDate
import kotlin.math.max

// IRS Uniform Lifetime Table (Table III for use by unmarried owners, married owners whose spouses are not more than 10 years younger)
val UNIFORM_LIFETIME_TABLE: Map<Int, Double> = mapOf(
    72 to 27.4, 73 to 26.5, 74 to 25.5, 75 to 24.6, 76 to 23.7, 77 to 22.9, 78 to 22.0, 79 to 21.1,
    80 to 20.2, 81 to 19.4, 82 to 18.5, 83 to 17.7, 84 to 16.8, 85 to 16.0, 86 to 15.2, 87 to 14.4,
    88 to 13.7, 89 to 12.9, 90 to 12.2, 91 to 11.5, 92 to 10.8, 93 to 10.1, 94 to 9.5, 95 to 8.9,
    96 to 8.4, 97 to 7.8, 98 to 7.3, 99 to 6.8, 100 to 6.4, 101 to 6.0, 102 to 5.6, 103 to 5.2,
    104 to 4.9, 105 to 4.6, 106 to 4.3, 107 to 4.1, 108 to 3.9, 109 to 3.7, 110 to 3.5, 111 to 3.4,
    112 to 3.3, 113 to 3.1, 114 to 3.0, 115 to 2.9, 116 to 2.8, 117 to 2.7, 118 to 2.5, 119 to 2.3
)

fun getRmdStartAge(birthYear: Int): Int {
    return when {
        birthYear >= 1960 -> 75
        birthYear >= 1951 -> 73
        else -> 72
    }
}

fun getRmdDistributionPeriod(age: Int): Double {
    return when {
        age < 72 -> 27.4
        age >= 120 -> 2.0
        else -> UNIFORM_LIFETIME_TABLE[age] ?: max(2.0, 100.0 - age)
    }
}

fun calculateIndividualRmd(age: Int, birthDate: LocalDate, shareOfIRA: Double): Double {
    val rmdStartAge = getRmdStartAge(birthDate.year)
    if (age < rmdStartAge) {
        return 0.0
    }
    val distroPeriod = getRmdDistributionPeriod(age)
    return shareOfIRA / distroPeriod
}
