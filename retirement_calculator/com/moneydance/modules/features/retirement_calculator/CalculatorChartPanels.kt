package com.moneydance.modules.features.retirement_calculator

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.io.File
import javax.swing.JPanel
import javax.swing.TransferHandler
import kotlin.math.max
import kotlin.math.min

// 1. Savings Line/Area Graph Component
class SavingsChartPanel : JPanel() {
    private var data: List<Map<String, Any>> = emptyList()

    fun updateData(newData: List<Map<String, Any>>, newConfig: Map<String, String>) {
        data = newData
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (data.isEmpty()) return

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val width = width
        val height = height
        val padding = 60
        val graphW = width - 2 * padding
        val graphH = height - 2 * padding

        // Find maximums for bounds
        val maxSavings = data.maxOfOrNull { it["savings"] as? Double ?: 0.0 } ?: 10000.0
        val maxCashFlow = data.maxOfOrNull {
            max(
                (it["salary"] as? Double ?: 0.0) + (it["ss"] as? Double ?: 0.0),
                (it["other"] as? Double ?: 0.0) + (it["taxes"] as? Double ?: 0.0) + (it["housing"] as? Double ?: 0.0)
            )
        } ?: 1000.0

        val maxVal = max(maxSavings, maxCashFlow) * 1.10
        val minVal = 0.0

        // Draw grid lines
        g2d.color = Color.LIGHT_GRAY
        for (i in 0..5) {
            val y = padding + graphH - (i * graphH / 5)
            g2d.drawLine(padding, y, padding + graphW, y)
            val labelVal = minVal + (i.toDouble() * (maxVal - minVal) / 5)
            g2d.color = Color.DARK_GRAY
            g2d.drawString(String.format("$%,.0f", labelVal), 10, y + 5)
            g2d.color = Color.LIGHT_GRAY
        }

        val points = data.size
        if (points < 2) return

        val stepX = graphW.toDouble() / (points - 1)

        // Draw Net Worth Filled Area
        val savingsPoints = mutableListOf<Point>()
        for (i in 0 until points) {
            val d = data[i]
            val x = padding + (i.toDouble() * stepX).toInt()
            val sVal = d["savings"] as? Double ?: 0.0
            val y = padding + graphH - ((sVal - minVal) * graphH / (maxVal - minVal)).toInt()
            savingsPoints.add(Point(x, y))
        }

        val poly = Polygon()
        poly.addPoint(padding, padding + graphH)
        for (p in savingsPoints) {
            poly.addPoint(p.x, p.y)
        }
        poly.addPoint(padding + graphW, padding + graphH)

        g2d.color = Color(54, 162, 235, 60)
        g2d.fillPolygon(poly)

        g2d.color = Color(54, 162, 235)
        g2d.stroke = BasicStroke(2.5f)
        for (i in 0 until savingsPoints.size - 1) {
            val p1 = savingsPoints[i]
            val p2 = savingsPoints[i + 1]
            g2d.drawLine(p1.x, p1.y, p2.x, p2.y)
        }

        // Draw Income line (Green)
        g2d.color = Color(75, 192, 192)
        g2d.stroke = BasicStroke(2.0f)
        var prevX = padding
        var prevY = padding + graphH - (((data[0]["salary"] as Double + data[0]["ss"] as Double) - minVal) * graphH / (maxVal - minVal)).toInt()
        for (i in 1 until points) {
            val d = data[i]
            val x = padding + (i.toDouble() * stepX).toInt()
            val inc = d["salary"] as Double + d["ss"] as Double
            val y = padding + graphH - ((inc - minVal) * graphH / (maxVal - minVal)).toInt()
            g2d.drawLine(prevX, prevY, x, y)
            prevX = x
            prevY = y
        }

        // Draw Expense line (Red)
        g2d.color = Color(255, 99, 132)
        var prevExpX = padding
        var prevExpY = padding + graphH - (((data[0]["other"] as Double + data[0]["taxes"] as Double + data[0]["housing"] as Double) - minVal) * graphH / (maxVal - minVal)).toInt()
        for (i in 1 until points) {
            val d = data[i]
            val x = padding + (i.toDouble() * stepX).toInt()
            val exp = d["other"] as Double + d["taxes"] as Double + d["housing"] as Double
            val y = padding + graphH - ((exp - minVal) * graphH / (maxVal - minVal)).toInt()
            g2d.drawLine(prevExpX, prevExpY, x, y)
            prevExpX = x
            prevExpY = y
        }

        // Draw labels/axis
        g2d.color = Color.BLACK
        g2d.stroke = BasicStroke(1.5f)
        g2d.drawLine(padding, padding + graphH, padding + graphW, padding + graphH) // X axis
        g2d.drawLine(padding, padding, padding, padding + graphH) // Y axis

        // Draw X axis ticks (Ages)
        for (i in 0 until points step 5) {
            val x = padding + (i.toDouble() * stepX).toInt()
            val age = data[i]["age"].toString()
            g2d.drawLine(x, padding + graphH, x, padding + graphH + 5)
            g2d.drawString(age, x - 10, padding + graphH + 20)
        }

        // Legend
        g2d.color = Color(54, 162, 235)
        g2d.fillRect(padding + 20, 15, 15, 10)
        g2d.color = Color.BLACK
        g2d.drawString("Savings", padding + 40, 25)

        g2d.color = Color(75, 192, 192)
        g2d.fillRect(padding + 120, 15, 15, 10)
        g2d.color = Color.BLACK
        g2d.drawString("Incomes", padding + 140, 25)

        g2d.color = Color(255, 99, 132)
        g2d.fillRect(padding + 220, 15, 15, 10)
        g2d.color = Color.BLACK
        g2d.drawString("Expenses", padding + 240, 25)
    }
}

