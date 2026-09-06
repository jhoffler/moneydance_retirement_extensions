package com.moneydance.modules.features.retirement_calculator

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JProgressBar
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.border.EmptyBorder
import kotlin.math.max
import kotlin.math.min

data class CombinationResult(
    val ssSelf: Double,
    val ssSpouse: Double,
    val pensionAges: Map<String, Double>,
    val successRate: Double,
    val medianSavings: Double
)

object AgeOptimizationEngine {

    fun formatAge(age: Double): String {
        return if (age % 1.0 == 0.0) {
            String.format("%.0f", age)
        } else {
            String.format("%.1f", age)
        }
    }

    fun runOptimization(
        parent: Component,
        baseFormData: Map<String, String>,
        ssSelfLocked: Boolean,
        ssSpouseLocked: Boolean,
        pensionsConfig: MutableMap<String, String>,
        onApply: (CombinationResult) -> Unit
    ) {
        val retAgeSelf = max(60.0, baseFormData.getDouble("retirement_age", 62.0)).toInt()
        val retAgeSpouse = max(60.0, baseFormData.getDouble("retirement_age_spouse", 63.0)).toInt()

        val currentSsSelf = baseFormData.getDouble("ss_age", 67.0)
        val currentSsSpouse = baseFormData.getDouble("ss_age_spouse", 67.0)

        val ssSelfRange1 = if (ssSelfLocked) listOf(currentSsSelf) else (retAgeSelf..70).map { it.toDouble() }
        val ssSpouseRange1 = if (ssSpouseLocked) listOf(currentSsSpouse) else (retAgeSpouse..70).map { it.toDouble() }

        val selfPenKeys = PensionDialogs.getUnlockedPensionStartAgeKeys(pensionsConfig, true)
        val spousePenKeys = PensionDialogs.getUnlockedPensionStartAgeKeys(pensionsConfig, false)

        val pensionsToOptimize = mutableListOf<String>()
        val pensionsToHoldConstant = mutableListOf<String>()

        for (k in selfPenKeys) {
            if (pensionsToOptimize.size < 2) pensionsToOptimize.add(k) else pensionsToHoldConstant.add(k)
        }
        for (k in spousePenKeys) {
            if (pensionsToOptimize.size < 2) pensionsToOptimize.add(k) else pensionsToHoldConstant.add(k)
        }

        val penRanges1 = pensionsToOptimize.map { key ->
            val isSpouse = key.contains("spouse")
            val retAge = if (isSpouse) retAgeSpouse else retAgeSelf
            (retAge..70).map { it.toDouble() }
        }

        val phase1Combs = mutableListOf<Map<String, Double>>()
        for (ssSelf in ssSelfRange1) {
            for (ssSpouse in ssSpouseRange1) {
                if (pensionsToOptimize.isEmpty()) {
                    phase1Combs.add(mapOf("ss_age" to ssSelf, "ss_age_spouse" to ssSpouse))
                } else if (pensionsToOptimize.size == 1) {
                    val penRange0 = penRanges1[0]
                    for (p0 in penRange0) {
                        phase1Combs.add(mapOf(
                            "ss_age" to ssSelf,
                            "ss_age_spouse" to ssSpouse,
                            pensionsToOptimize[0] to p0
                        ))
                    }
                } else {
                    val penRange0 = penRanges1[0]
                    val penRange1 = penRanges1[1]
                    for (p0 in penRange0) {
                        for (p1 in penRange1) {
                            phase1Combs.add(mapOf(
                                "ss_age" to ssSelf,
                                "ss_age_spouse" to ssSpouse,
                                pensionsToOptimize[0] to p0,
                                pensionsToOptimize[1] to p1
                            ))
                        }
                    }
                }
            }
        }

        if (phase1Combs.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No valid combinations to evaluate.", "Warning", JOptionPane.WARNING_MESSAGE)
            return
        }

        val progress = JProgressBar(0, 100)
        progress.value = 0
        progress.isStringPainted = true

        val frame = (parent as? Frame) ?: SwingUtilities.getWindowAncestor(parent) as? Frame
        val dialog = JDialog(frame, "Optimizing... 0% complete", true)
        val titleLabel = JLabel("Evaluating combinations...")
        titleLabel.border = EmptyBorder(10, 10, 10, 10)
        dialog.add(BorderLayout.NORTH, titleLabel)
        dialog.add(BorderLayout.CENTER, progress)

        val worker = object : SwingWorker<Pair<CombinationResult, Int>, Int>() {
            override fun doInBackground(): Pair<CombinationResult, Int> {
                val results1 = mutableListOf<CombinationResult>()
                val total1 = phase1Combs.size

                // Phase 1 execution
                for (idx in phase1Combs.indices) {
                    if (isCancelled) break
                    val comb = phase1Combs[idx]

                    val tempForm = baseFormData.toMutableMap()
                    tempForm["ss_age"] = comb["ss_age"].toString()
                    tempForm["ss_age_spouse"] = comb["ss_age_spouse"].toString()
                    for (k in pensionsToOptimize) {
                        tempForm[k] = comb[k].toString()
                    }
                    tempForm["num_simulations"] = "50" // Fast search uses 50 runs

                    val engine = MonteCarlo(tempForm)
                    val runs = engine.simulate()
                    val successCount = runs.count { it.last().getTotalSavings() > 0.0 }
                    val successRate = successCount.toDouble() / runs.size
                    val medianSavings = runs[runs.size / 2].last().getTotalSavings()

                    results1.add(CombinationResult(
                        ssSelf = comb["ss_age"]!!,
                        ssSpouse = comb["ss_age_spouse"]!!,
                        pensionAges = pensionsToOptimize.associateWith { key -> comb[key]!! },
                        successRate = successRate,
                        medianSavings = medianSavings
                    ))

                    publish((idx * 50) / total1)
                }

                if (isCancelled) throw RuntimeException("Cancelled")
                if (results1.isEmpty()) throw RuntimeException("No Phase 1 results")

                // Identify top 50 combinations of Phase 1 to get bounds
                val rankedPhase1 = results1.sortedWith(compareByDescending<CombinationResult> { it.successRate }
                    .thenByDescending { it.medianSavings })
                val top50 = rankedPhase1.take(50)

                // 2. Generate Phase 2 Combinations (Fractional 1/2 Year Search within Bounds of Top 50)
                val bounds = mutableMapOf<String, Pair<Double, Double>>()
                fun setBound(key: String, currentVal: Double, selector: (CombinationResult) -> Double) {
                    val isLocked = if (key.startsWith("pension_")) {
                        pensionsConfig[key.replace("start_age", "locked")]?.toBoolean() == true
                    } else if (key == "ss_age") {
                        ssSelfLocked
                    } else {
                        ssSpouseLocked
                    }
                    if (isLocked) {
                        bounds[key] = Pair(currentVal, currentVal)
                    } else {
                        val minV = top50.minOf { selector(it) }
                        val maxV = top50.maxOf { selector(it) }
                        bounds[key] = Pair(minV, maxV)
                    }
                }

                setBound("ss_age", currentSsSelf) { it.ssSelf }
                setBound("ss_age_spouse", currentSsSpouse) { it.ssSpouse }
                for (key in pensionsToOptimize) {
                    val currentVal = baseFormData.getDouble(key, 65.0)
                    setBound(key, currentVal) { it.pensionAges[key] ?: currentVal }
                }

                fun getRange(key: String, retAge: Int): List<Double> {
                    val b = bounds[key]!!
                    if (b.first == b.second) return listOf(b.first)
                    val list = mutableListOf<Double>()
                    var v = max(retAge.toDouble(), Math.floor(b.first))
                    val end = min(70.0, Math.ceil(b.second))
                    while (v <= end + 0.01) {
                        list.add(v)
                        if (v + 0.5 <= end) {
                            list.add(v + 0.5)
                        }
                        v += 1.0
                    }
                    return list.distinct().sorted()
                }

                val ssSelfRange2 = getRange("ss_age", retAgeSelf)
                val ssSpouseRange2 = getRange("ss_age_spouse", retAgeSpouse)
                val penRanges2 = pensionsToOptimize.map { key ->
                    val isSpouse = key.contains("spouse")
                    val retAge = if (isSpouse) retAgeSpouse else retAgeSelf
                    getRange(key, retAge)
                }

                val phase2Combs = mutableListOf<Map<String, Double>>()
                for (ssSelf in ssSelfRange2) {
                    for (ssSpouse in ssSpouseRange2) {
                        if (pensionsToOptimize.isEmpty()) {
                            val isFractional = (ssSelf % 1.0 != 0.0 || ssSpouse % 1.0 != 0.0)
                            if (isFractional) {
                                phase2Combs.add(mapOf("ss_age" to ssSelf, "ss_age_spouse" to ssSpouse))
                            }
                        } else if (pensionsToOptimize.size == 1) {
                            val penRange0 = penRanges2[0]
                            for (p0 in penRange0) {
                                val isFractional = (ssSelf % 1.0 != 0.0 || ssSpouse % 1.0 != 0.0 || p0 % 1.0 != 0.0)
                                if (isFractional) {
                                    phase2Combs.add(mapOf(
                                        "ss_age" to ssSelf,
                                        "ss_age_spouse" to ssSpouse,
                                        pensionsToOptimize[0] to p0
                                    ))
                                }
                            }
                        } else {
                            val penRange0 = penRanges2[0]
                            val penRange1 = penRanges2[1]
                            for (p0 in penRange0) {
                                for (p1 in penRange1) {
                                    val isFractional = (ssSelf % 1.0 != 0.0 || ssSpouse % 1.0 != 0.0 ||
                                            p0 % 1.0 != 0.0 || p1 % 1.0 != 0.0)
                                    if (isFractional) {
                                        phase2Combs.add(mapOf(
                                            "ss_age" to ssSelf,
                                            "ss_age_spouse" to ssSpouse,
                                            pensionsToOptimize[0] to p0,
                                            pensionsToOptimize[1] to p1
                                        ))
                                    }
                                }
                            }
                        }
                    }
                }

                val results2 = mutableListOf<CombinationResult>()
                val total2 = phase2Combs.size

                // Phase 2 execution
                for (idx in phase2Combs.indices) {
                    if (isCancelled) break
                    val comb = phase2Combs[idx]

                    val tempForm = baseFormData.toMutableMap()
                    tempForm["ss_age"] = comb["ss_age"].toString()
                    tempForm["ss_age_spouse"] = comb["ss_age_spouse"].toString()
                    for (k in pensionsToOptimize) {
                        tempForm[k] = comb[k].toString()
                    }
                    tempForm["num_simulations"] = "50" // Fast search uses 50 runs

                    val engine = MonteCarlo(tempForm)
                    val runs = engine.simulate()
                    val successCount = runs.count { it.last().getTotalSavings() > 0.0 }
                    val successRate = successCount.toDouble() / runs.size
                    val medianSavings = runs[runs.size / 2].last().getTotalSavings()

                    results2.add(CombinationResult(
                        ssSelf = comb["ss_age"]!!,
                        ssSpouse = comb["ss_age_spouse"]!!,
                        pensionAges = pensionsToOptimize.associateWith { key -> comb[key]!! },
                        successRate = successRate,
                        medianSavings = medianSavings
                    ))

                    val progressValue = 50 + if (total2 > 0) (idx * 45) / total2 else 45
                    publish(progressValue)
                }

                if (isCancelled) throw RuntimeException("Cancelled")

                // Merge pools
                val mergedResults = (results1 + results2).distinctBy {
                    val penKeysStr = it.pensionAges.entries.sortedBy { e -> e.key }.joinToString("_") { e -> "${e.key}_${e.value}" }
                    "${it.ssSelf}_${it.ssSpouse}_$penKeysStr"
                }

                // Rank the merged results
                val sortedBySuccess = mergedResults.sortedByDescending { it.successRate }
                val successRanks = sortedBySuccess.withIndex().associate { it.value to it.index + 1 }

                val sortedByMedian = mergedResults.sortedByDescending { it.medianSavings }
                val medianRanks = sortedByMedian.withIndex().associate { it.value to it.index + 1 }

                val rankedCandidates = mergedResults.sortedBy { successRanks[it]!! + medianRanks[it]!! }
                val top10 = rankedCandidates.take(10)

                // 3. Phase 3: High-Fidelity Duel
                publish(96)
                val finalResults = mutableListOf<CombinationResult>()
                val settingsCount = baseFormData.getInt("num_simulations", 100)

                for (idx in top10.indices) {
                    if (isCancelled) break
                    val comb = top10[idx]

                    val tempForm = baseFormData.toMutableMap()
                    tempForm["ss_age"] = comb.ssSelf.toString()
                    tempForm["ss_age_spouse"] = comb.ssSpouse.toString()
                    for ((k, v) in comb.pensionAges) {
                        tempForm[k] = v.toString()
                    }
                    tempForm["num_simulations"] = settingsCount.toString() // UI settings count

                    val engine = MonteCarlo(tempForm)
                    val runs = engine.simulate()
                    val successCount = runs.count { it.last().getTotalSavings() > 0.0 }
                    val successRate = successCount.toDouble() / runs.size
                    val medianSavings = runs[runs.size / 2].last().getTotalSavings()

                    finalResults.add(CombinationResult(
                        ssSelf = comb.ssSelf,
                        ssSpouse = comb.ssSpouse,
                        pensionAges = comb.pensionAges,
                        successRate = successRate,
                        medianSavings = medianSavings
                    ))

                    publish(96 + (idx * 4) / top10.size)
                }

                publish(100)

                val finalSortedBySuccess = finalResults.sortedByDescending { it.successRate }
                val finalSuccessRanks = finalSortedBySuccess.withIndex().associate { it.value to it.index + 1 }

                val finalSortedByMedian = finalResults.sortedByDescending { it.medianSavings }
                val finalMedianRanks = finalSortedByMedian.withIndex().associate { it.value to it.index + 1 }

                val finalBest = finalResults.minByOrNull { finalSuccessRanks[it]!! + finalMedianRanks[it]!! }
                    ?: throw RuntimeException("Failed to find final best CombinationResult")

                return Pair(finalBest, phase1Combs.size + results2.size)
            }

            override fun process(chunks: List<Int>) {
                val latest = chunks.last()
                progress.value = latest
                dialog.title = "Optimizing... $latest% complete"
                titleLabel.text = "Optimizing Phase... $latest% complete"
            }

            override fun done() {
                dialog.dispose()
                if (isCancelled) return
                try {
                    val res = get()
                    val best = res.first
                    val count = res.second

                    val sbMsg = StringBuilder()
                    sbMsg.append(String.format("Optimization completed! Evaluated %d combinations.\n\n", count))
                    sbMsg.append("Best combination found:\n")
                    if (!ssSelfLocked) sbMsg.append("  Self SS Age: ").append(formatAge(best.ssSelf)).append("\n")
                    if (!ssSpouseLocked) sbMsg.append("  Spouse SS Age: ").append(formatAge(best.ssSpouse)).append("\n")
                    for ((k, age) in best.pensionAges) {
                        val isSpouse = k.contains("spouse")
                        val pfx = if (isSpouse) "spouse" else "self"
                        val idx = k.substringAfterLast("_").toIntOrNull() ?: 0
                        val name = pensionsConfig["pension_name_${pfx}_$idx"] ?: "Pension"
                        val who = if (isSpouse) "Spouse" else "Self"
                        sbMsg.append("  ").append(name).append(" (").append(who).append(") Start Age: ").append(formatAge(age)).append("\n")
                    }
                    sbMsg.append(String.format("\nSimulation Success Rate: %.1f%%\n", best.successRate * 100.0))
                    sbMsg.append(String.format("Median Ending Balance: $%,.2f\n\n", best.medianSavings))
                    sbMsg.append("Would you like to apply these optimized ages?")

                    val choice = JOptionPane.showConfirmDialog(
                        parent,
                        sbMsg.toString(),
                        "Optimization Results",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE
                    )

                    if (choice == JOptionPane.YES_OPTION) {
                        onApply(best)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    JOptionPane.showMessageDialog(parent, "Error during optimization: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }

        dialog.defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                worker.cancel(true)
                dialog.dispose()
            }
        })

        dialog.setSize(400, 120)
        dialog.setLocationRelativeTo(parent)
        worker.execute()
        dialog.isVisible = true
    }
}
