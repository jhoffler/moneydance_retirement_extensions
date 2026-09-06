package com.moneydance.modules.features.retirement_calculator

import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.text.DecimalFormat
import javax.swing.JOptionPane

data class ActionRow(
    val type: String,
    val asset: String,
    val amount: String,
    val realizedGain: String,
    val purpose: String
)

class HtmlSelection(private val html: String, private val plainText: String) : Transferable {
    companion object {
        val HTML_FLAVOR = DataFlavor("text/html;class=java.lang.String")
    }

    private val flavors = arrayOf(HTML_FLAVOR, DataFlavor.stringFlavor)

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavors.any { it.equals(flavor) }

    override fun getTransferData(flavor: DataFlavor): Any {
        return when {
            flavor.equals(HTML_FLAVOR) -> html
            flavor.equals(DataFlavor.stringFlavor) -> plainText
            else -> throw UnsupportedFlavorException(flavor)
        }
    }
}

object FinancialPlanExporter {

    fun exportPlan(
        parent: Component,
        currentResults: List<Map<String, Any>>,
        form: Map<String, String>
    ) {
        if (currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No calculated projections found. Please recalculate first.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        val sb = StringBuilder()
        val htmlSb = StringBuilder()

        val fmt = { v: Any? ->
            val d = when (v) {
                is Number -> v.toDouble()
                is String -> v.replace("$", "").replace(",", "").toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            DecimalFormat("$#,##0.00").format(d)
        }

        val appendHtmlTableRow = { hsb: StringBuilder, cells: List<String>, isHeader: Boolean, isTotal: Boolean ->
            hsb.append("<tr>")
            for (i in cells.indices) {
                val cell = cells[i]
                val align = if (i == 0) "left" else "right"
                val padding = "padding: 6px 8px;"
                val border = "border: 1px solid #cccccc;"
                val font = when {
                    isHeader -> "font-weight: bold; background-color: #e6e6e6;"
                    isTotal -> "font-weight: bold; background-color: #e6e6e6; color: #0066cc;"
                    else -> ""
                }
                hsb.append("<td style=\"text-align: ").append(align).append("; ").append(padding).append(" ").append(border).append(" ").append(font).append("\">")
                hsb.append(cell)
                hsb.append("</td>")
            }
            hsb.append("</tr>\n")
        }

        val withdrawRegex = Regex("""Withdraw \$(-?[0-9,.]+) from (.*?) to (.*)""")
        val purchaseRegex = Regex("""Purchase \$(-?[0-9,.]+) of stock named '(.*?)' to (.*)""")
        val sellRegex = Regex("""Sell \$(-?[0-9,.]+) of (.*?) stock \(cost basis: \$(-?[0-9,.]+), realized gain: \$(-?[0-9,.]+)\) to (.*)""")
        val donateRegex = Regex("""Donate \$(-?[0-9,.]+) of (.*?) stock \(cost basis: \$(-?[0-9,.]+)\) directly to (.*)""")
        val convertRegex = Regex("""Convert \$(-?[0-9,.]+) from (.*?) to (.*)""")
        val qcdRegex = Regex("""QCD \$(-?[0-9,.]+) of (.*?) to (.*)""")

        for (i in currentResults.indices) {
            val r = currentResults[i]
            val yr = r["year"]?.toString() ?: "Unknown"
            val age = (r["age"] as? Number)?.toDouble() ?: 0.0
            val ageSp = (r["age_spouse"] as? Number)?.toDouble() ?: 0.0

            sb.append("# ").append(yr).append(" Annual Financial Instructions\n\n")
            if (i > 0) {
                htmlSb.append("<div style=\"text-align: center; color: #999999; margin-top: 24pt; margin-bottom: 24pt; font-size: 9pt; font-family: Arial, sans-serif; font-style: italic;\">[Page Break - Press Ctrl+Enter here]</div>\n")
            }
            htmlSb.append("<h1 style=\"text-align: center; font-family: Arial, sans-serif; font-size: 16pt; color: #333333; margin-top: 18pt; margin-bottom: 6pt;\">").append(yr).append(" Annual Financial Instructions</h1>\n")
            htmlSb.append("<hr style=\"border: 0; border-top: 1px solid #cccccc; margin-top: 6pt; margin-bottom: 18pt;\" />\n")

            sb.append("## Joint Financial Information\n")
            htmlSb.append("<h2 style=\"font-family: Arial, sans-serif; font-size: 13pt; color: #555555; margin-top: 14pt; margin-bottom: 6pt;\">Joint Financial Information</h2>\n")

            val getD = { key: String -> (r[key] as? Number)?.toDouble() ?: 0.0 }
            val iraSav = getD("ira_savings")
            val iraCsh = getD("ira_cash")
            val rothSav = getD("roth_savings")
            val rothCsh = getD("roth_cash")
            val taxSav = getD("other_savings")
            val taxCsh = getD("other_cash")
            val dafSav = getD("daf_savings")
            val totSav = getD("savings")
            val taxBasis = getD("cost_basis")

            val iraRoi = getD("ira_roi")
            val rothRoi = getD("roth_roi")
            val taxRoi = getD("other_roi")
            val dafRoi = getD("daf_roi")
            val totRoi = getD("roi")

            val iraDiv = Math.max(0.0, iraSav - iraCsh) * 0.01
            val rothDiv = Math.max(0.0, rothSav - rothCsh) * 0.01
            val taxDiv = Math.max(0.0, taxSav - taxCsh) * 0.01
            val totDiv = getD("dividends")

            val iraInt = iraCsh * 0.03
            val rothInt = rothCsh * 0.03
            val taxInt = taxCsh * 0.03
            val totInt = getD("interest")

            sb.append("<div style=\"font-size: 9pt;\">\n\n")
            sb.append("| Savings Type | Total Balance | Cost Basis | Cash Portion | ROI | Dividends | Interest |\n")
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n")
            sb.append("| IRA | ").append(fmt(iraSav)).append(" | N/A | ").append(fmt(iraCsh)).append(" | ").append(fmt(iraRoi)).append(" | ").append(fmt(iraDiv)).append(" | ").append(fmt(iraInt)).append(" |\n")
            sb.append("| Roth | ").append(fmt(rothSav)).append(" | N/A | ").append(fmt(rothCsh)).append(" | ").append(fmt(rothRoi)).append(" | ").append(fmt(rothDiv)).append(" | ").append(fmt(rothInt)).append(" |\n")
            sb.append("| Taxable Brokerage | ").append(fmt(taxSav)).append(" | ").append(fmt(taxBasis)).append(" | ").append(fmt(taxCsh)).append(" | ").append(fmt(taxRoi)).append(" | ").append(fmt(taxDiv)).append(" | ").append(fmt(taxInt)).append(" |\n")
            sb.append("| DAF | ").append(fmt(dafSav)).append(" | N/A | $0.00 | ").append(fmt(dafRoi)).append(" | $0.00 | $0.00 |\n")
            sb.append("| **Total** | **").append(fmt(totSav)).append("** | **").append(fmt(taxBasis)).append("** | **").append(fmt(iraCsh + rothCsh + taxCsh)).append("** | **").append(fmt(totRoi)).append("** | **").append(fmt(totDiv)).append("** | **").append(fmt(totInt)).append("** |\n\n")
            sb.append("</div>\n\n")

            val htmlTable = StringBuilder()
            htmlTable.append("<table style=\"border-collapse: collapse; width: 100%; font-family: Arial, sans-serif; font-size: 9pt; margin-bottom: 12pt;\">\n")
            appendHtmlTableRow(htmlTable, listOf("Savings Type", "Total Balance", "Cost Basis", "Cash Portion", "ROI", "Dividends", "Interest"), true, false)
            appendHtmlTableRow(htmlTable, listOf("IRA", fmt(iraSav), "N/A", fmt(iraCsh), fmt(iraRoi), fmt(iraDiv), fmt(iraInt)), false, false)
            appendHtmlTableRow(htmlTable, listOf("Roth", fmt(rothSav), "N/A", fmt(rothCsh), fmt(rothRoi), fmt(rothDiv), fmt(rothInt)), false, false)
            appendHtmlTableRow(htmlTable, listOf("Taxable Brokerage", fmt(taxSav), fmt(taxBasis), fmt(taxCsh), fmt(taxRoi), fmt(taxDiv), fmt(taxInt)), false, false)
            appendHtmlTableRow(htmlTable, listOf("DAF", fmt(dafSav), "N/A", "$0.00", fmt(dafRoi), "$0.00", "$0.00"), false, false)
            appendHtmlTableRow(htmlTable, listOf("Total", fmt(totSav), fmt(taxBasis), fmt(iraCsh + rothCsh + taxCsh), fmt(totRoi), fmt(totDiv), fmt(totInt)), false, true)
            htmlTable.append("</table>\n")
            htmlSb.append(htmlTable.toString())

            // Income Sources
            sb.append("## Income Sources\n")
            htmlSb.append("<h2 style=\"font-family: Arial, sans-serif; font-size: 13pt; color: #555555; margin-top: 14pt; margin-bottom: 6pt;\">Income Sources</h2>\n")

            val salSelf = getD("salarySelf")
            val salSp = getD("salarySpouse")
            val ssSelf = getD("socsecSelf")
            val ssSp = getD("socsecSpouse")
            val penSelf = getD("pensionSelf")
            val penSp = getD("pensionSpouse")
            val rothConv = getD("roth_conversion")
            val iraDist = getD("ira_distro")
            val rothDist = getD("roth_distro")
            val realizedGain = getD("realized_gain")
            val totSelf = salSelf + ssSelf + penSelf
            val totSp = salSp + ssSp + penSp
            val taxableDist = getD("other_distro")
            val taxableDiv = getD("taxable_dividends")
            val taxableInt = getD("taxable_interest")
            val jointTot = iraDist + rothDist + (taxableDiv + taxableInt) + taxableDist
            val grandTot = totSelf + totSp + jointTot

            data class IncomeColSpec(
                val header: String,
                val selfVal: Double,
                val spouseVal: Double,
                val jointVal: Double,
                val totalVal: Double,
                val isTaxableGain: Boolean = false,
                val selfGain: Double = 0.0,
                val spouseGain: Double = 0.0,
                val jointGain: Double = 0.0,
                val totalGain: Double = 0.0
            )

            val allCols = listOf(
                IncomeColSpec("Salary", salSelf, salSp, 0.0, salSelf + salSp),
                IncomeColSpec("Social Security", ssSelf, ssSp, 0.0, ssSelf + ssSp),
                IncomeColSpec("Pension", penSelf, penSp, 0.0, penSelf + penSp),
                IncomeColSpec("IRA", 0.0, 0.0, iraDist, iraDist),
                IncomeColSpec("Roth", 0.0, 0.0, rothDist, rothDist),
                IncomeColSpec("Taxable Div/Int", 0.0, 0.0, taxableDiv + taxableInt, taxableDiv + taxableInt),
                IncomeColSpec("Taxable (Gain)", 0.0, 0.0, taxableDist, taxableDist, isTaxableGain = true, jointGain = realizedGain, totalGain = realizedGain)
            )

            val activeCols = allCols.filter { Math.abs(it.totalVal) > 0.001 || Math.abs(it.totalGain) > 0.001 }

            val formatCell = { col: IncomeColSpec, rowVal: Double, rowGain: Double ->
                if (col.isTaxableGain) {
                    "${fmt(rowVal)} (${fmt(rowGain)})"
                } else {
                    fmt(rowVal)
                }
            }

            val headers = mutableListOf("Person")
            for (col in activeCols) {
                headers.add(col.header)
            }
            headers.add("Total")

            val selfRow = mutableListOf("Self (${Math.round(age)})")
            for (col in activeCols) {
                selfRow.add(formatCell(col, col.selfVal, col.selfGain))
            }
            selfRow.add(fmt(totSelf))

            val spouseRow = mutableListOf("Spouse (${Math.round(ageSp)})")
            for (col in activeCols) {
                spouseRow.add(formatCell(col, col.spouseVal, col.spouseGain))
            }
            spouseRow.add(fmt(totSp))

            val jointRow = mutableListOf("Joint")
            for (col in activeCols) {
                jointRow.add(formatCell(col, col.jointVal, col.jointGain))
            }
            jointRow.add(fmt(jointTot))

            val totalRow = mutableListOf("Total")
            for (col in activeCols) {
                totalRow.add(formatCell(col, col.totalVal, col.totalGain))
            }
            totalRow.add(fmt(grandTot))

            sb.append("| ").append(headers.joinToString(" | ")).append(" |\n")
            sb.append("| :--- | ")
            sb.append(activeCols.joinToString(" | ") { "---:" })
            sb.append(" | ---: |\n")

            sb.append("| ").append(selfRow.joinToString(" | ")).append(" |\n")
            sb.append("| ").append(spouseRow.joinToString(" | ")).append(" |\n")
            sb.append("| ").append(jointRow.joinToString(" | ")).append(" |\n")

            val boldTotalRow = totalRow.mapIndexed { idx, cell -> if (idx == 0) "**$cell**" else "**$cell**" }
            sb.append("| ").append(boldTotalRow.joinToString(" | ")).append(" |\n\n")

            val htmlTableInd = StringBuilder()
            htmlTableInd.append("<table style=\"border-collapse: collapse; width: 100%; font-family: Arial, sans-serif; font-size: 9pt; margin-bottom: 12pt;\">\n")
            appendHtmlTableRow(htmlTableInd, headers, true, false)
            appendHtmlTableRow(htmlTableInd, selfRow, false, false)
            appendHtmlTableRow(htmlTableInd, spouseRow, false, false)
            appendHtmlTableRow(htmlTableInd, jointRow, false, false)
            appendHtmlTableRow(htmlTableInd, totalRow, false, true)
            htmlTableInd.append("</table>\n")
            htmlSb.append(htmlTableInd.toString())

            val ordRate = getD("fedOrdinaryBracketRate")
            val ordLimit = getD("fedOrdinaryBracketLimit")
            val cgRate = getD("fedCapGainsBracketRate")
            val cgLimit = getD("fedCapGainsBracketLimit")
            val ordInc = getD("fedTaxableOrdinaryIncome")
            val totInc = getD("fedTotalTaxableIncome")

            val pctFmt = DecimalFormat("0.0%")
            val ordRateStr = pctFmt.format(ordRate)
            val cgRateStr = pctFmt.format(cgRate)
            val ordLimitStr = if (ordLimit > 1e15) "Unlimited" else fmt(ordLimit)
            val cgLimitStr = if (cgLimit > 1e15) "Unlimited" else fmt(cgLimit)

            val taxSS = getD("fed_taxable_ss")
            val stdDed = getD("standard_deduction")
            val dafContrib = getD("daf_contrib")
            val totalDeds = getD("total_deductions")
            val medicalDed = getD("deductible_medical")

            val taxableIntVal = getD("taxable_interest")
            val taxableDivVal = getD("taxable_dividends")

            val qcdVal = getD("qcd_amount")

            val ordIncDerivationMd = buildString {
                append("    * Gross Ordinary Income components:\n")
                append("      * Salary: **").append(fmt(salSelf + salSp)).append("**\n")
                append("      * Pension: **").append(fmt(penSelf + penSp)).append("**\n")
                append("      * Interest: **").append(fmt(taxableIntVal)).append("**\n")
                val nuaTransfer = getD("nua_transfer")
                val nuaBasis = getD("nua_basis")
                if (nuaTransfer > 0.01) {
                    append("      * NUA Transfer (Taxable Basis): **").append(fmt(nuaBasis)).append("** (Total Transfer: **").append(fmt(nuaTransfer)).append("**)\n")
                }
                append("      * IRA Distribution: **").append(fmt(iraDist - rothConv - nuaTransfer)).append("**\n")
                if (rothConv > 0.01) {
                    append("      * Roth Conversion: **").append(fmt(rothConv)).append("**\n")
                }
                append("      * Taxable Social Security: **").append(fmt(taxSS)).append("** (Gross SS: **").append(fmt(ssSelf + ssSp)).append("**)\n")
                append("    * Deductions:\n")
                if (totalDeds > stdDed + 0.01) {
                    append("      * Itemized Deduction: **-").append(fmt(totalDeds)).append("**\n")
                    if (dafContrib > 0.0) {
                        append("        * DAF Contribution: **-").append(fmt(dafContrib)).append("**\n")
                    }
                    if (medicalDed > 0.0) {
                        append("        * Medical Deduction (Eldercare): **-").append(fmt(medicalDed)).append("**\n")
                    }
                } else {
                    append("      * Standard Deduction: **-").append(fmt(stdDed)).append("**\n")
                    if (qcdVal > 0.0) {
                        append("        * QCD Donation: **-").append(fmt(qcdVal)).append("**\n")
                    }
                }
            }

            val combIncDerivationMd = buildString {
                append("    * Components:\n")
                append("      * Taxable Ordinary Income: **").append(fmt(ordInc)).append("**\n")
                append("      * Taxable Capital Gains: **").append(fmt(realizedGain)).append("**\n")
                append("      * Taxable Dividends: **").append(fmt(taxableDivVal)).append("**\n")
            }

            val ordIncDerivationHtml = buildString {
                append("<ul style=\"padding-left: 20px; font-size: 9pt;\">")
                append("<li>Gross Ordinary Income components:")
                append("<ul style=\"padding-left: 20px;\">")
                append("<li>Salary: <strong>").append(fmt(salSelf + salSp)).append("</strong></li>")
                append("<li>Pension: <strong>").append(fmt(penSelf + penSp)).append("</strong></li>")
                append("<li>Interest: <strong>").append(fmt(taxableIntVal)).append("</strong></li>")
                val nuaTransfer = getD("nua_transfer")
                val nuaBasis = getD("nua_basis")
                if (nuaTransfer > 0.01) {
                    append("<li>NUA Transfer (Taxable Basis): <strong>").append(fmt(nuaBasis)).append("</strong> (Total Transfer: <strong>").append(fmt(nuaTransfer)).append("</strong>)</li>")
                }
                append("<li>IRA Distribution: <strong>").append(fmt(iraDist - rothConv - nuaTransfer)).append("</strong></li>")
                if (rothConv > 0.01) {
                    append("<li>Roth Conversion: <strong>").append(fmt(rothConv)).append("</strong></li>")
                }
                append("<li>Taxable Social Security: <strong>").append(fmt(taxSS)).append("</strong> (Gross SS: <strong>").append(fmt(ssSelf + ssSp)).append("</strong>)</li>")
                append("</ul></li>")
                append("<li>Deductions:")
                append("<ul style=\"padding-left: 20px;\">")
                if (totalDeds > stdDed + 0.01) {
                    append("<li>Itemized Deduction: <strong>-").append(fmt(totalDeds)).append("</strong></li>")
                    if (dafContrib > 0.0) {
                        append("<li>DAF Contribution: <strong>-").append(fmt(dafContrib)).append("</strong></li>")
                    }
                    if (medicalDed > 0.0) {
                        append("<li>Medical Deduction (Eldercare): <strong>-").append(fmt(medicalDed)).append("</strong></li>")
                    }
                } else {
                    append("<li>Standard Deduction: <strong>-").append(fmt(stdDed)).append("</strong></li>")
                    if (qcdVal > 0.0) {
                        append("<li>QCD Donation: <strong>-").append(fmt(qcdVal)).append("</strong></li>")
                    }
                }
                append("</ul></li>")
                append("</ul>")
            }

            val combIncDerivationHtml = buildString {
                append("<ul style=\"padding-left: 20px; font-size: 9pt;\">")
                append("<li>Taxable Ordinary Income: <strong>").append(fmt(ordInc)).append("</strong></li>")
                append("<li>Taxable Capital Gains: <strong>").append(fmt(realizedGain)).append("</strong></li>")
                append("<li>Taxable Dividends: <strong>").append(fmt(taxableDivVal)).append("</strong></li>")
                append("</ul>")
            }

            val totalExpenditures = getD("housing") + getD("travel_eldercare") + getD("other") + getD("taxes")

            sb.append("## Tax & Expense Information\n")
            sb.append("* **Federal Ordinary Income Tax Bracket**:\n")
            sb.append("  * Active Bracket Rate: **").append(ordRateStr).append("**\n")
            sb.append("  * Taxable Ordinary Income: **").append(fmt(ordInc)).append("**\n")
            sb.append(ordIncDerivationMd)
            sb.append("  * Bracket Income Limit: up to **").append(ordLimitStr).append("**\n")
            sb.append("* **Federal Capital Gains Tax Bracket**:\n")
            sb.append("  * Active Bracket Rate: **").append(cgRateStr).append("**\n")
            sb.append("  * Combined Taxable Income: **").append(fmt(totInc)).append("**\n")
            sb.append(combIncDerivationMd)
            sb.append("  * Bracket Income Limit: up to **").append(cgLimitStr).append("**\n")
            val rothConvVal = getD("roth_conversion")
            val rothLimitReason = r["roth_limit_reason"]?.toString() ?: ""
            val oldestAge = Math.max(age, ageSp)
            val rothEnabled = form["roth_enabled"]?.toBoolean() ?: false
            val rothStartAge = form["roth_start_age"]?.toDoubleOrNull() ?: 60.0
            val rothEndAge = form["roth_end_age"]?.toDoubleOrNull() ?: 75.0

            if (rothEnabled && oldestAge >= rothStartAge && oldestAge <= rothEndAge) {
                sb.append("* **Roth Conversion Details**:\n")
                sb.append("  * Amount Converted: **").append(fmt(rothConvVal)).append("**\n")
                sb.append("  * Capped / Limited By: **").append(if (rothLimitReason.isEmpty()) "N/A" else rothLimitReason).append("**\n")
            }
            sb.append("* **Expenditures**: **").append(fmt(totalExpenditures)).append("**\n")
            sb.append("  * Housing (Mortgage): ").append(fmt(r["housing"])).append("\n")
            sb.append("  * Travel & Eldercare: ").append(fmt(r["travel_eldercare"])).append("\n")
            sb.append("  * Other spending: ").append(fmt(r["other"])).append("\n")
            sb.append("  * Taxes: **").append(fmt(r["taxes"])).append("**\n")
            sb.append("    * Federal: **").append(fmt(r["fed_income_tax"])).append("**\n")
            sb.append("    * State: **").append(fmt(r["state_income_tax"])).append("**\n")
            sb.append("    * Payroll: **").append(fmt(r["payroll_tax"])).append("**\n")
            sb.append("    * Property: **").append(fmt(r["property_tax"])).append("**\n\n")

            htmlSb.append("<h2 style=\"font-family: Arial, sans-serif; font-size: 13pt; color: #555555; margin-top: 14pt; margin-bottom: 6pt;\">Tax & Expense Information</h2>\n")
            htmlSb.append("<ul style=\"font-family: Arial, sans-serif; font-size: 10pt; line-height: 1.4; margin-bottom: 12pt; padding-left: 20px;\">\n")
            htmlSb.append("<li><strong>Federal Ordinary Income Tax Bracket</strong>:\n")
            htmlSb.append("<ul style=\"padding-left: 20px;\">")
            htmlSb.append("<li>Active Bracket Rate: <strong>").append(ordRateStr).append("</strong></li>\n")
            htmlSb.append("<li>Taxable Ordinary Income: <strong>").append(fmt(ordInc)).append("</strong>")
            htmlSb.append(ordIncDerivationHtml)
            htmlSb.append("</li>\n")
            htmlSb.append("<li>Bracket Income Limit: up to <strong>").append(ordLimitStr).append("</strong></li>\n")
            htmlSb.append("</ul></li>\n")
            htmlSb.append("<li><strong>Federal Capital Gains Tax Bracket</strong>:\n")
            htmlSb.append("<ul style=\"padding-left: 20px;\">")
            htmlSb.append("<li>Active Bracket Rate: <strong>").append(cgRateStr).append("</strong></li>\n")
            htmlSb.append("<li>Combined Taxable Income: <strong>").append(fmt(totInc)).append("</strong>")
            htmlSb.append(combIncDerivationHtml)
            htmlSb.append("</li>\n")
            htmlSb.append("<li>Bracket Income Limit: up to <strong>").append(cgLimitStr).append("</strong></li>\n")
            htmlSb.append("</ul></li>\n")
            if (rothEnabled && oldestAge >= rothStartAge && oldestAge <= rothEndAge) {
                htmlSb.append("<li><strong>Roth Conversion Details</strong>:\n")
                htmlSb.append("<ul style=\"padding-left: 20px;\">")
                htmlSb.append("<li>Amount Converted: <strong>").append(fmt(rothConvVal)).append("</strong></li>\n")
                htmlSb.append("<li>Capped / Limited By: <strong>").append(if (rothLimitReason.isEmpty()) "N/A" else rothLimitReason).append("</strong></li>\n")
                htmlSb.append("</ul></li>\n")
            }
            htmlSb.append("<li><strong>Expenditures</strong>: <strong>").append(fmt(totalExpenditures)).append("</strong>\n")
            htmlSb.append("<ul style=\"padding-left: 20px;\">")
            htmlSb.append("<li>Housing (Mortgage): ").append(fmt(r["housing"])).append("</li>\n")
            htmlSb.append("<li>Travel & Eldercare: ").append(fmt(r["travel_eldercare"])).append("</li>\n")
            htmlSb.append("<li>Other spending: ").append(fmt(r["other"])).append("</li>\n")
            htmlSb.append("<li>Taxes: <strong>").append(fmt(r["taxes"])).append("</strong>\n")
            htmlSb.append("<ul style=\"padding-left: 20px;\">")
            htmlSb.append("<li>Federal: ").append(fmt(r["fed_income_tax"])).append("</li>\n")
            htmlSb.append("<li>State: ").append(fmt(r["state_income_tax"])).append("</li>\n")
            htmlSb.append("<li>Payroll: ").append(fmt(r["payroll_tax"])).append("</li>\n")
            htmlSb.append("<li>Property: ").append(fmt(r["property_tax"])).append("</li>\n")
            htmlSb.append("</ul></li>\n")
            htmlSb.append("</ul></li>\n")
            htmlSb.append("</ul>\n")

            sb.append("## Action Plan & Distributions\n")
            htmlSb.append("<h2 style=\"font-family: Arial, sans-serif; font-size: 13pt; color: #555555; margin-top: 14pt; margin-bottom: 6pt;\">Action Plan & Distributions</h2>\n")

            val logs = r["action_logs"] as? List<String> ?: emptyList()
            val actionRows = mutableListOf<ActionRow>()
            for (log in logs) {
                var row: ActionRow? = null
                var match = sellRegex.matchEntire(log)
                if (match != null) {
                    val rawAmt = match.groupValues[1]
                    val amount = if (rawAmt.startsWith("-")) "-$" + rawAmt.substring(1) else "$" + rawAmt
                    val asset = match.groupValues[2].trim()
                    val rawGain = match.groupValues[4]
                    val gain = if (rawGain.startsWith("-")) "-$" + rawGain.substring(1) else "$" + rawGain
                    val purpose = match.groupValues[5].trim().replaceFirstChar { it.uppercase() }
                    row = ActionRow("Sell", asset, amount, gain, purpose)
                } else {
                    match = convertRegex.matchEntire(log)
                    if (match != null) {
                        val rawAmt = match.groupValues[1]
                        val amount = if (rawAmt.startsWith("-")) "-$" + rawAmt.substring(1) else "$" + rawAmt
                        val fromAsset = match.groupValues[2].trim()
                        val toAsset = match.groupValues[3].trim()
                        val assetName = if (toAsset.lowercase().contains("cash")) "Roth Cash" else "Roth Stock"
                        row = ActionRow("Convert", assetName, amount, "N/A", "Roth conversion from $fromAsset")
                    } else {
                        match = donateRegex.matchEntire(log)
                        if (match != null) {
                            val rawAmt = match.groupValues[1]
                            val amount = if (rawAmt.startsWith("-")) "-$" + rawAmt.substring(1) else "$" + rawAmt
                            val asset = match.groupValues[2].trim()
                            val rawBasis = match.groupValues[3]
                            val purpose = "Donate directly to " + match.groupValues[4].trim()

                            val amtVal = rawAmt.replace(",", "").toDoubleOrNull() ?: 0.0
                            val basisVal = rawBasis.replace(",", "").toDoubleOrNull() ?: 0.0
                            val gainVal = amtVal - basisVal
                            val gainStr = if (gainVal >= 0.0) {
                                "($" + String.format("%,.2f", gainVal) + ")"
                            } else {
                                "(-$" + String.format("%,.2f", -gainVal) + ")"
                            }
                            row = ActionRow("Donate", asset, amount, gainStr, purpose)
                        } else {
                            match = qcdRegex.matchEntire(log)
                            if (match != null) {
                                val rawAmt = match.groupValues[1]
                                val amount = if (rawAmt.startsWith("-")) "-$" + rawAmt.substring(1) else "$" + rawAmt
                                val asset = match.groupValues[2].trim()
                                val purpose = match.groupValues[3].trim().replaceFirstChar { it.uppercase() }
                                row = ActionRow("QCD", asset, amount, "N/A", purpose)
                            } else {
                                match = purchaseRegex.matchEntire(log)
                                if (match != null) {
                                    val rawAmt = match.groupValues[1]
                                    val amount = if (rawAmt.startsWith("-")) "-$" + rawAmt.substring(1) else "$" + rawAmt
                                    val asset = match.groupValues[2].trim()
                                    val purpose = match.groupValues[3].trim().replaceFirstChar { it.uppercase() }
                                    row = ActionRow("Buy", asset, amount, "N/A", purpose)
                                } else {
                                    match = withdrawRegex.matchEntire(log)
                                    if (match != null) {
                                        val rawAmt = match.groupValues[1]
                                        val amount = if (rawAmt.startsWith("-")) "-$" + rawAmt.substring(1) else "$" + rawAmt
                                        val asset = match.groupValues[2].trim()
                                        val purpose = match.groupValues[3].trim().replaceFirstChar { it.uppercase() }
                                        row = ActionRow("Withdraw", asset, amount, "N/A", purpose)
                                    }
                                }
                            }
                        }
                    }
                }
                if (row != null) actionRows.add(row) else actionRows.add(ActionRow("Info", "N/A", "N/A", "N/A", log))
            }

            if (actionRows.isEmpty()) {
                sb.append("No stock transactions or rebalancing events occurred this year.\n")
                htmlSb.append("<p style=\"font-family: Arial, sans-serif; font-size: 10pt;\">No stock transactions or rebalancing events occurred this year.</p>\n")
            } else {
                sb.append("| Transaction Type | Asset / Stock Name | Amount | Realized Gain | Purpose |\n")
                sb.append("| :--- | :--- | :--- | :--- | :--- |\n")
                for (row in actionRows) {
                    sb.append("| ").append(row.type).append(" | ").append(row.asset).append(" | ").append(row.amount).append(" | ").append(row.realizedGain).append(" | ").append(row.purpose).append(" |\n")
                }
                sb.append("\n")

                val htmlTableAct = StringBuilder()
                htmlTableAct.append("<table style=\"border-collapse: collapse; width: 100%; font-family: Arial, sans-serif; font-size: 10pt; margin-bottom: 12pt;\">\n")
                appendHtmlTableRow(htmlTableAct, listOf("Transaction Type", "Asset / Stock Name", "Amount", "Realized Gain", "Purpose"), true, false)
                for (row in actionRows) {
                    val tdStyle = "padding: 6px 8px; border: 1px solid #cccccc;"
                    htmlTableAct.append("<tr>")
                    htmlTableAct.append("<td style=\"text-align: left; ").append(tdStyle).append("\">").append(row.type).append("</td>")
                    htmlTableAct.append("<td style=\"text-align: left; ").append(tdStyle).append("\">").append(row.asset).append("</td>")
                    htmlTableAct.append("<td style=\"text-align: right; ").append(tdStyle).append("\">").append(row.amount).append("</td>")
                    htmlTableAct.append("<td style=\"text-align: right; ").append(tdStyle).append("\">").append(row.realizedGain).append("</td>")
                    htmlTableAct.append("<td style=\"text-align: left; ").append(tdStyle).append("\">").append(row.purpose).append("</td>")
                    htmlTableAct.append("</tr>\n")
                }
                htmlTableAct.append("</table>\n")
                htmlSb.append(htmlTableAct.toString())
            }

            if (i < currentResults.size - 1) {
                sb.append("\n<div style=\"page-break-after: always;\"></div>\n\n")
            }
        }
        val htmlDoc = "<html><body style=\"font-family: Arial, sans-serif; font-size: 10pt; color: #333333; line-height: 1.4;\">" + htmlSb.toString() + "</body></html>"

        try {
            val selection = HtmlSelection(htmlDoc, sb.toString())
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            JOptionPane.showMessageDialog(parent, "Annual Financial Instruction Sheet exported successfully and copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Failed to copy to clipboard: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun copyStockLots(
        parent: Component,
        selectedRow: Int,
        currentResults: List<Map<String, Any>>
    ) {
        if (selectedRow < 0 || selectedRow >= currentResults.size) {
            JOptionPane.showMessageDialog(parent, "Please select a year in the table first.", "Info", JOptionPane.WARNING_MESSAGE)
            return
        }

        val r = currentResults[selectedRow]
        val yr = r["year"]?.toString() ?: "Unknown"

        @Suppress("UNCHECKED_CAST")
        val lots = r["stock_lots"] as? List<StockLot> ?: emptyList()
        if (lots.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No taxable stock lots found for Year $yr.", "Info", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val sortedLots = lots.sortedByDescending { it.basisRatio }

        val sb = StringBuilder()
        sb.append("Name\tCurrent Value\tBasis\tGain\tPercent Gain/Loss\n")

        val df = DecimalFormat("$#,##0.00")
        val pf = DecimalFormat("0.00%")
        for (lot in sortedLots) {
            val gain = lot.currentVal - lot.costBasis
            val pct = if (lot.costBasis > 0.0) gain / lot.costBasis else 0.0
            sb.append(lot.name).append("\t")
                .append(df.format(lot.currentVal)).append("\t")
                .append(df.format(lot.costBasis)).append("\t")
                .append(df.format(gain)).append("\t")
                .append(pf.format(pct)).append("\n")
        }

        try {
            val selection = StringSelection(sb.toString())
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            JOptionPane.showMessageDialog(parent, "Stock lots for Year $yr successfully copied to clipboard in spreadsheet format!", "Success", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Failed to copy to clipboard: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}