// 2. Monte Carlo Bar Simulation Graph Component
class SimulationChartPanel : JPanel() {
    private var runs: List<List<YearRow>> = emptyList()
    private var hoverIndex: Int = -1
    private var tooltipText: String? = null
    private var tooltipPoint: Point? = null

    var onRunSelected: ((Int) -> Unit)? = null

    init {
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                updateHover(e.point)
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) {
                hoverIndex = -1
                tooltipText = null
                tooltipPoint = null
                repaint()
            }
            override fun mousePressed(e: MouseEvent) {
                if (hoverIndex in runs.indices) {
                    onRunSelected?.invoke(hoverIndex)
                }
            }
        })
    }

    private fun updateHover(p: Point) {
        if (runs.isEmpty()) return
        val padding = 60
        val graphW = width - 2 * padding
        if (p.x in padding..(padding + graphW)) {
            val barCount = runs.size
            val barW = max(1.0, graphW.toDouble() / barCount)
            val index = max(0, min(barCount - 1, ((p.x - padding) / barW).toInt()))
            if (index != hoverIndex) {
                hoverIndex = index
                val run = runs[index]
                val endingSavings = run.last().getTotalSavings()
                tooltipText = String.format("Simulation #%d: $%,.2f", index + 1, endingSavings)
                tooltipPoint = p
                repaint()
            } else if (tooltipPoint != p) {
                tooltipPoint = p
                repaint()
            }
        } else {
            if (hoverIndex != -1) {
                hoverIndex = -1
                tooltipText = null
                tooltipPoint = null
                repaint()
            }
        }
    }

    fun updateRuns(newRuns: List<List<YearRow>>) {
        runs = newRuns
        hoverIndex = -1
        tooltipText = null
        tooltipPoint = null
        repaint()
    }

    private fun formatCompact(v: Double): String {
        return when {
            v < 10.0 -> String.format("$%.0f", v)
            v < 1000.0 -> String.format("$%.0f", v)
            v < 1000000.0 -> String.format("$%.0fk", v / 1000.0)
            v < 1000000000.0 -> String.format("$%.0fM", v / 1000000.0)
            else -> String.format("$%.0fB", v / 1000000000.0)
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (runs.isEmpty()) {
            g.drawString("Run a Monte Carlo simulation to display the chart.", width / 2 - 120, height / 2)
            return
        }

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val width = width
        val height = height
        val padding = 60
        val graphW = width - 2 * padding
        val graphH = height - 2 * padding

        val maxVal = runs.maxOfOrNull { it.last().getTotalSavings() } ?: 10000.0
        val maxLog = Math.log10(max(10.0, maxVal))
        val minLog = 1.0 // Log10(10)
        val logRange = if (maxLog - minLog > 0) maxLog - minLog else 1.0

        val barCount = runs.size
        val barW = max(1.0, graphW.toDouble() / barCount)

        // Find rational power of 10 values that fit between minLog and maxLog
        val axisPoints = mutableListOf<Double>()
        var currentPower = 1.0
        while (currentPower <= maxLog + 0.1) {
            axisPoints.add(currentPower)
            currentPower += 1.0
        }

        // Draw grid
        g2d.color = Color.LIGHT_GRAY
        for (pow in axisPoints) {
            val y = padding + graphH - (((pow - minLog) * graphH / logRange).toInt())
            if (y in padding..(padding + graphH)) {
                g2d.color = Color.LIGHT_GRAY
                g2d.drawLine(padding, y, padding + graphW, y)
                val labelVal = Math.pow(10.0, pow)
                g2d.color = Color.DARK_GRAY
                g2d.drawString(formatCompact(labelVal), 10, y + 5)
            }
        }

        // Draw bars
        for (i in 0 until barCount) {
            val run = runs[i]
            val lastSavings = run.last().getTotalSavings()
            val hasGuardrail = run.any { it.percentBelowGuardrail > 0.0 }

            // Choose color: Red for fail (<=0), Orange for guardrail hits, Green for clean success
            g2d.color = when {
                lastSavings <= 0.0 -> Color.RED
                hasGuardrail -> Color.ORANGE
                else -> Color(75, 200, 75)
            }

            // Highlight hovered bar
            if (i == hoverIndex) {
                g2d.color = g2d.color.darker()
            }

            val currentLog = Math.log10(max(10.0, lastSavings))
            val barH = if (lastSavings <= 10.0) 0 else ((currentLog - minLog) * graphH / logRange).toInt()
            val x = padding + (i.toDouble() * barW).toInt()
            val y = padding + graphH - barH

            g2d.fillRect(x, y, max(1, barW.toInt() - 1), barH)
        }

        // Draw axis lines
        g2d.color = Color.BLACK
        g2d.drawLine(padding, padding + graphH, padding + graphW, padding + graphH) // X axis
        g2d.drawLine(padding, padding, padding, padding + graphH) // Y axis

        // Title details
        val successCount = runs.count { it.last().getTotalSavings() > 0.0 }
        val rate = successCount.toDouble() / runs.size * 100.0
        g2d.drawString(String.format("Simulation Results: %.1f%% Success Rate (%d / %d runs)", rate, successCount, runs.size), padding + 10, 30)

        // Draw custom tooltip inside paintComponent if active
        val txt = tooltipText
        val pt = tooltipPoint
        if (txt != null && pt != null) {
            g2d.font = Font("Arial", Font.PLAIN, 10)
            val fm = g2d.fontMetrics
            val tw = fm.stringWidth(txt)
            val th = fm.height
            val tx = max(10, min(width - tw - 20, pt.x + 10))
            val ty = max(10, min(height - th - 15, pt.y - 15))

            g2d.color = Color(255, 255, 225)
            g2d.fillRect(tx, ty - th + 2, tw + 10, th + 4)
            g2d.color = Color.BLACK
            g2d.drawRect(tx, ty - th + 2, tw + 10, th + 4)
            g2d.drawString(txt, tx + 5, ty)
        }
    }
}

class FileDropHandler(private val onDrop: (File) -> Unit) : TransferHandler() {
    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDrop) return false
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) return false
        try {
            val t = support.transferable
            @Suppress("UNCHECKED_CAST")
            val files = t.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
            val file = files?.firstOrNull()
            if (file != null) {
                onDrop(file)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
