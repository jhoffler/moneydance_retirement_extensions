package com.moneydance.modules.features.retirement_calculator

import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.text.DecimalFormat
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableCellRenderer
import kotlin.math.max

// Table cell highlighting cell renderer
class CustomRowRenderer(private val tableData: List<Map<String, Any>>) : DefaultTableCellRenderer() {

    private fun fmt(value: Any?): String {
        val d = when (value) {
            is Number -> value.toDouble()
            is String -> value.replace("$", "").replace(",", "").toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        return DecimalFormat("$#,##0.00").format(d)
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel

        cell.font = table.font

        if (isSelected) {
            cell.foreground = table.selectionForeground
            cell.background = table.selectionBackground
        } else {
            cell.foreground = table.foreground
            val decadeIndex = row / 10
            if (decadeIndex % 2 == 1) {
                cell.background = Color(245, 240, 255) // Soft, light lavender alternate color for decades
            } else {
                cell.background = table.background
            }
        }

        if (row >= tableData.size) return cell

        val rowData = tableData[row]
        val modelIdx = table.convertColumnIndexToModel(column)
        val colName = table.model.getColumnName(modelIdx)

        val hasEvent = when {
            colName == "Year" -> (rowData["one_time_events"] as? List<*>)?.isNotEmpty() == true
            colName == "Salary" || colName == "Pre-Tax Income" -> rowData["has_event_salary"] == true || rowData["has_event_income"] == true
            colName == "Expenses" || colName == "Other Spending" || colName == "Travel & Eldercare" -> rowData["has_event_expenses"] == true
            colName == "Savings" -> rowData["has_event_savings"] == true
            colName == "IRA" -> rowData["has_event_ira"] == true
            colName == "Roth" -> rowData["has_event_roth"] == true
            colName == "Taxable" -> rowData["has_event_taxable"] == true
            colName == "DAF" -> rowData["has_event_daf"] == true
            colName == "Distrib" -> rowData["has_event_distro"] == true
            else -> false
        }
        if (hasEvent) {
            cell.font = cell.font.deriveFont(Font.ITALIC)
        }

        val isDistro = colName.lowercase().contains("distro") || colName.lowercase().contains("distrib")

        val cleanStr = value?.toString()?.replace("$", "")?.replace(",", "")?.replace("%", "")?.trim() ?: ""
        val doubleVal = cleanStr.toDoubleOrNull() ?: 0.0

        // Highlight Year column red if a guardrail message is triggered
        if (colName == "Year") {
            val msg = rowData["guardrail_msg"] as? String ?: ""
            if (msg.isNotEmpty()) {
                cell.foreground = Color.RED
            }
        }

        var prevVal = 0.0
        if (row > 0 && row - 1 < tableData.size) {
            val prevRow = tableData[row - 1]
            val key = colName.lowercase().replace(" ", "_").replace("%", "pct").replace("&", "").replace("__", "_")
            val rawPrev = prevRow[key]
            prevVal = when (rawPrev) {
                is Number -> rawPrev.toDouble()
                else -> 0.0
            }
        }

        if (isDistro) {
            if (doubleVal < 0.0) {
                cell.foreground = Color(46, 125, 50) // Green if the distribution is negative
            } else if (doubleVal > 0.0 && doubleVal > prevVal && row > 0) {
                if (!isSelected) {
                    cell.foreground = Color.ORANGE // Orange if greater than zero AND value increased
                }
            }
        } else {
            // Negative savings / ordinary values drop below zero -> RED
            if (doubleVal < 0.0) {
                cell.foreground = Color.RED
            } else if (doubleVal < prevVal && doubleVal != 0.0 && row > 0) {
                if (!isSelected) {
                    cell.foreground = Color.ORANGE
                }
            }
        }

        // Tooltip formatting
        val isSavingsCol = colName in setOf(
            "Savings", "IRA", "Roth", "Taxable", "DAF", "Taxable Basis", "Gains", "Distrib", "IRA Distro", "Roth Distro", "Taxable Distro"
        )

        when {
            colName == "Year" -> {
                val msg = rowData["guardrail_msg"] as? String ?: ""
                cell.toolTipText = if (msg.isNotEmpty()) msg else null
            }
            colName == "Salary" -> {
                val salSelf = fmt(rowData["salarySelf"])
                val salSpouse = fmt(rowData["salarySpouse"])
                cell.toolTipText = """<html>
                    <table border='0' cellpadding='1' cellspacing='5'>
                    <tr><td><b>Self:</b></td><td align='right'>$salSelf</td></tr>
                    <tr><td><b>Spouse:</b></td><td align='right'>$salSpouse</td></tr>
                    </table>
                    </html>""".trimIndent()
            }
            colName == "SS/Pension" -> {
                val ssSelfVal = (rowData["socsecSelf"] as? Number)?.toDouble() ?: 0.0
                val ssSpouseVal = (rowData["socsecSpouse"] as? Number)?.toDouble() ?: 0.0
                val ssTotVal = (rowData["socsec"] as? Number)?.toDouble() ?: 0.0

                val penSelfVal = (rowData["pensionSelf"] as? Number)?.toDouble() ?: 0.0
                val penSpouseVal = (rowData["pensionSpouse"] as? Number)?.toDouble() ?: 0.0
                val penTotVal = (rowData["pension"] as? Number)?.toDouble() ?: 0.0

                val totSelfVal = ssSelfVal + penSelfVal
                val totSpouseVal = ssSpouseVal + penSpouseVal
                val totTotVal = (rowData["ss"] as? Number)?.toDouble() ?: 0.0

                cell.toolTipText = """<html>
                    <table border='0' cellpadding='1' cellspacing='5'>
                    <tr><th></th><th align='right'>Self</th><th align='right'>Spouse</th><th align='right'>Total</th></tr>
                    <tr><td><b>Social Security:</b></td><td align='right'>${fmt(ssSelfVal)}</td><td align='right'>${fmt(ssSpouseVal)}</td><td align='right'>${fmt(ssTotVal)}</td></tr>
                    <tr><td><b>Pension:</b></td><td align='right'>${fmt(penSelfVal)}</td><td align='right'>${fmt(penSpouseVal)}</td><td align='right'>${fmt(penTotVal)}</td></tr>
                    <tr style='border-top: 1px solid black;'><td style='border-top: 1px solid black;'><b>Total:</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totSelfVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totSpouseVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totTotVal)}</b></td></tr>
                    </table>
                    </html>""".trimIndent()
            }
            isSavingsCol -> {
                val interestRateVal = (rowData["interest_rate"] as? Number)?.toDouble() ?: 0.03
                val dividendRateVal = (rowData["dividend_rate"] as? Number)?.toDouble() ?: 0.005
                val iraBalVal = (rowData["ira_savings"] as? Number)?.toDouble() ?: 0.0
                val iraCashVal = (rowData["ira_cash"] as? Number)?.toDouble() ?: 0.0
                val iraNonCashVal = max(0.0, iraBalVal - iraCashVal)
                val iraRoiVal = (rowData["ira_roi"] as? Number)?.toDouble() ?: 0.0
                val iraInterest = iraCashVal * interestRateVal
                val iraDividends = iraNonCashVal * dividendRateVal
                val iraDistVal = (rowData["ira_distro"] as? Number)?.toDouble() ?: 0.0
                val iraContrib = if (iraDistVal < 0.0) -iraDistVal else 0.0
                val iraDist = if (iraDistVal > 0.0) iraDistVal else 0.0
                val iraNetVal = iraRoiVal + iraInterest + iraDividends - iraDist + iraContrib

                val rothBalVal = (rowData["roth_savings"] as? Number)?.toDouble() ?: 0.0
                val rothCashVal = (rowData["roth_cash"] as? Number)?.toDouble() ?: 0.0
                val rothNonCashVal = max(0.0, rothBalVal - rothCashVal)
                val rothRoiVal = (rowData["roth_roi"] as? Number)?.toDouble() ?: 0.0
                val rothInterest = rothCashVal * interestRateVal
                val rothDividends = rothNonCashVal * dividendRateVal
                val rothDistVal = (rowData["roth_distro"] as? Number)?.toDouble() ?: 0.0
                val rothContrib = if (rothDistVal < 0.0) -rothDistVal else 0.0
                val rothDist = if (rothDistVal > 0.0) rothDistVal else 0.0
                val rothNetVal = rothRoiVal + rothInterest + rothDividends - rothDist + rothContrib

                val otherBalVal = (rowData["other_savings"] as? Number)?.toDouble() ?: 0.0
                val otherCashVal = (rowData["other_cash"] as? Number)?.toDouble() ?: 0.0
                val otherNonCashVal = max(0.0, otherBalVal - otherCashVal)
                val otherRoiVal = (rowData["other_roi"] as? Number)?.toDouble() ?: 0.0
                val otherInterest = otherCashVal * interestRateVal
                val otherDividends = otherNonCashVal * dividendRateVal
                val otherDistVal = (rowData["other_distro"] as? Number)?.toDouble() ?: 0.0
                val otherContrib = if (otherDistVal < 0.0) -otherDistVal else 0.0
                val otherDist = if (otherDistVal > 0.0) otherDistVal else 0.0
                val otherNetVal = otherRoiVal + otherInterest + otherDividends - otherDist + otherContrib

                val dafBalVal = (rowData["daf_savings"] as? Number)?.toDouble() ?: 0.0
                val dafRoiVal = (rowData["daf_roi"] as? Number)?.toDouble() ?: 0.0
                val dafInterest = 0.0
                val dafDividends = 0.0
                val dafDistVal = (rowData["daf_distro"] as? Number)?.toDouble() ?: 0.0
                val dafContribVal = (rowData["daf_contrib"] as? Number)?.toDouble() ?: 0.0
                val dafContrib = dafContribVal + (if (dafDistVal < 0.0) -dafDistVal else 0.0)
                val dafDist = if (dafDistVal > 0.0) dafDistVal else 0.0
                val dafNetVal = dafRoiVal + dafInterest + dafDividends - dafDist + dafContrib

                val totBalVal = (rowData["savings"] as? Number)?.toDouble() ?: 0.0
                val totCashVal = iraCashVal + rothCashVal + otherCashVal
                val totRoiVal = (rowData["roi"] as? Number)?.toDouble() ?: 0.0
                val totInterest = iraInterest + rothInterest + otherInterest
                val totDividends = iraDividends + rothDividends + otherDividends
                val totContrib = iraContrib + rothContrib + otherContrib + dafContrib
                val totDist = iraDist + rothDist + otherDist + dafDist
                val totNetVal = totRoiVal + totInterest + totDividends - totDist + totContrib

                cell.toolTipText = """<html>
                    <table border='0' cellpadding='2' cellspacing='3'>
                    <tr><th></th><th align='right'>Total Balance</th><th align='right'>Cash Balance</th><th align='right'>Gains</th><th align='right'>Div / Int</th><th align='right'>Contribution</th><th align='right'>Distribution</th><th align='right'>Net</th></tr>
                    <tr><td><b>IRA:</b></td><td align='right'>${fmt(iraBalVal)}</td><td align='right'>${fmt(iraCashVal)}</td><td align='right'>${fmt(iraRoiVal)}</td><td align='right'>${fmt(iraDividends + iraInterest)}</td><td align='right'>${fmt(iraContrib)}</td><td align='right'>${fmt(iraDist)}</td><td align='right'>${fmt(iraNetVal)}</td></tr>
                    <tr><td><b>Roth:</b></td><td align='right'>${fmt(rothBalVal)}</td><td align='right'>${fmt(rothCashVal)}</td><td align='right'>${fmt(rothRoiVal)}</td><td align='right'>${fmt(rothDividends + rothInterest)}</td><td align='right'>${fmt(rothContrib)}</td><td align='right'>${fmt(rothDist)}</td><td align='right'>${fmt(rothNetVal)}</td></tr>
                    <tr><td><b>Other:</b></td><td align='right'>${fmt(otherBalVal)}</td><td align='right'>${fmt(otherCashVal)}</td><td align='right'>${fmt(otherRoiVal)}</td><td align='right'>${fmt(otherDividends + otherInterest)}</td><td align='right'>${fmt(otherContrib)}</td><td align='right'>${fmt(otherDist)}</td><td align='right'>${fmt(otherNetVal)}</td></tr>
                    <tr><td><b>DAF:</b></td><td align='right'>${fmt(dafBalVal)}</td><td align='right'>$0.00</td><td align='right'>${fmt(dafRoiVal)}</td><td align='right'>${fmt(dafDividends + dafInterest)}</td><td align='right'>${fmt(dafContrib)}</td><td align='right'>${fmt(dafDist)}</td><td align='right'>${fmt(dafNetVal)}</td></tr>
                    <tr style='border-top: 1px solid black;'><td style='border-top: 1px solid black;'><b>Total:</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totBalVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totCashVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totRoiVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totDividends + totInterest)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totContrib)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totDist)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totNetVal)}</b></td></tr>
                    </table>
                    </html>""".trimIndent()
            }
            colName == "Div / Int Taxable" -> {
                val interestRateVal = (rowData["interest_rate"] as? Number)?.toDouble() ?: 0.03
                val dividendRateVal = (rowData["dividend_rate"] as? Number)?.toDouble() ?: 0.005
                val iraBalVal = (rowData["ira_savings"] as? Number)?.toDouble() ?: 0.0
                val iraCashVal = (rowData["ira_cash"] as? Number)?.toDouble() ?: 0.0
                val iraNonCashVal = max(0.0, iraBalVal - iraCashVal)
                val iraRoiVal = (rowData["ira_roi"] as? Number)?.toDouble() ?: 0.0
                val iraInterest = iraCashVal * interestRateVal
                val iraDividends = iraNonCashVal * dividendRateVal

                val rothBalVal = (rowData["roth_savings"] as? Number)?.toDouble() ?: 0.0
                val rothCashVal = (rowData["roth_cash"] as? Number)?.toDouble() ?: 0.0
                val rothNonCashVal = max(0.0, rothBalVal - rothCashVal)
                val rothRoiVal = (rowData["roth_roi"] as? Number)?.toDouble() ?: 0.0
                val rothInterest = rothCashVal * interestRateVal
                val rothDividends = rothNonCashVal * dividendRateVal

                val otherBalVal = (rowData["other_savings"] as? Number)?.toDouble() ?: 0.0
                val otherCashVal = (rowData["other_cash"] as? Number)?.toDouble() ?: 0.0
                val otherNonCashVal = max(0.0, otherBalVal - otherCashVal)
                val otherRoiVal = (rowData["other_roi"] as? Number)?.toDouble() ?: 0.0
                val otherInterest = otherCashVal * interestRateVal
                val otherDividends = otherNonCashVal * dividendRateVal

                val dafBalVal = (rowData["daf_savings"] as? Number)?.toDouble() ?: 0.0
                val dafRoiVal = (rowData["daf_roi"] as? Number)?.toDouble() ?: 0.0
                val dafInterest = 0.0
                val dafDividends = 0.0

                val totBalVal = (rowData["savings"] as? Number)?.toDouble() ?: 0.0
                val totCashVal = iraCashVal + rothCashVal + otherCashVal
                val totRoiVal = (rowData["roi"] as? Number)?.toDouble() ?: 0.0
                val totInterest = iraInterest + rothInterest + otherInterest
                val totDividends = iraDividends + rothDividends + otherDividends

                cell.toolTipText = """<html>
                    <table border='0' cellpadding='2' cellspacing='3'>
                    <tr><th></th><th align='right'>Total Balance</th><th align='right'>Cash Balance</th><th align='right'>Gains</th><th align='right'>Dividends</th><th align='right'>Interest</th></tr>
                    <tr><td><b>IRA:</b></td><td align='right'>${fmt(iraBalVal)}</td><td align='right'>${fmt(iraCashVal)}</td><td align='right'>${fmt(iraRoiVal)}</td><td align='right'>${fmt(iraDividends)}</td><td align='right'>${fmt(iraInterest)}</td></tr>
                    <tr><td><b>Roth:</b></td><td align='right'>${fmt(rothBalVal)}</td><td align='right'>${fmt(rothCashVal)}</td><td align='right'>${fmt(rothRoiVal)}</td><td align='right'>${fmt(rothDividends)}</td><td align='right'>${fmt(rothInterest)}</td></tr>
                    <tr><td><b>Other:</b></td><td align='right'>${fmt(otherBalVal)}</td><td align='right'>${fmt(otherCashVal)}</td><td align='right'>${fmt(otherRoiVal)}</td><td align='right'>${fmt(otherDividends)}</td><td align='right'>${fmt(otherInterest)}</td></tr>
                    <tr><td><b>DAF:</b></td><td align='right'>${fmt(dafBalVal)}</td><td align='right'>$0.00</td><td align='right'>${fmt(dafRoiVal)}</td><td align='right'>${fmt(dafDividends)}</td><td align='right'>${fmt(dafInterest)}</td></tr>
                    <tr style='border-top: 1px solid black;'><td style='border-top: 1px solid black;'><b>Total:</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totBalVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totCashVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totRoiVal)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totDividends)}</b></td><td style='border-top: 1px solid black;' align='right'><b>${fmt(totInterest)}</b></td></tr>
                    </table>
                    </html>""".trimIndent()
            }
            colName == "Taxes" -> {
                val fedtax = fmt(rowData["fed_income_tax"])
                val statetax = fmt(rowData["state_income_tax"])
                val payrolltax = fmt(rowData["payroll_tax"])
                val proptax = fmt(rowData["property_tax"])

                val limitReason = rowData["tax_limit_reason"] as? String ?: ""
                val limitHtml = if (limitReason.isNotEmpty()) {
                    "<tr><td colspan='2' style='border-top: 1px solid gray; padding-top: 4px;'><b>IRA Limited By:</b> $limitReason</td></tr>"
                } else ""

                cell.toolTipText = """<html>
                    <table border='0' cellpadding='1' cellspacing='5'>
                    <tr><td><b>Federal:</b></td><td align='right'>$fedtax</td></tr>
                    <tr><td><b>Payroll:</b></td><td align='right'>$payrolltax</td></tr>
                    <tr><td><b>State:</b></td><td align='right'>$statetax</td></tr>
                    <tr><td><b>Property:</b></td><td align='right'>$proptax</td></tr>
                    $limitHtml
                    </table>
                    </html>""".trimIndent()
            }
            colName == "Travel & Eldercare" -> {
                val travel = fmt(rowData["travel"])
                val eldercare = fmt(rowData["eldercare"])
                val total = fmt(rowData["travel_eldercare"])
                cell.toolTipText = """<html>
                    <table border='0' cellpadding='1' cellspacing='5'>
                    <tr><td><b>Travel:</b></td><td align='right'>$travel</td></tr>
                    <tr><td><b>Elder Care:</b></td><td align='right'>$eldercare</td></tr>
                    <tr style='border-top: 1px solid black;'><td style='border-top: 1px solid black;'><b>Total:</b></td><td style='border-top: 1px solid black;' align='right'><b>$total</b></td></tr>
                    </table>
                    </html>""".trimIndent()
            }
            else -> {
                cell.toolTipText = null
            }
        }

        @Suppress("UNCHECKED_CAST")
        val eventsList = rowData["one_time_events"] as? List<Map<String, Any>>
        if (!eventsList.isNullOrEmpty() && hasEvent) {
            val df = DecimalFormat("$#,##0")
            val eventsHtml = StringBuilder("<div style='margin-top: 5px; border-top: 1px dashed gray; padding-top: 3px;'><b>One-Time Events:</b><br>")
            for (ev in eventsList) {
                val eName = ev["name"]?.toString() ?: ""
                val eDate = ev["date"]?.toString() ?: ""
                val eType = ev["type"]?.toString() ?: ""
                val eAmt = (ev["amount"] as? Number)?.toDouble() ?: 0.0
                val isInc = ev["isIncome"] as? Boolean ?: true
                val eBasis = (ev["costBasis"] as? Number)?.toDouble() ?: 0.0
                val basisNote = if (eType == "NUA Transfer (IRA to Brokerage)" && eBasis > 0.0) " [Basis: " + df.format(eBasis) + "]" else ""
                val sign = if (eType == "NUA Transfer (IRA to Brokerage)") "Transfer " else if (eAmt >= 0) (if (isInc) "+" else "-") else ""
                eventsHtml.append("• ").append(eDate).append(" ").append(eName).append(": ")
                    .append(sign).append(df.format(eAmt)).append(basisNote).append(" (").append(eType).append(")<br>")
            }
            eventsHtml.append("</div>")

            val currentTip = cell.toolTipText
            if (currentTip != null && currentTip.startsWith("<html>") && currentTip.endsWith("</html>")) {
                cell.toolTipText = currentTip.removeSuffix("</html>") + eventsHtml.toString() + "</html>"
            } else if (currentTip != null) {
                cell.toolTipText = "<html>" + currentTip + "<br>" + eventsHtml.toString() + "</html>"
            } else {
                cell.toolTipText = "<html>" + eventsHtml.toString() + "</html>"
            }
        }

        // Alignment formatting
        if (colName == "Year" || colName.contains("Age")) {
            cell.horizontalAlignment = SwingConstants.CENTER
        } else {
            cell.horizontalAlignment = SwingConstants.RIGHT
        }

        return cell
    }
}

class CustomHeaderRenderer : DefaultTableCellRenderer() {
    init {
        horizontalAlignment = SwingConstants.CENTER
        border = UIManager.getBorder("TableHeader.cellBorder") ?: LineBorder(Color.GRAY)
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        val colName = value?.toString() ?: ""

        val savingsCols = setOf("Savings", "IRA", "Roth", "Taxable", "DAF", "Taxable Basis")
        val incomeCols = setOf("Salary", "Div / Int Taxable", "SS/Pension")
        val distroCols = setOf("Distrib", "IRA Distro", "Roth Distro", "Taxable Distro")
        val expenseCols = setOf("Taxes", "Housing", "Travel & Eldercare", "Other Spending")

        val bgColor = when {
            colName in savingsCols -> Color(220, 235, 252) // Soft pastel blue
            colName in incomeCols -> Color(225, 245, 225)  // Soft pastel green
            colName in distroCols -> Color(255, 240, 225)  // Soft pastel orange/peach
            colName in expenseCols -> Color(255, 225, 225) // Soft pastel red/pink
            else -> UIManager.getColor("TableHeader.background") ?: Color(240, 240, 240)
        }

        cell.background = bgColor
        cell.foreground = UIManager.getColor("TableHeader.foreground") ?: Color.BLACK
        cell.font = table.font.deriveFont(Font.BOLD)

        return cell
    }
}
