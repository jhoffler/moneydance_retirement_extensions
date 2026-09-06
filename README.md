# Moneydance Retirement Extensions

A suite of native extensions for [Moneydance](https://infinitekind.com/moneydance) designed to provide institutional-grade retirement planning, tax-aware multi-account withdrawal sequencing, stochastic Monte Carlo market simulation, claiming age optimization, and tax-bucket portfolio aggregation.

---

## Extensions in this Suite

### 1. Retirement Calculator (`retirement_calculator`)
A multi-decade, tax-optimized retirement forecasting engine that integrates directly with your active Moneydance data file.

* **Direct Moneydance Integration**: Automatically discovers and categorizes investment accounts into Traditional IRA/401(k), Roth IRA, Taxable Brokerage, and Donor Advised Funds (DAF). Identifies Money Market Funds (MMFs) and extracts individual security stock lots with actual cost bases.
* **Household Modeling**: Supports both Single and Married Filing Jointly (MFJ) households with independent birthdates, retirement dates, wage growth trajectories, Social Security benefits, and multiple discrete pension plans.
* **Actuarial Social Security Engine**: Calculates Full Retirement Age (FRA = 67) Primary Insurance Amounts (PIA) with statutory actuarial adjustments: ~6.67%/year reduction down to 70% at age 62, and 8%/year delayed retirement credits up to 124% at age 70.
* **Multi-Pension Manager**: Handles multiple distinct pensions per spouse with nonlinear benefit schedules (early retirement penalties vs. delayed credits) and support for copying/pasting benefit tables directly from spreadsheets.
* **3-Phase Late-Life Eldercare**: Models Light Assistance, Facility / Assisted Living, and Skilled Nursing / High-Acuity care phases. Automatically factors in Schedule A itemized medical deductions for expenses exceeding 7.5% of AGI.
* **Tax-Optimized Distribution Waterfall**:
  1. *Qualified Charitable Distributions (QCDs)*: Directly from Traditional IRA (age &ge; 70½) up to statutory caps ($108,000 indexed), satisfying RMDs dollar-for-dollar while entirely excluding the amount from AGI.
  2. *Required Minimum Distributions (RMDs)*: Evaluated using the IRS Uniform Lifetime Table against prior-year account balances.
  3. *Taxable Cash & Dividends*: First line of defense for living expenses.
  4. *Taxable Equities (Specific Lot Harvesting)*: Automatically sells highest basis-ratio lots (lowest embedded capital gains) to minimize capital gains tax liability.
  5. *Traditional IRA Distributions*: Fills lower ordinary income tax brackets.
  6. *Systematic Roth Conversions*: Automatically executes Roth conversions up to the user-selected tax bracket ceiling (e.g., top of 12% or 22% bracket) or annual tax budget.
  7. *Tax-Free Roth Buffer*: Draws from Roth accounts only to avoid pushing ordinary income or capital gains into higher brackets or triggering Medicare IRMAA surcharges.
* **Chained CPI (C-CPI-U) Indexing**: In compliance with the Tax Cuts and Jobs Act (TCJA), ordinary income tax brackets, capital gains brackets, and standard deductions are adjusted using Chained CPI-U (~0.2% lower than general CPI-U inflation).
* **3-Phase Age Optimization Engine**: Automated search algorithm (coarse grid search &rarr; fine-tuning pass &rarr; full Monte Carlo ranking) that determines the optimal Social Security and pension claiming ages for Self and Spouse to maximize portfolio survival probability and median ending net worth.
* **Stochastic Monte Carlo Simulation**: Box-Muller Gaussian normal distribution simulator evaluating 100+ volatile economic paths. Plots an interactive fan chart with 5th, 25th, 50th (median), 75th, and 95th percentile bands. Clicking any percentile curve immediately populates the Projections Table with that exact market scenario.
* **Annual Financial Plan Exporter**: Generates structured Markdown and styled HTML instruction sheets for distributions, tax withholdings, Roth conversions, QCDs, and specific stock lot sales, formatted ready for clipboard copying or printing.

---

### 2. Retirement Portfolio Summary (`retirement_portfolio_summary`)
A dashboard that classifies your Moneydance accounts into clear tax-treatment categories for retirement analysis:

* **Tax-Deferred Accounts**: Traditional IRAs, Rollover IRAs, SEP IRAs, 401(k)s, 403(b)s.
* **Tax-Free Accounts**: Roth IRAs, Roth 401(k)s, Health Savings Accounts (HSAs).
* **Taxable Accounts**: Brokerage accounts, bank accounts, Money Market Funds (MMFs), certificates of deposit.
* **Charitable Accounts**: Donor Advised Funds (DAF) and dedicated gifting trusts.
* **Allocation Breakdown**: Displays liquid cash vs. equity/bond balances, total cost basis, embedded unrealized gains, and portfolio percentages with built-in printing support.

---

## Codebase Architecture

Both extensions are built with **Kotlin** and **Java Swing**, adhering to a modular, single-responsibility architecture:

```
moneydance/
├── retirement_calculator/
│   ├── build_extension.py                         # Build & packaging script
│   └── com/moneydance/modules/features/retirement_calculator/
│       ├── Main.kt                                # FeatureModule lifecycle entry point
│       ├── CalculatorWindow.kt                    # Primary UI layout, tabbed navigation & toolbar
│       ├── CalculatorModel.kt                     # YearRow projection engine & tax waterfall
│       ├── CalculatorDataModels.kt                # Domain structures, tax brackets & constants
│       ├── CalculatorHelp.kt                      # In-app HTML documentation & user guide
│       ├── CalculatorStorage.kt                   # JSON serialization & localStorage persistence
│       ├── CalculatorTableRenderers.kt            # Color-coded cell rendering & multiline HTML tooltips
│       ├── CalculatorChartPanels.kt               # Net worth 2D chart & Monte Carlo fan chart
│       ├── AgeOptimizationEngine.kt               # 3-Phase Social Security & pension age optimizer
│       ├── FinancialPlanExporter.kt               # HTML/Markdown financial instruction sheet generator
│       ├── MoneydanceDataLoader.kt                # Moneydance API account interrogation & lot loader
│       ├── MonteCarloSimulator.kt                 # Box-Muller Gaussian multi-run simulator
│       ├── PensionDialogs.kt                      # Multi-pension manager & nonlinear schedule editor
│       ├── FinancialEventDialogs.kt               # One-time income and expense event manager
│       ├── RmdCalculator.kt                       # IRS Uniform Lifetime Table & RMD logic
│       └── meta_info.dict                         # Extension manifest
│
├── retirement_portfolio_summary/
│   ├── build_extension.py                         # Build & packaging script
│   └── com/moneydance/modules/features/retirement_portfolio_summary/
│       ├── Main.kt                                # FeatureModule entry point & UI
│       └── meta_info.dict                         # Extension manifest
│
└── README.md
```

---

## Requirements

* **Moneydance**: Version 2023.0 (build 3056) or newer (tested on Moneydance 2024+).
* **Java**: Java 21 JRE (bundled standard with Moneydance installations).
* **Python**: Python 3.8+ (used to run the build and packaging scripts).

---

## Building from Source

Each extension includes a standalone `build_extension.py` script that compiles all Kotlin source files using the embedded kotlinc compiler, writes manifest metadata, packages the `.mxt` bundle, and optionally copies the output to a designated directory.

### To build the Retirement Calculator:
```bash
cd retirement_calculator
python build_extension.py
```
*Output:* `retirement_calculator/retirement_calculator.mxt`

### To build the Retirement Portfolio Summary:
```bash
cd retirement_portfolio_summary
python build_extension.py
```
*Output:* `retirement_portfolio_summary/retirement_portfolio_summary.mxt`

---

## Installation into Moneydance

1. Open **Moneydance**.
2. Drag and drop the generated `.mxt` file directly onto the Moneydance application window.
   * *Alternatively:* In the top menu, navigate to **Extensions** &rarr; **Manage Extensions...** &rarr; **Add from file...**, select the `.mxt` file, and confirm.
3. The extensions will appear under the **Extensions** menu:
   * **Retirement Calculator**
   * **Retirement Portfolio Summary**

---

## Pro-Tips & Shortcuts

* **Right-Click Future Stock Lots**: Right-click any row in the *Projections Table* and select *"Copy Stock Lots for selected year"* to copy a tab-delimited spreadsheet of remaining taxable stock lots, current value, cost basis, unrealized gain, and gain percentage.
* **Spreadsheet Copy**: Select any range of rows or cells in the *Projections Table* and press `Ctrl+C` (`Cmd+C` on macOS) to copy clean, tab-separated values ready for pasting into Excel or Google Sheets.
* **Drag-and-Drop Configuration**: Drag any saved `.json` retirement scenario directly onto the *Retirement Calculator* window to instantly load it.
* **Inspect Stochastic Scenarios**: Click on any individual percentile path on the *Simulation Chart* tab to load that specific market sequence directly into the *Projections Table*.
* **Lock Variables for Optimization**: In the configuration panel, check the *Lock* box next to Self SS Age, Spouse SS Age, or any pension to hold them fixed during age optimization.

---

## Author & License

* **Developer**: John Hoffler
* **Website**: [hofflergroup.com](https://hofflergroup.com)
* Developed for use with [The Infinite Kind's Moneydance](https://infinitekind.com/moneydance).
