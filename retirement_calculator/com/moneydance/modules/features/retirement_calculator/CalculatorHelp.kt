package com.moneydance.modules.features.retirement_calculator

import java.awt.Dimension
import javax.swing.JEditorPane
import javax.swing.JScrollPane

object CalculatorHelp {

    fun createHelpPanel(): JScrollPane {
        val helpPane = JEditorPane()
        helpPane.contentType = "text/html"
        helpPane.isEditable = false
        helpPane.text = HELP_HTML
        helpPane.caretPosition = 0 // Scroll to top initially
        
        val scrollPane = JScrollPane(helpPane)
        scrollPane.verticalScrollBar.unitIncrement = 16
        return scrollPane
    }

    private val HELP_HTML = """
    <html>
    <head>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            font-size: 13px;
            color: #24292e;
            background-color: #ffffff;
            margin: 20px;
            line-height: 1.5;
        }
        h1 {
            color: #1a527f;
            font-size: 22px;
            border-bottom: 2px solid #1a527f;
            padding-bottom: 6px;
            margin-top: 10px;
            margin-bottom: 14px;
        }
        h2 {
            color: #2c3e50;
            font-size: 16px;
            border-bottom: 1px solid #eaecef;
            padding-bottom: 4px;
            margin-top: 22px;
            margin-bottom: 10px;
        }
        h3 {
            color: #34495e;
            font-size: 14px;
            margin-top: 14px;
            margin-bottom: 6px;
        }
        p {
            margin-top: 6px;
            margin-bottom: 10px;
        }
        ul, ol {
            margin-top: 4px;
            margin-bottom: 12px;
            padding-left: 24px;
        }
        li {
            margin-bottom: 5px;
        }
        code {
            font-family: Consolas, "Liberation Mono", Courier, monospace;
            font-size: 12px;
            background-color: #f6f8fa;
            padding: 2px 4px;
            border-radius: 3px;
        }
        .callout {
            background-color: #f0f7ff;
            border-left: 4px solid #0366d6;
            padding: 10px 14px;
            margin-bottom: 14px;
        }
        .tip {
            background-color: #f6ffed;
            border-left: 4px solid #52c41a;
            padding: 10px 14px;
            margin-bottom: 14px;
        }
        .warning {
            background-color: #fffbe6;
            border-left: 4px solid #faad14;
            padding: 10px 14px;
            margin-bottom: 14px;
        }
        table.grid {
            border-collapse: collapse;
            width: 100%;
            margin-top: 8px;
            margin-bottom: 16px;
        }
        table.grid th {
            background-color: #f1f5f9;
            color: #334155;
            text-align: left;
            padding: 7px 10px;
            border: 1px solid #cbd5e1;
            font-size: 12px;
        }
        table.grid td {
            padding: 6px 10px;
            border: 1px solid #e2e8f0;
            font-size: 12px;
        }
        table.grid tr:nth-child(even) {
            background-color: #f8fafc;
        }
    </style>
    </head>
    <body>

    <h1>Moneydance Retirement Calculator Guide</h1>
    <p>
        Welcome to the <strong>Moneydance Retirement Calculator</strong>. This extension provides an institutional-grade, multi-account retirement forecasting engine designed specifically to work seamlessly with your active Moneydance financial records. It models real-world tax rules, capital gains with specific stock lot basis tracking, Required Minimum Distributions (RMDs), Roth conversion optimization, dynamic Medicare IRMAA tiers, multi-phase eldercare costs, and stochastic Monte Carlo market volatility.
    </p>

    <div class="callout">
        <strong>Quick-Start Workflow:</strong>
        <ol style="margin-top: 4px; margin-bottom: 4px;">
            <li>Click <strong>Load Balances</strong> to pull current investment totals and taxable stock lots from Moneydance.</li>
            <li>Review birthdates, retirement ages, and baseline living expenses under the configuration tabs on the left.</li>
            <li>Click <strong>Recalculate</strong> to generate your baseline single-path projection table and Net Worth chart.</li>
            <li>Click <strong>Run Simulation</strong> to stress-test your portfolio across 100+ randomized market paths.</li>
            <li>Click <strong>Optimize Ages</strong> to automatically find the Social Security and pension claiming ages that maximize your portfolio survival rate.</li>
        </ol>
    </div>

    <h2>1. Toolbar Actions & Core Operations</h2>
    <ul>
        <li><strong>Recalculate (F5):</strong> Evaluates a deterministic, year-by-year cashflow projection using your baseline investment returns and inflation rate. Updates both the Projections Table and the Net Worth Chart.</li>
        <li><strong>Run Simulation:</strong> Executes a stochastic Monte Carlo simulation (default 100 iterations) using Box-Muller Gaussian distributions for ROI and inflation. Generates the 5th, 25th, 50th (median), 75th, and 95th percentile curves on the Simulation Chart. Clicking any individual curve instantly loads that specific simulated economic path into the Projections Table.</li>
        <li><strong>Optimize Ages:</strong> Runs an intelligent 3-phase optimization engine (coarse grid search, fine-grained exploration, and full Monte Carlo ranking) to discover the optimal combination of Social Security and pension claiming ages for Self and Spouse. Check the <em>Lock</em> box next to any age to hold it constant during optimization.</li>
        <li><strong>Load Balances:</strong> Connects directly to Moneydance's active data file. It categorizes accounts into Traditional IRA/401(k), Roth IRA, Taxable Brokerage, and Donor Advised Funds (DAF). For taxable brokerage accounts, it automatically identifies Money Market Funds (MMFs) and extracts individual security stock lots with their actual cost bases.</li>
        <li><strong>Export (Financial Plan):</strong> Generates an executive annual financial instruction sheet formatted in both styled HTML and GitHub Markdown, copied straight to your clipboard. It summarizes required cash distributions, tax withholdings, Roth conversions, Qualified Charitable Distributions (QCDs), and rebalancing steps for the upcoming year.</li>
        <li><strong>Print Report:</strong> Opens a printable report dialog with a clean, printer-friendly summary of your retirement plan and annual projections.</li>
        <li><strong>Save / Open Config:</strong> Saves or loads all configuration settings to/from an external JSON file. The extension also supports <em>drag-and-drop</em>: simply drag any saved <code>.json</code> configuration file directly onto the calculator window to load it. Your settings are also automatically preserved in Moneydance's secure internal storage.</li>
    </ul>

    <h2>2. Configuration Tabs</h2>

    <h3>A. Economic Settings</h3>
    <table class="grid">
        <tr><th width="30%">Setting</th><th>Description</th></tr>
        <tr><td><strong>Start Date</strong></td><td>The starting date of the plan (defaults to current date). Synchronized with Moneydance account balances.</td></tr>
        <tr><td><strong>Self / Spouse Life Expectancy</strong></td><td>Age through which projections run. Lock checkboxes allow fixing life expectancy during optimization.</td></tr>
        <tr><td><strong>Investment Return (%)</strong></td><td>Nominal expected annual portfolio return (e.g. 6.0%).</td></tr>
        <tr><td><strong>Return Std Dev (%)</strong></td><td>Annual volatility / standard deviation of returns (e.g. 15.0%) used by the Monte Carlo simulator.</td></tr>
        <tr><td><strong>Inflation Rate (%)</strong></td><td>Baseline annual inflation rate (e.g. 2.25%) applied to spending, tax brackets, and Social Security COLAs.</td></tr>
        <tr><td><strong>Inflation Std Dev (%)</strong></td><td>Standard deviation of inflation (e.g. 1.25%) used in stochastic simulations.</td></tr>
        <tr><td><strong>Interest & Dividend Rates (%)</strong></td><td>Yield on cash/MMF holdings (default 3.0%) and taxable dividend rate (default 0.5%).</td></tr>
    </table>

    <h3>B. People (Self & Spouse)</h3>
    <table class="grid">
        <tr><th width="30%">Setting</th><th>Description</th></tr>
        <tr><td><strong>Birthdates</strong></td><td>Exact birthdates used to calculate precise age thresholds, Social Security Full Retirement Age, RMD triggers, and Medicare ages.</td></tr>
        <tr><td><strong>Starting Salary & Raise (%)</strong></td><td>Pre-retirement earned income and projected annual wage growth until retirement.</td></tr>
        <tr><td><strong>Retirement Age</strong></td><td>Age at which earned income ceases and living expenses transition to portfolio distributions.</td></tr>
        <tr><td><strong>Social Security Start Age</strong></td><td>Claiming age between 62.0 and 70.0 (supports fractional ages like 66.5). Can be locked during optimization.</td></tr>
        <tr><td><strong>Social Security PIA @ 67</strong></td><td>Primary Insurance Amount at Full Retirement Age. The model applies statutory actuarial adjustments: ~6.67%/yr reduction down to 70% at 62, and 8%/yr delayed retirement credits up to 124% at 70.</td></tr>
        <tr><td><strong>Pensions (Manage Buttons)</strong></td><td>Opens the multi-pension manager. Supports multiple distinct pensions per spouse with custom nonlinear benefit curves (early reduction vs delayed credits) and spreadsheet clipboard pasting.</td></tr>
        <tr><td><strong>One-Time Incomes</strong></td><td>Pre- or post-retirement lump-sum events (bonuses, inheritances, NUA stock sales) with designated dates and cost basis.</td></tr>
    </table>

    <h3>C. Savings & Balances</h3>
    <table class="grid">
        <tr><th width="30%">Account Type</th><th>Tax Treatment & Model Mechanics</th></tr>
        <tr><td><strong>Tax-Deferred (Traditional IRA / 401k)</strong></td><td>Ordinary income upon withdrawal. Subject to IRS Required Minimum Distributions (RMDs) starting at statutory age (73/75). Available for tax-free Qualified Charitable Distributions (QCDs) after age 70½.</td></tr>
        <tr><td><strong>Tax-Free (Roth IRA)</strong></td><td>Distributions are completely tax-free. No RMDs during lifetime. Used as a surgical buffer to prevent pushing taxable income into higher brackets or IRMAA tiers.</td></tr>
        <tr><td><strong>Taxable (Brokerage / MMF)</strong></td><td>Separated into liquid cash/MMF (interest taxed as ordinary income) and equities (subject to long-term capital gains tax). Automatically tracks individual stock lots with specific cost bases.</td></tr>
        <tr><td><strong>Donor Advised Fund (DAF)</strong></td><td>Dedicated charitable gifting account. Can fund baseline annual giving or distribute a percentage of excess portfolio gains.</td></tr>
    </table>

    <h3>D. Expenses</h3>
    <ul>
        <li><strong>Base Other Spending:</strong> Annual general household living costs (groceries, utilities, insurance, leisure), indexed annually for inflation.</li>
        <li><strong>Property Taxes:</strong> Annual real estate property taxes, inflating over time.</li>
        <li><strong>Housing / Mortgage:</strong> Fixed monthly/annual mortgage payment with a configurable payoff date (e.g. 12/31/2034). Outflows drop automatically once the loan matures.</li>
        <li><strong>Travel:</strong> Dedicated active-retirement travel budget, allowing higher early-retirement spending that can taper off in later years.</li>
        <li><strong>3-Phase Eldercare:</strong> Realistic late-life healthcare modeling covering three distinct stages:
            <ol>
                <li><em>Light Assistance:</em> In-home assistance (e.g., 2 years at $40,000/yr).</li>
                <li><em>Facility Care:</em> Assisted living or memory care (e.g., 2 years at $120,000/yr).</li>
                <li><em>High Acuity:</em> Intensive skilled nursing care (e.g., 1 year at $180,000/yr).</li>
            </ol>
            Eligible medical expenses exceeding 7.5% of AGI are automatically deducted on Schedule A to model real-world tax relief during high-cost care years.
        </li>
        <li><strong>One-Time Expenses:</strong> Non-recurring capital outflows (home renovations, weddings, vehicle purchases, college tuition).</li>
    </ul>

    <h3>E. Taxes & Roth Strategy</h3>
    <ul>
        <li><strong>Filing Status:</strong> Models Married Filing Jointly (MFJ) and Single brackets, standard deductions, and senior bonus additions (age 65+).</li>
        <li><strong>Social Security Taxation:</strong> Automatically evaluates provisional income against IRS statutory thresholds ($32,000 and $44,000 for MFJ), taxing 0%, 50%, or 85% of Social Security benefits.</li>
        <li><strong>Capital Gains Brackets:</strong> Tracks capital gains thresholds (0%, 15%, 20%) layered on top of ordinary income.</li>
        <li><strong>Roth Conversion Engine:</strong>
            <ul>
                <li><em>Target Ordinary Bracket:</em> Converts Traditional IRA dollars into Roth up to the ceiling of the selected bracket (e.g., top of 12% or 22% bracket).</li>
                <li><em>Capital Gains Cap:</em> Prevents conversions from pushing preferential taxable capital gains into the 15% bracket.</li>
                <li><em>Annual Tax Cap:</em> Restricts the maximum out-of-pocket tax generated by conversions in any single year.</li>
            </ul>
        </li>
    </ul>

    <h2>3. The Cash Flow & Distribution Waterfall Engine</h2>
    <p>
        In every projected year, the calculator evaluates cash needs and tax consequences through a strict, tax-optimized waterfall:
    </p>
    <ol>
        <li><strong>Aggregate Required Outflows:</strong> Sums living expenses, property taxes, active mortgages, eldercare, travel, and charitable gifts.</li>
        <li><strong>Apply Non-Portfolio Inflows:</strong> Applies earned salaries, Social Security benefits, pension payments, and one-time income events.</li>
        <li><strong>Execute Qualified Charitable Distributions (QCDs):</strong> If age &ge; 70½, charitable giving is satisfied directly from Traditional IRA funds up to the statutory limit ($108,000 indexed). This satisfies RMD requirements dollar-for-dollar while entirely excluding the amount from Adjusted Gross Income (AGI).</li>
        <li><strong>Satisfy Required Minimum Distributions (RMDs):</strong> If the IRS Uniform Lifetime Table mandates an RMD exceeding the year's cash distribution requirement, the excess is distributed and either reinvested or converted to Roth.</li>
        <li><strong>Harvest Taxable Cash & Dividends:</strong> Liquid taxable cash and dividend earnings are drawn first to meet remaining expenses.</li>
        <li><strong>Taxable Equities (Specific Lot Harvesting):</strong> If additional funds are required, equities are sold starting with the <em>highest basis ratio lots</em> (lots with the lowest embedded capital gains) to minimize capital gains tax impact.</li>
        <li><strong>Traditional IRA Distributions:</strong> Withdrawn up to the optimal tax bracket ceiling or to fulfill remaining RMD requirements.</li>
        <li><strong>Roth Conversions:</strong> If room remains under the user's selected tax bracket ceiling, Traditional IRA funds are converted to Roth IRA.</li>
        <li><strong>Tax-Free Roth Distributions:</strong> Roth IRA funds are drawn last to cover any remaining cash deficits, preserving low taxable income and preventing bracket creep or Medicare IRMAA surcharges.</li>
        <li><strong>Surplus Reinvestment:</strong> In surplus years (e.g., while working), excess income is automatically reinvested into taxable savings.</li>
    </ol>

    <h2>4. Interactive Features & Pro Tips</h2>

    <div class="tip">
        <strong>Pro-Tip: Inspecting Future Stock Lots</strong><br>
        Right-click any row in the <strong>Projections Table</strong> and select <em>"Copy Stock Lots for selected year"</em>. The calculator will copy a tab-delimited spreadsheet of every remaining taxable stock lot, current value, cost basis, unrealized gain, and gain percentage to your clipboard for that specific future year.
    </div>

    <div class="tip">
        <strong>Pro-Tip: Interrogating Monte Carlo Scenarios</strong><br>
        On the <strong>Simulation Chart</strong> tab, clicking any individual percentile line or simulation curve immediately loads that specific random market trajectory into the <strong>Projections Table</strong> and updates the title bar. This lets you inspect the exact year-by-year sequence-of-returns risk for adverse market scenarios (e.g. 5th percentile).
    </div>

    <div class="tip">
        <strong>Pro-Tip: Rich Table Tooltips</strong><br>
        Hover your mouse over any cell in the Projections Table to see a rich HTML tooltip detailing the exact breakdown of earned income, Social Security, pensions, distribution sources, deductions, federal/state taxes, and Roth conversion amounts for that year.
    </div>

    <div class="warning">
        <strong>Locking Ages During Optimization:</strong><br>
        When using <strong>Optimize Ages</strong>, check the lock box next to Self SS Age, Spouse SS Age, or any pension in the Pension Manager to prevent the optimizer from modifying that parameter. This is especially useful if one spouse is already claiming Social Security or has a mandatory pension commencement date.
    </div>

    <h2>5. Frequently Asked Questions</h2>
    <p><strong>Q: Why does the Projections Table show some cells highlighted in color?</strong><br>
    A: Green cells indicate surplus years where new savings were added to the portfolio. Salmon/orange cells indicate years where distributions were required to meet spending needs.</p>

    <p><strong>Q: What does the bold HTML text in the IRA Distribution column mean?</strong><br>
    A: Bold text indicates that the IRA distribution was strictly dictated by the IRS Required Minimum Distribution (RMD) mandate rather than living expense needs.</p>

    <p><strong>Q: Can I copy table data to Excel or Google Sheets?</strong><br>
    A: Yes! Simply select any cell or row range in the Projections Table and press <code>Ctrl+C</code>. The table automatically cleans all HTML formatting and places clean, tab-separated values on your clipboard ready to paste into any spreadsheet.</p>

    <p><strong>Q: How do Guardrails work in the simulation?</strong><br>
    A: Guardrails model dynamic spending adjustments (e.g., Guyton-Klinger rules). If portfolio balances drop significantly below target trajectories, non-discretionary spending is automatically trimmed to preserve principal and reduce failure risk.</p>

    <hr style="border: 0; border-top: 1px solid #eaecef; margin-top: 25px; margin-bottom: 15px;">
    <p style="font-size: 11px; color: #6a737d; text-align: center;">
        Moneydance Retirement Calculator Extension &bull; Built with Kotlin & Swing
    </p>

    </body>
    </html>
    """.trimIndent()
}
