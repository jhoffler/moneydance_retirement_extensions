package com.moneydance.modules.features.retirement_calculator

import kotlin.math.cos
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Monte Carlo Simulation Engine
class MonteCarlo(val baseFormData: Map<String, String>) {
    
    fun simulate(): List<List<YearRow>> {
        val firstRow = YearRow(baseFormData, null)
        val lifetimeSelf = baseFormData.getInt("lifetime", 100)
        val lifetimeSpouse = baseFormData.getInt("lifetime_spouse", 100)
        val numSimulations = baseFormData.getInt("num_simulations", 100)
        val investmentStdDev = baseFormData.getDouble("investment_std_dev", 15.0) / 100.0
        val inflationStdDev = baseFormData.getDouble("inflation_std_dev", 1.25) / 100.0

        val startAge = min(firstRow.ageSelf, firstRow.ageSpouse)
        val maxForecastAge = max(max(lifetimeSelf, lifetimeSpouse) + 40, 100)
        val forecastYears = maxForecastAge - startAge + 5

        val roiPct = baseFormData.getDouble("investment_return", 6.0) / 100.0
        val infPct = baseFormData.getDouble("inflation", 2.25) / 100.0

        val returns = monteCarloSimulation(roiPct, investmentStdDev, numSimulations, forecastYears)
        val inflations = monteCarloSimulation(infPct, inflationStdDev, numSimulations, forecastYears)

        val runs = mutableListOf<List<YearRow>>()
        val minDeathSelf = firstRow.ageSelf + 2.0
        val minDeathSpouse = firstRow.ageSpouse + 2.0

        for (runIdx in 0 until numSimulations) {
            val runYears = mutableListOf<YearRow>()
            var curYear: YearRow? = null
            val dSelf = max(minDeathSelf, lifetimeSelf.toDouble() + 8.0 * generateStandardNormalRandom())
            val dSpouse = max(minDeathSpouse, lifetimeSpouse.toDouble() + 8.0 * generateStandardNormalRandom())
            var i = 0
            while (true) {
                if (curYear != null && 
                    curYear.ageSelf > dSelf && 
                    curYear.ageSpouse > dSpouse && 
                    min(curYear.ageSelf, curYear.ageSpouse) > 100) {
                    break
                }
                if (i >= forecastYears) break // bounds safety
                val curForm = baseFormData.toMutableMap()
                curForm["investment_return"] = (returns[runIdx][i] * 100.0).toString()
                curForm["inflation"] = (inflations[runIdx][i] * 100.0).toString()

                val nextYear = YearRow(curForm, curYear, dSelf, dSpouse)
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
