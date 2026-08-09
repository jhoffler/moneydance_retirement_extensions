package com.moneydance.modules.features.retirement_calculator

import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder
import java.awt.*
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.DecimalFormat
import java.time.LocalDate
import java.util.Vector
import kotlin.math.max
import kotlin.math.min
import java.awt.datatransfer.DataFlavor

class CalculatorWindow(private val extension: Main, private val mdBook: com.infinitekind.moneydance.model.AccountBook) : JFrame("Retirement Calculator") {
    
    // Map of text fields and other inputs
    private val dollarKeys = setOf(
        "salary", "salary_spouse", "ss_pia", "ss_pia_spouse", "pension_early", "pension_late", 
        "pension_early_spouse", "pension_late_spouse", "start_other_spending", "prop_taxes", 
        "fed_std_deduction", "state_std_deduction", "mortgage", "travel", "eldercare", "daf_distro",
        "start_ira_savings", "start_ira_cash", "start_roth_savings", "start_roth_cash", "start_other_savings", "start_other_cash", "start_taxable_cost_basis", "start_daf_savings"
    )
    private val textFields = mutableMapOf<String, JTextField>()
    private val checkboxes = mutableMapOf<String, JCheckBox>()
    private val radioButtons = mutableMapOf<String, JRadioButton>()
    private val dateFields = mutableMapOf<String, com.moneydance.awt.JDateField>()
    
    // UI elements
    private val tableModel = DefaultTableModel()
    private val resultTable = JTable(tableModel)
    private val mainTabbedPane = JTabbedPane()
    
    private val chartPanel = SavingsChartPanel()
    private val simChartPanel = SimulationChartPanel()
    
    // Data list representing current recalc results
    private val currentResults = mutableListOf<Map<String, Any>>()
    
    // Default form configuration
    private val defaultData = mapOf(
        "start_year" to "2026",
        "investment_return" to "6",
        "inflation" to "2.25",
        "lifetime" to "100",
        "investment_std_dev" to "15",
        "inflation_std_dev" to "1.25",
        "raise" to "3",
        "birthdate" to "1/1/1968",
        "salary" to "60000",
        "retirement_age" to "62",
        "ss_age" to "67",
        "ss_pia" to "1000",
        "pension_age" to "67",
        "pension_early" to "0",
        "pension_late" to "0",
        "birthdate_spouse" to "1/1/1970",
        "salary_spouse" to "30000",
        "retirement_age_spouse" to "63",
        "ss_age_spouse" to "67",
        "ss_pia_spouse" to "1000",
        "pension_age_spouse" to "67",
        "pension_early_spouse" to "0",
        "pension_late_spouse" to "0",
        "start_other_spending" to "30000",
        "prop_taxes" to "8000",
        "fed_std_deduction" to "27700",
        "payroll_tax_rate" to "7.65",
        "state_std_deduction" to "22500",
        "state_tax_rate" to "4.5",
        "mortgage" to "2000",
        "mortgage_end" to "12/31/2034",
        "travel" to "25000",
        "travel_end" to "12/31/2045",
        "eldercare" to "10000",
        "eldercare_start" to "01/01/2050",
        "daf_distro" to "0",
        "daf_excess_pct" to "50",
        "start_ira_savings" to "1000000",
        "start_ira_cash" to "100000",
        "start_roth_savings" to "1000000",
        "start_roth_cash" to "100000",
        "start_other_savings" to "1000000",
        "start_other_cash" to "100000",
        "start_taxable_cost_basis" to "",
        "start_daf_savings" to "0",
        "stack_incomes" to "true",
        "stack_savings" to "false",
        "show_distros" to "false",
        "show_inflation_roi" to "false",
        "guardrail_percent" to "10",
        "num_simulations" to "100"
    )

    init {
        setupUI()
        
        val today = LocalDate.now()
        dateFields["start_date"]?.dateInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth

        // Initial populate from default data
        setFormData(defaultData)
        
        // Read account balances from MoneyDance if possible
        loadMoneyDanceBalances()
        
        // Perform initial calculation
        recalc()
        
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        pack()
        setLocationRelativeTo(null)
        
        // Support drag-n-drop of config files
        this.transferHandler = FileDropHandler { file ->
            loadConfigurationFromFile(file)
        }
    }

    private fun setupUI() {
        preferredSize = Dimension(1200, 800)
        layout = BorderLayout()
        
        // 1. Toolbar at the top
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        
        val recalcBtn = JButton("Recalculate")
        recalcBtn.addActionListener { recalc() }
        toolbar.add(recalcBtn)
        
        val simBtn = JButton("Run Simulation")
        simBtn.addActionListener { runSimulation() }
        toolbar.add(simBtn)
        
        toolbar.addSeparator()
        
        val openBtn = JButton("Open Config")
        openBtn.addActionListener { openConfigFile() }
        toolbar.add(openBtn)
        
        val saveBtn = JButton("Save Config")
        saveBtn.addActionListener { saveConfigFile() }
        toolbar.add(saveBtn)
        
        toolbar.addSeparator()
        
        val printBtn = JButton("Print Report")
        printBtn.addActionListener { printReport() }
        toolbar.add(printBtn)
        
        val exportBtn = JButton("Export")
        exportBtn.addActionListener { exportInstructions() }
        toolbar.add(exportBtn)
        
        toolbar.addSeparator()
        
        val loadBtn = JButton("Load Balances")
        loadBtn.addActionListener { 
            loadMoneyDanceBalances()
            recalc()
        }
        toolbar.add(loadBtn)
        
        add(toolbar, BorderLayout.NORTH)
        
        // 2. Left side: Configurations Tabbed Pane
        val configTabs = JTabbedPane()
        configTabs.preferredSize = Dimension(400, 700)
        
        configTabs.addTab("Economic", createEconomicPanel())
        configTabs.addTab("Income", createIncomePanel())
        configTabs.addTab("Expenses", createExpensesPanel())
        configTabs.addTab("Savings", createSavingsPanel())
        configTabs.addTab("Settings", createSettingsPanel())
        
        // 3. Right side: Results Tabbed Pane
        mainTabbedPane.addTab("Projections Table", createTablePanel())
        mainTabbedPane.addTab("Net Worth Chart", chartPanel)
        mainTabbedPane.addTab("Simulation Chart", simChartPanel)
        mainTabbedPane.addTab("Help", createHelpPanel())
        
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configTabs, mainTabbedPane)
        splitPane.dividerLocation = 380
        add(splitPane, BorderLayout.CENTER)
    }

    // Helper to create grid layouts
    private fun createGridPanel(rows: Int, cols: Int): JPanel {
        val p = JPanel(GridLayout(rows, cols, 5, 5))
        p.border = EmptyBorder(10, 10, 10, 10)
        return p
    }

    private fun addFieldRow(p: JPanel, labelText: String, key: String) {
        p.add(JLabel(labelText))
        val tf = JTextField()
        tf.horizontalAlignment = JTextField.RIGHT
        textFields[key] = tf
        p.add(tf)
    }

    private fun createEconomicPanel(): JScrollPane {
        val p = JPanel(GridBagLayout())
        p.border = EmptyBorder(10, 10, 10, 10)
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        
        // Row 0: Start Date (Col 0,1)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.15
        p.add(JLabel("Start Date:"), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 0.35
        val dfStartDate = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["start_date"] = dfStartDate
        p.add(dfStartDate, gbc)

        // Row 0: Life Expectancy (Col 2,3)
        gbc.gridx = 2
        gbc.weightx = 0.15
        p.add(JLabel("Life Expectancy:"), gbc)
        
        gbc.gridx = 3
        gbc.weightx = 0.35
        val tfLifetime = JTextField()
        tfLifetime.horizontalAlignment = JTextField.RIGHT
        textFields["lifetime"] = tfLifetime
        p.add(tfLifetime, gbc)

        // Row 1: Investment Return (Col 0,1) & ROI Std Dev (Col 2,3)
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.15
        p.add(JLabel("Investment Return %:"), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 0.35
        val tfReturn = JTextField()
        tfReturn.horizontalAlignment = JTextField.RIGHT
        textFields["investment_return"] = tfReturn
        p.add(tfReturn, gbc)
        
        gbc.gridx = 2
        gbc.weightx = 0.15
        p.add(JLabel("ROI Std Dev %:"), gbc)
        
        gbc.gridx = 3
        gbc.weightx = 0.35
        val tfReturnDev = JTextField()
        tfReturnDev.horizontalAlignment = JTextField.RIGHT
        textFields["investment_std_dev"] = tfReturnDev
        p.add(tfReturnDev, gbc)

        // Row 2: Inflation Rate (Col 0,1) & Inflation Std Dev (Col 2,3)
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.15
        p.add(JLabel("Inflation Rate %:"), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 0.35
        val tfInflation = JTextField()
        tfInflation.horizontalAlignment = JTextField.RIGHT
        textFields["inflation"] = tfInflation
        p.add(tfInflation, gbc)
        
        gbc.gridx = 2
        gbc.weightx = 0.15
        p.add(JLabel("Inflation Std Dev %:"), gbc)
        
        gbc.gridx = 3
        gbc.weightx = 0.35
        val tfInflationDev = JTextField()
        tfInflationDev.horizontalAlignment = JTextField.RIGHT
        textFields["inflation_std_dev"] = tfInflationDev
        p.add(tfInflationDev, gbc)
        
        val container = JPanel(BorderLayout())
        container.add(p, BorderLayout.NORTH)
        return JScrollPane(container)
    }

    private fun createIncomePanel(): JScrollPane {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(10, 10, 10, 10)

        // General raise %
        val top = JPanel(FlowLayout(FlowLayout.LEFT))
        top.add(JLabel("Raise Percent %:"))
        val raiseF = JTextField(6)
        raiseF.horizontalAlignment = JTextField.RIGHT
        textFields["raise"] = raiseF
        top.add(raiseF)
        container.add(top)

        // Self Column
        val selfPanel = JPanel(GridLayout(9, 2, 5, 5))
        selfPanel.border = TitledBorder("Self")
        
        selfPanel.add(JLabel("Birthdate:"))
        val bdSelf = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["birthdate"] = bdSelf
        selfPanel.add(bdSelf)
        
        addFieldRow(selfPanel, "Starting Salary:", "salary")
        addFieldRow(selfPanel, "Retirement Age:", "retirement_age")
        addFieldRow(selfPanel, "SS Start Age:", "ss_age")
        addFieldRow(selfPanel, "SS PIA @ 67:", "ss_pia")
        addFieldRow(selfPanel, "Pension Start Age:", "pension_age")
        addFieldRow(selfPanel, "Monthly Pension @ 62:", "pension_early")
        addFieldRow(selfPanel, "Monthly Pension @ 70:", "pension_late")
        container.add(selfPanel)

        // Spouse Column
        val spousePanel = JPanel(GridLayout(9, 2, 5, 5))
        spousePanel.border = TitledBorder("Spouse")
        
        spousePanel.add(JLabel("Birthdate:"))
        val bdSpouse = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["birthdate_spouse"] = bdSpouse
        spousePanel.add(bdSpouse)
        
        addFieldRow(spousePanel, "Starting Salary:", "salary_spouse")
        addFieldRow(spousePanel, "Retirement Age:", "retirement_age_spouse")
        addFieldRow(spousePanel, "SS Start Age:", "ss_age_spouse")
        addFieldRow(spousePanel, "SS PIA @ 67:", "ss_pia_spouse")
        addFieldRow(spousePanel, "Pension Start Age:", "pension_age_spouse")
        addFieldRow(spousePanel, "Monthly Pension @ 62:", "pension_early_spouse")
        addFieldRow(spousePanel, "Monthly Pension @ 70:", "pension_late_spouse")
        container.add(spousePanel)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(container, BorderLayout.NORTH)
        return JScrollPane(wrapper)
    }

    private fun createExpensesPanel(): JScrollPane {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(10, 10, 10, 10)

        // Group 1: General Spending
        val generalPanel = JPanel(GridLayout(3, 2, 5, 5))
        generalPanel.border = TitledBorder("General Spending")
        addFieldRow(generalPanel, "First Year Spending:", "start_other_spending")
        addFieldRow(generalPanel, "Annual Travel:", "travel")
        generalPanel.add(JLabel("Travel End Date:"))
        val trEnd = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["travel_end"] = trEnd
        generalPanel.add(trEnd)
        container.add(generalPanel)
        container.add(Box.createVerticalStrut(10))

        // Group 2: Taxes
        val taxPanel = JPanel(GridLayout(5, 2, 5, 5))
        taxPanel.border = TitledBorder("Taxes")
        addFieldRow(taxPanel, "Property Taxes:", "prop_taxes")
        addFieldRow(taxPanel, "Payroll Tax Rate %:", "payroll_tax_rate")
        addFieldRow(taxPanel, "Fed Std Deduction:", "fed_std_deduction")
        addFieldRow(taxPanel, "State Std Deduction:", "state_std_deduction")
        addFieldRow(taxPanel, "State Tax Rate %:", "state_tax_rate")
        container.add(taxPanel)
        container.add(Box.createVerticalStrut(10))

        // Group 3: Housing & Future Care
        val houseCarePanel = JPanel(GridLayout(4, 2, 5, 5))
        houseCarePanel.border = TitledBorder("Housing & Future Care")
        addFieldRow(houseCarePanel, "Monthly Mortgage:", "mortgage")
        houseCarePanel.add(JLabel("Mortgage End Date:"))
        val mtgEnd = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["mortgage_end"] = mtgEnd
        houseCarePanel.add(mtgEnd)
        addFieldRow(houseCarePanel, "Monthly Eldercare:", "eldercare")
        houseCarePanel.add(JLabel("Eldercare Start Date:"))
        val ecStart = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["eldercare_start"] = ecStart
        houseCarePanel.add(ecStart)
        container.add(houseCarePanel)
        container.add(Box.createVerticalStrut(10))

        // Group 4: Donor Advised Fund
        val dafPanel = JPanel(GridLayout(2, 2, 5, 5))
        dafPanel.border = TitledBorder("Donor Advised Fund (DAF)")
        addFieldRow(dafPanel, "DAF Annual Dist:", "daf_distro")
        addFieldRow(dafPanel, "DAF Excess Pct %:", "daf_excess_pct")
        container.add(dafPanel)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(container, BorderLayout.NORTH)
        return JScrollPane(wrapper)
    }

    private fun createSavingsPanel(): JScrollPane {
        textFields["start_taxable_lots"] = JTextField()
        val p = createGridPanel(8, 2)
        addFieldRow(p, "IRA Savings:", "start_ira_savings")
        addFieldRow(p, "  IRA Cash Portion:", "start_ira_cash")
        addFieldRow(p, "Roth Savings:", "start_roth_savings")
        addFieldRow(p, "  Roth Cash Portion:", "start_roth_cash")
        addFieldRow(p, "Other Savings (Brokerage):", "start_other_savings")
        addFieldRow(p, "  Other Cash Portion:", "start_other_cash")
        addFieldRow(p, "Taxable Cost Basis:", "start_taxable_cost_basis")
        addFieldRow(p, "DAF Savings:", "start_daf_savings")
        
        val container = JPanel(BorderLayout())
        container.add(p, BorderLayout.NORTH)
        return JScrollPane(container)
    }

    private fun createSettingsPanel(): JScrollPane {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(10, 10, 10, 10)

        fun addCheck(labelText: String, key: String) {
            val cb = JCheckBox(labelText)
            cb.addActionListener { recalc() }
            checkboxes[key] = cb
            container.add(cb)
            container.add(Box.createVerticalStrut(5))
        }

        addCheck("Stack income / expense", "stack_incomes")
        addCheck("Stack savings", "stack_savings")
        addCheck("Show Distribution types", "show_distros")
        addCheck("Display Inflation & ROI", "show_inflation_roi")

        container.add(Box.createVerticalStrut(10))

        val p = JPanel(GridLayout(3, 2, 5, 5))
        p.add(JLabel("Guardrails %:"))
        val tf1 = JTextField()
        tf1.horizontalAlignment = JTextField.RIGHT
        textFields["guardrail_percent"] = tf1
        p.add(tf1)

        p.add(JLabel("Simulations count:"))
        val tf2 = JTextField()
        tf2.horizontalAlignment = JTextField.RIGHT
        textFields["num_simulations"] = tf2
        p.add(tf2)

        container.add(p)
        container.add(Box.createVerticalStrut(10))

        // Optimization Radio buttons
        val radioPanel = JPanel()
        radioPanel.layout = BoxLayout(radioPanel, BoxLayout.Y_AXIS)
        radioPanel.border = TitledBorder("Social Security / Pension Optimization")
        
        val bg = ButtonGroup()
        
        fun addRadio(text: String, id: String, selected: Boolean = false) {
            val rb = JRadioButton(text)
            rb.isSelected = selected
            rb.addActionListener { recalc() }
            radioButtons[id] = rb
            bg.add(rb)
            radioPanel.add(rb)
        }

        addRadio("None", "opt_none", true)
        addRadio("Social Security vs. Pension", "opt_ss_vs_pension")
        addRadio("Social Security Self vs. Spouse", "opt_ss_self_vs_spouse")
        addRadio("Pension Self vs. Spouse", "opt_pension_self_vs_spouse")

        container.add(radioPanel)
        
        val wrapper = JPanel(BorderLayout())
        wrapper.add(container, BorderLayout.NORTH)
        return JScrollPane(wrapper)
    }

    private fun createTablePanel(): JPanel {
        val p = JPanel(BorderLayout())
        resultTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        
        // Add popup menu for copying stock lots
        val popupMenu = JPopupMenu()
        val copyLotsItem = JMenuItem("Copy Stock Lots for selected year")
        copyLotsItem.addActionListener {
            copyStockLotsForSelectedRow()
        }
        popupMenu.add(copyLotsItem)
        resultTable.componentPopupMenu = popupMenu
        
        // Auto-select row on right click
        resultTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val row = resultTable.rowAtPoint(e.point)
                    if (row >= 0 && row < resultTable.rowCount) {
                        resultTable.setRowSelectionInterval(row, row)
                    }
                }
            }
        })
        
        p.add(JScrollPane(resultTable), BorderLayout.CENTER)
        return p
    }

    private fun createHelpPanel(): JScrollPane {
        val helpText = JEditorPane()
        helpText.contentType = "text/html"
        helpText.isEditable = false
        helpText.text = """
        <html>
        <body style="font-family: sans-serif; margin: 15px;">
        <h2 style="color: #1a527f;">Retirement Calculator Help Guide</h2>
        <p>This tool helps you model your retirement projections using custom tax, spending, and Monte Carlo market simulations.</p>
        <h3>Using the Calculator:</h3>
        <ol>
            <li><strong>Recalculate:</strong> Evaluates a single-path projection based on your inputs and populates the table and Net Worth chart.</li>
            <li><strong>Run Simulation:</strong> Evaluates multiple paths under volatile ROI & inflation rates using a Monte Carlo simulation. Draws the success chart.</li>
            <li><strong>Optimizations:</strong> Runs search loops to find the combination of starting ages that maximizes final net worth.</li>
        </ol>
        <h3>Distribution Order:</h3>
        <ul>
            <li><strong>IRA:</strong> Up to the top of the 0% tax bracket or RMD. Excess converts to Roth.</li>
            <li><strong>Other Savings:</strong> Withdrawals taken up to 0% capital gains bracket.</li>
            <li><strong>Roth:</strong> Fills any remaining expenses.</li>
        </ul>
        </body>
        </html>
        """.trimIndent()
        return JScrollPane(helpText)
    }


    private fun formatDollar(value: Double): String {
        val df = DecimalFormat("#,##0.##")
        return df.format(value)
    }

    private fun formatValueForField(key: String, value: String): String {
        if (key in dollarKeys) {
            val doubleVal = value.replace("$", "").replace(",", "").toDoubleOrNull()
            if (doubleVal != null) {
                return formatDollar(doubleVal)
            }
        }
        return value
    }

    private fun cleanValueFromField(key: String, text: String): String {
        if (key in dollarKeys) {
            return text.replace("$", "").replace(",", "").trim()
        }
        return text.trim()
    }

    // Dynamic JSON Form Data Loading
    private fun setFormData(data: Map<String, String>) {
        var startDateVal = data["start_date"]
        if ((startDateVal == null || startDateVal.isEmpty()) && data.containsKey("start_year")) {
            val y = data["start_year"]?.toIntOrNull() ?: 2026
            startDateVal = String.format("%02d/%02d/%04d", 1, 1, y)
        }
        
        val dataToUse = data.toMutableMap()
        if (startDateVal != null) {
            dataToUse["start_date"] = startDateVal
        }

        for ((k, v) in dataToUse) {
            val tf = textFields[k]
            if (tf != null) {
                tf.text = formatValueForField(k, v)
                continue
            }
            val cb = checkboxes[k]
            if (cb != null) {
                cb.isSelected = v.toBoolean()
                continue
            }
            val df = dateFields[k]
            if (df != null) {
                if (v.isEmpty()) {
                    df.dateInt = 0
                } else {
                    try {
                        if (v.contains("-")) {
                            val parts = v.split("-")
                            if (parts.size == 3) {
                                val y = parts[0].toInt()
                                val m = parts[1].toInt()
                                val d = parts[2].toInt()
                                df.dateInt = y * 10000 + m * 100 + d
                            }
                        } else if (v.contains("/")) {
                            val parts = v.split("/")
                            if (parts.size == 3) {
                                val m = parts[0].toInt()
                                val d = parts[1].toInt()
                                val y = parts[2].toInt()
                                df.dateInt = y * 10000 + m * 100 + d
                            }
                        }
                    } catch (e: Exception) {
                        df.dateInt = 0
                    }
                }
                continue
            }
        }
    }

    private fun getFormData(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for ((k, tf) in textFields) {
            map[k] = cleanValueFromField(k, tf.text)
        }
        for ((k, cb) in checkboxes) {
            map[k] = cb.isSelected.toString()
        }
        for ((k, rb) in radioButtons) {
            map[k] = rb.isSelected.toString()
        }
        for ((k, df) in dateFields) {
            val dateInt = df.dateInt
            if (dateInt > 0) {
                val y = dateInt / 10000
                val m = (dateInt % 10000) / 100
                val d = dateInt % 100
                map[k] = String.format("%02d/%02d/%04d", m, d, y)
            } else {
                map[k] = ""
            }
        }
        
        val dateInt = dateFields["start_date"]?.dateInt ?: 0
        if (dateInt > 0) {
            val y = dateInt / 10000
            val m = (dateInt % 10000) / 100
            val d = dateInt % 100
            map["start_year"] = y.toString()
            map["as_of_date"] = String.format("%04d-%02d-%02d", y, m, d)
        }
        return map
    }

    // MoneyDance Balance Loading
    private fun loadMoneyDanceBalances() {
        val bookContext = extension.getUnprotectedContext()
        bookContext?.showURL("moneydance:setprogress?label=Loading retirement account balances...")
        
        Thread {
            try {
                val dateInt = dateFields["start_date"]?.dateInt ?: 0
                if (dateInt > 0) {
                    val allAccounts = com.infinitekind.moneydance.model.AccountUtil.allMatchesForSearch(mdBook, AcctTypeFilter())
                    val dafAccounts = mutableListOf<com.infinitekind.moneydance.model.Account>()
                    val otherAccounts = mutableListOf<com.infinitekind.moneydance.model.Account>()
                    
                    if (allAccounts != null) {
                        for (acct in allAccounts) {
                            val nameLower = (acct.getAccountName() ?: "").lowercase()
                            if (nameLower.contains("daf") || nameLower.contains("donor advised") || nameLower.contains("charity")) {
                                dafAccounts.add(acct)
                            } else {
                                otherAccounts.add(acct)
                            }
                        }
                    }
                    
                    val divider = AccountDivider(mdBook, dateInt)
                    for (acct in otherAccounts) {
                        divider.add(acct)
                    }
                    
                    fun sumAccounts(grp: List<com.infinitekind.moneydance.model.Account>): Long {
                        var sum = 0L
                        for (acct in grp) {
                            sum += getRecursiveBalanceAsOfDate(mdBook, acct, dateInt)
                        }
                        return sum
                    }
                    
                    fun sumBasis(grp: List<com.infinitekind.moneydance.model.Account>): Long {
                        var sum = 0L
                        for (acct in grp) {
                            if (acct.getAccountType() == com.infinitekind.moneydance.model.Account.AccountType.INVESTMENT) {
                                var totalInvestmentCostBasis = 0L
                                val subAccounts = acct.getSubAccounts()
                                if (subAccounts != null) {
                                    for (subAcct in subAccounts) {
                                        if (subAcct.getAccountType() == com.infinitekind.moneydance.model.Account.AccountType.SECURITY) {
                                            totalInvestmentCostBasis += getHistoricalSecurityCostBasis(mdBook, subAcct, dateInt)
                                        }
                                    }
                                }
                                sum += totalInvestmentCostBasis + com.infinitekind.moneydance.model.AccountUtil.getBalanceAsOfDate(mdBook, acct, dateInt)
                            } else {
                                sum += com.infinitekind.moneydance.model.AccountUtil.getBalanceAsOfDate(mdBook, acct, dateInt)
                            }
                        }
                        return sum
                    }

                    fun sumCash(grp: List<com.infinitekind.moneydance.model.Account>): Long {
                        var sum = 0L
                        for (acct in grp) {
                            if (acct.getAccountType() == com.infinitekind.moneydance.model.Account.AccountType.INVESTMENT) {
                                sum += com.infinitekind.moneydance.model.AccountUtil.getBalanceAsOfDate(mdBook, acct, dateInt)
                            } else {
                                sum += getRecursiveBalanceAsOfDate(mdBook, acct, dateInt)
                            }
                        }
                        return sum
                    }

                    val taxableLots = mutableListOf<String>()
                    for (acct in divider.taxables) {
                        if (acct.getAccountType() == com.infinitekind.moneydance.model.Account.AccountType.INVESTMENT) {
                            val subAccounts = acct.getSubAccounts()
                            if (subAccounts != null) {
                                for (subAcct in subAccounts) {
                                    if (subAcct.getAccountType() == com.infinitekind.moneydance.model.Account.AccountType.SECURITY) {
                                        val lotsTable = com.infinitekind.moneydance.model.InvestUtil.getRemainingLots(mdBook, subAcct, dateInt)
                                        if (lotsTable != null && !lotsTable.isEmpty()) {
                                            for (key in lotsTable.keys) {
                                                val txnId = key as? String ?: continue
                                                val tracker = lotsTable[txnId] ?: continue
                                                val remainingShares = try {
                                                    val field = tracker.javaClass.getDeclaredField("availableShares")
                                                    field.isAccessible = true
                                                    field.getLong(tracker)
                                                } catch (e: Exception) {
                                                    0L
                                                }
                                                if (remainingShares > 0L) {
                                                    val buySplit = try {
                                                        val txnSet = mdBook.javaClass.getMethod("getTransactionSet").invoke(mdBook)
                                                        val rawTxn = txnSet.javaClass.getMethod("getTxnByID", String::class.java).invoke(txnSet, txnId)
                                                        if (rawTxn != null) {
                                                            if (rawTxn.javaClass.name.contains("SplitTxn")) {
                                                                rawTxn
                                                            } else if (rawTxn.javaClass.name.contains("ParentTxn")) {
                                                                var foundSplit: Any? = null
                                                                val getSplitM = rawTxn.javaClass.getMethod("getSplit", Int::class.java)
                                                                val getSplitCountM = rawTxn.javaClass.getMethod("getSplitCount")
                                                                val splitCount = getSplitCountM.invoke(rawTxn) as Int
                                                                for (idx in 0 until splitCount) {
                                                                    val split = getSplitM.invoke(rawTxn, idx) ?: continue
                                                                    val acct = split.javaClass.getMethod("getAccount").invoke(split)
                                                                    if (acct == subAcct) {
                                                                        foundSplit = split
                                                                        break
                                                                    }
                                                                }
                                                                foundSplit
                                                            } else {
                                                                rawTxn
                                                            }
                                                        } else {
                                                            null
                                                        }
                                                    } catch (e: Exception) {
                                                        null
                                                    }
                                                    if (buySplit != null) {
                                                        val originalShares = try {
                                                            buySplit.javaClass.getMethod("getValue").invoke(buySplit) as Long
                                                        } catch (e: Exception) {
                                                            0L
                                                        }
                                                        var originalCostBasis = try {
                                                            val amt = buySplit.javaClass.getMethod("getAmount").invoke(buySplit) as Long
                                                            Math.abs(amt)
                                                        } catch (e: Exception) {
                                                            0L
                                                        }
                                                        if (originalCostBasis == 0L) {
                                                            originalCostBasis = try {
                                                                val method = Class.forName("com.infinitekind.moneydance.model.InvestUtil")
                                                                    .getDeclaredMethod("getCostBasis", com.infinitekind.moneydance.model.Account::class.java, com.infinitekind.moneydance.model.SplitTxn::class.java)
                                                                method.isAccessible = true
                                                                method.invoke(null, subAcct, buySplit) as Long
                                                            } catch (e: Exception) {
                                                                0L
                                                            }
                                                        }
                                                        
                                                        // Calculate remaining cost basis
                                                        val remainingCostBasis = if (originalShares > 0L) {
                                                            originalCostBasis.toDouble() * (remainingShares.toDouble() / originalShares.toDouble())
                                                        } else {
                                                            originalCostBasis.toDouble()
                                                        }
                                                        
                                                        // Get current value of remaining shares
                                                        val totalShares = com.infinitekind.moneydance.model.AccountUtil.getBalanceAsOfDate(mdBook, subAcct, dateInt)
                                                        val secVal = getRecursiveBalanceAsOfDate(mdBook, subAcct, dateInt)
                                                        
                                                        val currentVal = if (totalShares > 0L) {
                                                            secVal.toDouble() * (remainingShares.toDouble() / totalShares.toDouble())
                                                        } else {
                                                            0.0
                                                        }
                                                        
                                                        // Format name: Name (Bought YYYY-MM-DD)
                                                        val rawName = subAcct.getAccountName() ?: "Stock"
                                                        val buyDate = try {
                                                            buySplit.javaClass.getMethod("getDateInt").invoke(buySplit) as Int
                                                        } catch (e: Exception) {
                                                            0
                                                        }
                                                        val yr = buyDate / 10000
                                                        val mo = (buyDate % 10000) / 100
                                                        val dy = buyDate % 100
                                                        val dateStr = String.format("%04d-%02d-%02d", yr, mo, dy)
                                                        val lotName = "$rawName (Bought $dateStr)"
                                                        
                                                        val encName = java.net.URLEncoder.encode(lotName, "UTF-8")
                                                        taxableLots.add("$encName,${remainingCostBasis / 100.0},${currentVal / 100.0}")
                                                    }
                                                }
                                            }
                                        } else {
                                            val secVal = getRecursiveBalanceAsOfDate(mdBook, subAcct, dateInt)
                                            val secBasis = getHistoricalSecurityCostBasis(mdBook, subAcct, dateInt)
                                            if (secVal > 0L) {
                                                val rawName = subAcct.getAccountName() ?: "Stock"
                                                val encName = java.net.URLEncoder.encode(rawName, "UTF-8")
                                                taxableLots.add("$encName,${secBasis / 100.0},${secVal / 100.0}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val lotsStr = taxableLots.joinToString(";")

                    val iraBal = sumAccounts(divider.iras)
                    val iraCash = sumCash(divider.iras)
                    val rothBal = sumAccounts(divider.roths)
                    val rothCash = sumCash(divider.roths)
                    val otherBal = sumAccounts(divider.taxables)
                    val otherCash = sumCash(divider.taxables)
                    val otherBasis = sumBasis(divider.taxables)
                    val dafBal = sumAccounts(dafAccounts)

                    SwingUtilities.invokeLater {
                        textFields["start_taxable_lots"]?.text = lotsStr
                        textFields["start_ira_savings"]?.text = formatDollar(iraBal / 100.0)
                        textFields["start_ira_cash"]?.text = formatDollar(iraCash / 100.0)
                        textFields["start_roth_savings"]?.text = formatDollar(rothBal / 100.0)
                        textFields["start_roth_cash"]?.text = formatDollar(rothCash / 100.0)
                        textFields["start_other_savings"]?.text = formatDollar(otherBal / 100.0)
                        textFields["start_other_cash"]?.text = formatDollar(otherCash / 100.0)
                        textFields["start_taxable_cost_basis"]?.text = formatDollar(otherBasis / 100.0)
                        textFields["start_daf_savings"]?.text = formatDollar(dafBal / 100.0)
                        
                        recalc()
                        bookContext?.showURL("moneydance:setprogress?label=")
                    }
                } else {
                    SwingUtilities.invokeLater {
                        bookContext?.showURL("moneydance:setprogress?label=")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    bookContext?.showURL("moneydance:setprogress?label=")
                }
            }
        }.start()
    }

    private fun loadConfigurationFromFile(file: File) {
        try {
            val content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
            val map = mutableMapOf<String, String>()
            
            val regex = Regex("""\[\s*"(.*?)"\s*,\s*"(.*?)"\s*\]""")
            val matches = regex.findAll(content)
            for (m in matches) {
                val key = m.groupValues[1].replace("\\\"", "\"")
                val valStr = m.groupValues[2].replace("\\\"", "\"")
                map[key] = valStr
            }
            
            setFormData(map)
            recalc()
            JOptionPane.showMessageDialog(this, "Configuration loaded successfully.", "Success", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Error loading config: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun openConfigFile() {
        val fc = JFileChooser()
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadConfigurationFromFile(fc.selectedFile)
        }
    }

    // Main recalculate action
    private fun recalc() {
        val form = getFormData()
        
        // Optimizations run before standard recalculate
        val optimizedForm = runOptimization(form)
        
        currentResults.clear()
        
        var curYear: YearRow? = null
        val lifetime = optimizedForm.getInt("lifetime", 100)
        
        while (true) {
            if (curYear != null && (curYear.ageSelf > lifetime || curYear.ageSpouse > lifetime)) {
                break
            }
            val nextRow = YearRow(optimizedForm, curYear)
            currentResults.add(nextRow.toMap())
            curYear = nextRow
        }
        
        updateTable()
        chartPanel.updateData(currentResults, getFormData())
        mainTabbedPane.setSelectedIndex(0)
    }

    // Run Social Security / Pension starting age optimization
    private fun runOptimization(originalForm: Map<String, String>): Map<String, String> {
        if (radioButtons["opt_none"]?.isSelected == true) return originalForm
        
        val firstRow = YearRow(originalForm, null)
        val startAgeVal = min(firstRow.ageSelf, firstRow.ageSpouse)
        if (startAgeVal < 60) return originalForm
        
        val mutableForm = originalForm.toMutableMap()
        var bestAge1 = originalForm.getDouble("ss_age")
        var bestAge2 = originalForm.getDouble("pension_age")
        var maxFinalSavings = -1.0
        
        for (a1 in startAgeVal..75) {
            for (a2 in startAgeVal..75) {
                val tempForm = originalForm.toMutableMap()
                if (radioButtons["opt_ss_vs_pension"]?.isSelected == true) {
                    tempForm["ss_age"] = a1.toString()
                    tempForm["ss_age_spouse"] = a1.toString()
                    tempForm["pension_age"] = a2.toString()
                    tempForm["pension_age_spouse"] = a2.toString()
                } else if (radioButtons["opt_ss_self_vs_spouse"]?.isSelected == true) {
                    tempForm["ss_age"] = a1.toString()
                    tempForm["ss_age_spouse"] = a2.toString()
                } else if (radioButtons["opt_pension_self_vs_spouse"]?.isSelected == true) {
                    tempForm["pension_age"] = a1.toString()
                    tempForm["pension_age_spouse"] = a2.toString()
                }
                
                // Run projection
                var cur: YearRow? = null
                val lifetime = tempForm.getInt("lifetime", 100)
                while (true) {
                    if (cur != null && (cur.ageSelf > lifetime || cur.ageSpouse > lifetime)) {
                        break
                    }
                    cur = YearRow(tempForm, cur)
                }
                val finalSavings = cur?.getTotalSavings() ?: 0.0
                if (finalSavings > maxFinalSavings) {
                    maxFinalSavings = finalSavings
                    bestAge1 = a1.toDouble()
                    bestAge2 = a2.toDouble()
                }
            }
        }
        
        // Apply optimal values
        if (radioButtons["opt_ss_vs_pension"]?.isSelected == true) {
            mutableForm["ss_age"] = bestAge1.toString()
            mutableForm["ss_age_spouse"] = bestAge1.toString()
            mutableForm["pension_age"] = bestAge2.toString()
            mutableForm["pension_age_spouse"] = bestAge2.toString()
        } else if (radioButtons["opt_ss_self_vs_spouse"]?.isSelected == true) {
            mutableForm["ss_age"] = bestAge1.toString()
            mutableForm["ss_age_spouse"] = bestAge2.toString()
        } else if (radioButtons["opt_pension_self_vs_spouse"]?.isSelected == true) {
            mutableForm["pension_age"] = bestAge1.toString()
            mutableForm["pension_age_spouse"] = bestAge2.toString()
        }
        return mutableForm
    }

    // Monte Carlo simulation runner
    private fun runSimulation() {
        val form = getFormData()
        val engine = MonteCarlo(form)
        
        // Start background worker to keep UI responsive
        val progress = JProgressBar(0, 100)
        progress.isIndeterminate = true
        val dialog = JDialog(this, "Simulating...", true)
        dialog.add(BorderLayout.NORTH, JLabel("Running Monte Carlo simulations..."))
        dialog.add(BorderLayout.CENTER, progress)
        dialog.setSize(300, 100)
        dialog.setLocationRelativeTo(this)
        
        val worker = object : SwingWorker<List<List<YearRow>>, Void>() {
            override fun doInBackground(): List<List<YearRow>> {
                return engine.simulate()
            }
            override fun done() {
                dialog.dispose()
                try {
                    val runs = get()
                    simChartPanel.updateRuns(runs)
                    mainTabbedPane.setSelectedIndex(2) // open sim chart
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        worker.execute()
        dialog.setVisible(true)
    }

    // JTable Drawing & Formatting
    private fun updateTable() {
        val cols = mutableListOf(
            "Year", "Age", "Spouse Age", "Savings", 
            "Salary", "SS/Pension", "ROI", "Distrib", 
            "Taxes", "Housing", "Travel & Eldercare", "Other Spending"
        )
        
        val showDist = checkboxes["show_distros"]?.isSelected == true
        if (showDist) {
            cols.add(4, "Taxable Basis")
            cols.add(6, "Dividends")
            cols.add(10, "IRA Distro")
            cols.add(11, "Roth Distro")
            cols.add(12, "Other Distro")
        }
        
        val showInfl = checkboxes["show_inflation_roi"]?.isSelected == true
        if (showInfl) {
            cols.add("Inflation %")
            cols.add("ROI %")
        }

        val colNames = cols.toTypedArray()
        tableModel.setDataVector(emptyArray<Array<Any>>(), colNames)
        
        val df = DecimalFormat("$#,##0.00")
        val pctDf = DecimalFormat("0.00%")
        
        for (rowData in currentResults) {
            val v = Vector<Any>()
            v.add(rowData["year"].toString())
            v.add(rowData["age"].toString())
            v.add(rowData["age_spouse"].toString())
            v.add(df.format(rowData["savings"]))
            
            if (showDist) {
                v.add(df.format(rowData["cost_basis"]))
            }
            v.add(df.format(rowData["salary"]))
            if (showDist) {
                v.add(df.format(rowData["dividends"]))
            }
            v.add(df.format(rowData["ss"]))
            v.add(df.format(rowData["roi"]))
            v.add(df.format(rowData["distro"]))
            if (showDist) {
                v.add(df.format(rowData["ira_distro"]))
                v.add(df.format(rowData["roth_distro"]))
                v.add(df.format(rowData["other_distro"]))
            }
            v.add(df.format(rowData["taxes"]))
            v.add(df.format(rowData["housing"]))
            v.add(df.format(rowData["travel_eldercare"]))
            v.add(df.format(rowData["other"]))
            
            if (showInfl) {
                v.add(pctDf.format(rowData["inflation_pct"]))
                v.add(pctDf.format(rowData["roi_pct"]))
            }
            tableModel.addRow(v)
        }
        
        // Custom Table row rendering
        val renderer = CustomRowRenderer(currentResults)
        for (i in 0 until resultTable.columnCount) {
            resultTable.columnModel.getColumn(i).cellRenderer = renderer
        }
        
        resultTable.revalidate()
    }

    // Save configuration to JSON
    private fun saveConfigFile() {
        val fc = JFileChooser()
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fc.selectedFile
                val data = getFormData()
                
                // Write serialized JSON
                val sb = java.lang.StringBuilder()
                sb.append("[")
                var first = true
                for ((k, v) in data) {
                    if (!first) sb.append(",\n")
                    first = false
                    sb.append("[\"").append(k.replace("\"", "\\\"")).append("\",\"").append(v.replace("\"", "\\\"")).append("\"]")
                }
                sb.append("]")
                
                Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8)
                JOptionPane.showMessageDialog(this, "Configuration saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "Error saving config: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }


    // Print helper
    private fun printReport() {
        try {
            val startDateStr = dateFields["start_date"]?.let { df ->
                val dateInt = df.dateInt
                if (dateInt > 0) {
                    val y = dateInt / 10000
                    val m = (dateInt % 10000) / 100
                    val d = dateInt % 100
                    String.format("%02d/%02d/%04d", m, d, y)
                } else null
            } ?: "Unknown"
            
            val runTimeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, YYYY hh:mm a"))
            
            val headerFormat = java.text.MessageFormat("Retirement Calculator Projections from $startDateStr  |  Report Generated: $runTimeStr")
            val footerFormat = java.text.MessageFormat("- Page {0} -")
            
            resultTable.print(
                JTable.PrintMode.FIT_WIDTH,
                headerFormat,
                footerFormat,
                true,
                null,
                true
            )
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Error printing: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun exportInstructions() {
        if (currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No calculated projections found. Please recalculate first.", "Error", JOptionPane.ERROR_MESSAGE)
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
            hsb.append("<tr style=\"")
            if (isTotal) {
                hsb.append("font-weight: bold; background-color: #f2f2f2;")
            }
            hsb.append("\">")
            for (i in cells.indices) {
                val cell = cells[i]
                val align = if (i == 0) "left" else "right"
                val padding = "padding: 6px 8px;"
                val border = "border: 1px solid #cccccc;"
                val font = if (isHeader) "font-weight: bold; background-color: #e6e6e6;" else ""
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
            val iraDist = getD("ira_distro") - rothConv
            val rothDist = getD("roth_distro") + rothConv
            val realizedGain = getD("realized_gain")
            val totSelf = salSelf + ssSelf + penSelf
            val totSp = salSp + ssSp + penSp
            val taxableDist = getD("other_distro")
            val taxableDiv = getD("taxable_dividends")
            val taxableInt = getD("taxable_interest")
            val jointTot = iraDist + rothDist + (taxableDiv + taxableInt) + taxableDist
            val grandTot = totSelf + totSp + jointTot
            
            data class ColSpec(
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
                ColSpec("Salary", salSelf, salSp, 0.0, salSelf + salSp),
                ColSpec("Social Security", ssSelf, ssSp, 0.0, ssSelf + ssSp),
                ColSpec("Pension", penSelf, penSp, 0.0, penSelf + penSp),
                ColSpec("IRA", 0.0, 0.0, iraDist, iraDist),
                ColSpec("Roth", 0.0, 0.0, rothDist, rothDist),
                ColSpec("Taxable Div/Int", 0.0, 0.0, taxableDiv + taxableInt, taxableDiv + taxableInt),
                ColSpec("Taxable (Gain)", 0.0, 0.0, taxableDist, taxableDist, isTaxableGain = true, jointGain = realizedGain, totalGain = realizedGain)
            )
            
            val activeCols = allCols.filter { Math.abs(it.totalVal) > 0.001 || Math.abs(it.totalGain) > 0.001 }
            
            val formatCell = { col: ColSpec, rowVal: Double, rowGain: Double ->
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
            
            val taxableIntVal = getD("taxable_interest")
            val taxableDivVal = getD("taxable_dividends")

            val ordIncDerivationMd = buildString {
                append("    * Gross Ordinary Income components:\n")
                append("      * Salary: **").append(fmt(salSelf + salSp)).append("**\n")
                append("      * Pension: **").append(fmt(penSelf + penSp)).append("**\n")
                append("      * Interest: **").append(fmt(taxableIntVal)).append("**\n")
                append("      * IRA Distribution: **").append(fmt(iraDist)).append("**\n")
                if (rothConv > 0.01) {
                    append("      * Roth Conversion: **").append(fmt(rothConv)).append("**\n")
                }
                append("      * Taxable Social Security: **").append(fmt(taxSS)).append("** (Gross SS: **").append(fmt(ssSelf + ssSp)).append("**)\n")
                append("    * Deductions:\n")
                append("      * Standard Deduction: **-").append(fmt(stdDed)).append("**\n")
                if (dafContrib > 0.0) {
                    append("      * DAF Contribution: **-").append(fmt(dafContrib)).append("**\n")
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
                append("<li>IRA Distribution: <strong>").append(fmt(iraDist)).append("</strong></li>")
                if (rothConv > 0.01) {
                    append("<li>Roth Conversion: <strong>").append(fmt(rothConv)).append("</strong></li>")
                }
                append("<li>Taxable Social Security: <strong>").append(fmt(taxSS)).append("</strong> (Gross SS: <strong>").append(fmt(ssSelf + ssSp)).append("</strong>)</li>")
                append("</ul></li>")
                append("<li>Deductions:")
                append("<ul style=\"padding-left: 20px;\">")
                append("<li>Standard Deduction: <strong>-").append(fmt(stdDed)).append("</strong></li>")
                if (dafContrib > 0.0) {
                    append("<li>DAF Contribution: <strong>-").append(fmt(dafContrib)).append("</strong></li>")
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
                            val purpose = "Donate directly to " + match.groupValues[4].trim()
                            row = ActionRow("Donate", asset, amount, "N/A", purpose)
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
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            JOptionPane.showMessageDialog(this, "Annual Financial Instruction Sheet exported successfully and copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to copy to clipboard: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun copyStockLotsForSelectedRow() {
        val selectedRow = resultTable.selectedRow
        if (selectedRow < 0 || selectedRow >= currentResults.size) {
            JOptionPane.showMessageDialog(this, "Please select a year in the table first.", "Info", JOptionPane.WARNING_MESSAGE)
            return
        }
        
        val r = currentResults[selectedRow]
        val yr = r["year"]?.toString() ?: "Unknown"
        
        @Suppress("UNCHECKED_CAST")
        val lots = r["stock_lots"] as? List<StockLot> ?: emptyList()
        if (lots.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No taxable stock lots found for Year $yr.", "Info", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        
        val sortedLots = lots.sortedByDescending { it.basisRatio }
        
        val sb = java.lang.StringBuilder()
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
            val selection = java.awt.datatransfer.StringSelection(sb.toString())
            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            JOptionPane.showMessageDialog(this, "Stock lots for Year $yr successfully copied to clipboard in spreadsheet format!", "Success", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Failed to copy to clipboard: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
}

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
        
        val isDistro = colName.lowercase().contains("distro") || colName.lowercase().contains("distrib")
        
        val cleanStr = value?.toString()?.replace("$", "")?.replace(",", "")?.replace("%", "")?.trim() ?: ""
        val doubleVal = cleanStr.toDoubleOrNull() ?: 0.0
        
        // Negative savings / ordinary values drop below zero -> RED
        // Positive distributions -> RED (since they drain assets)
        if ((!isDistro && doubleVal < 0.0) || (isDistro && doubleVal > 0.0)) {
            cell.foreground = Color.RED
        }
        
        // Highlight Year column red if a guardrail message is triggered
        if (colName == "Year") {
            val msg = rowData["guardrail_msg"] as? String ?: ""
            if (msg.isNotEmpty()) {
                cell.foreground = Color.RED
            }
        }
        
        // Compare with previous row for orange drop alerts
        if (row > 0 && row - 1 < tableData.size) {
            val prevRow = tableData[row - 1]
            val key = colName.lowercase().replace(" ", "_").replace("%", "pct").replace("&", "").replace("__", "_")
            val rawPrev = prevRow[key]
            val prevVal = when (rawPrev) {
                is Number -> rawPrev.toDouble()
                else -> 0.0
            }
            if (!isDistro && doubleVal < prevVal && doubleVal != 0.0) {
                if (!isSelected) {
                    cell.foreground = Color.ORANGE
                }
            } else if (isDistro && doubleVal > prevVal && doubleVal != 0.0) {
                if (!isSelected) {
                    cell.foreground = Color.ORANGE
                }
            }
        }
        
        // Tooltip formatting
        val isSavingsCol = colName in setOf(
            "Savings", "Taxable Basis", "ROI", "Distrib", "IRA Distro", "Roth Distro", "Other Distro"
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
                val iraBal = fmt(rowData["ira_savings"])
                val iraCashVal = fmt(rowData["ira_cash"])
                val iraRoi = fmt(rowData["ira_roi"])
                val iraDist = fmt(rowData["ira_distro"])
                val iraNetVal = ((rowData["ira_roi"] as? Number)?.toDouble() ?: 0.0) - ((rowData["ira_distro"] as? Number)?.toDouble() ?: 0.0)
                val iraNet = DecimalFormat("$#,##0.00").format(iraNetVal)
                
                val rothBal = fmt(rowData["roth_savings"])
                val rothCashVal = fmt(rowData["roth_cash"])
                val rothRoi = fmt(rowData["roth_roi"])
                val rothDist = fmt(rowData["roth_distro"])
                val rothNetVal = ((rowData["roth_roi"] as? Number)?.toDouble() ?: 0.0) - ((rowData["roth_distro"] as? Number)?.toDouble() ?: 0.0)
                val rothNet = DecimalFormat("$#,##0.00").format(rothNetVal)
                
                val otherBal = fmt(rowData["other_savings"])
                val otherCashVal = fmt(rowData["other_cash"])
                val otherRoi = fmt(rowData["other_roi"])
                val otherDist = fmt(rowData["other_distro"])
                val otherNetVal = ((rowData["other_roi"] as? Number)?.toDouble() ?: 0.0) - ((rowData["other_distro"] as? Number)?.toDouble() ?: 0.0)
                val otherNet = DecimalFormat("$#,##0.00").format(otherNetVal)
                
                val dafBal = fmt(rowData["daf_savings"])
                val dafCashVal = "$0.00"
                val dafRoi = fmt(rowData["daf_roi"])
                val dafDist = fmt(rowData["daf_distro"])
                val dafContrib = (rowData["daf_contrib"] as? Number)?.toDouble() ?: 0.0
                val dafNetVal = ((rowData["daf_roi"] as? Number)?.toDouble() ?: 0.0) - ((rowData["daf_distro"] as? Number)?.toDouble() ?: 0.0) + dafContrib
                val dafNet = DecimalFormat("$#,##0.00").format(dafNetVal)
                
                val totBal = fmt(rowData["savings"])
                val totCashVal = fmt(
                    ((rowData["ira_cash"] as? Number)?.toDouble() ?: 0.0) +
                    ((rowData["roth_cash"] as? Number)?.toDouble() ?: 0.0) +
                    ((rowData["other_cash"] as? Number)?.toDouble() ?: 0.0)
                )
                val totRoi = fmt(rowData["roi"])
                val totDist = fmt(rowData["distro"])
                val totNetVal = ((rowData["roi"] as? Number)?.toDouble() ?: 0.0) - ((rowData["distro"] as? Number)?.toDouble() ?: 0.0)
                val totNet = DecimalFormat("$#,##0.00").format(totNetVal)
                
                cell.toolTipText = """<html>
                    <table border='0' cellpadding='2' cellspacing='3'>
                    <tr><th></th><th align='right'>Total Balance</th><th align='right'>Cash Balance</th><th align='right'>ROI</th><th align='right'>Distribution</th><th align='right'>Net</th></tr>
                    <tr><td><b>IRA:</b></td><td align='right'>$iraBal</td><td align='right'>$iraCashVal</td><td align='right'>$iraRoi</td><td align='right'>$iraDist</td><td align='right'>$iraNet</td></tr>
                    <tr><td><b>Roth:</b></td><td align='right'>$rothBal</td><td align='right'>$rothCashVal</td><td align='right'>$rothRoi</td><td align='right'>$rothDist</td><td align='right'>$rothNet</td></tr>
                    <tr><td><b>Other:</b></td><td align='right'>$otherBal</td><td align='right'>$otherCashVal</td><td align='right'>$otherRoi</td><td align='right'>$otherDist</td><td align='right'>$otherNet</td></tr>
                    <tr><td><b>DAF:</b></td><td align='right'>$dafBal</td><td align='right'>$dafCashVal</td><td align='right'>$dafRoi</td><td align='right'>$dafDist</td><td align='right'>$dafNet</td></tr>
                    <tr style='border-top: 1px solid black;'><td style='border-top: 1px solid black;'><b>Total:</b></td><td style='border-top: 1px solid black;' align='right'><b>$totBal</b></td><td style='border-top: 1px solid black;' align='right'><b>$totCashVal</b></td><td style='border-top: 1px solid black;' align='right'><b>$totRoi</b></td><td style='border-top: 1px solid black;' align='right'><b>$totDist</b></td><td style='border-top: 1px solid black;' align='right'><b>$totNet</b></td></tr>
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

        // Alignment formatting
        if (colName == "Year" || colName.contains("Age")) {
            cell.horizontalAlignment = SwingConstants.CENTER
        } else {
            cell.horizontalAlignment = SwingConstants.RIGHT
        }
        
        return cell
    }
}

// 1. Savings Line/Area Graph Component
class SavingsChartPanel : JPanel() {
    private var data: List<Map<String, Any>> = emptyList()
    private var config: Map<String, String> = emptyMap()

    fun updateData(newData: List<Map<String, Any>>, newConfig: Map<String, String>) {
        data = newData
        config = newConfig
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

    fun updateRuns(newRuns: List<List<YearRow>>) {
        runs = newRuns
        repaint()
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
        val minVal = runs.minOfOrNull { it.last().getTotalSavings() } ?: 0.0
        
        // Normalize heights
        val range = if (maxVal - minVal > 0) maxVal - minVal else 1.0
        
        val barCount = runs.size
        val barW = max(1.0, graphW.toDouble() / barCount)
        
        // Draw grid
        g2d.color = Color.LIGHT_GRAY
        for (i in 0..5) {
            val y = padding + graphH - (i * graphH / 5)
            g2d.drawLine(padding, y, padding + graphW, y)
            val labelVal = minVal + (i * range / 5)
            g2d.color = Color.DARK_GRAY
            g2d.drawString(String.format("$%,.0f", labelVal), 10, y + 5)
            g2d.color = Color.LIGHT_GRAY
        }
        
        // Draw bars
        for (i in 0 until barCount) {
            val run = runs[i]
            val lastSavings = run.last().getTotalSavings()
            val hasGuardrail = run.any { it.percentBelowGuardrail > 0.0 }
            
            // Choose color: Red for fail (<=0), Orange/Teal for guardrail hits, Green for clean success
            g2d.color = when {
                lastSavings <= 0.0 -> Color.RED
                hasGuardrail -> Color.ORANGE
                else -> Color(75, 200, 75)
            }
            
            val barH = ((lastSavings - minVal) * graphH / range).toInt()
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
            val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
            if (files.isNotEmpty()) {
                val file = files[0] as File
                onDrop(file)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}

data class ActionRow(
    val type: String,
    val asset: String,
    val amount: String,
    val realizedGain: String,
    val purpose: String
)

class HtmlSelection(private val html: String, private val plainText: String) : java.awt.datatransfer.Transferable {
    companion object {
        val HTML_FLAVOR = java.awt.datatransfer.DataFlavor("text/html;class=java.lang.String")
    }

    private val flavors = arrayOf(HTML_FLAVOR, java.awt.datatransfer.DataFlavor.stringFlavor)

    override fun getTransferDataFlavors(): Array<java.awt.datatransfer.DataFlavor> = flavors

    override fun isDataFlavorSupported(flavor: java.awt.datatransfer.DataFlavor): Boolean =
        flavors.any { it.equals(flavor) }

    override fun getTransferData(flavor: java.awt.datatransfer.DataFlavor): Any {
        return when {
            flavor.equals(HTML_FLAVOR) -> html
            flavor.equals(java.awt.datatransfer.DataFlavor.stringFlavor) -> plainText
            else -> throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
        }
    }
}
