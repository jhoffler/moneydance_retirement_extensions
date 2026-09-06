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
import java.awt.event.*

class CalculatorWindow(private val extension: Main, private val mdBook: com.infinitekind.moneydance.model.AccountBook) : JFrame("Retirement Calculator") {
    private var lastSimRuns: List<List<YearRow>>? = null
    private var activeSimIndex: Int? = null
    
    // Map of text fields and other inputs
    private val dollarKeys = setOf(
        "salary", "salary_spouse", "ss_pia", "ss_pia_spouse", "pension_early", "pension_late", 
        "pension_early_spouse", "pension_late_spouse", "start_other_spending", "prop_taxes", 
        "fed_std_deduction", "state_std_deduction", "mortgage", "travel", "eldercare", "daf_distro",
        "start_ira_savings", "start_ira_cash", "start_roth_savings", "start_roth_cash", "start_other_savings", "start_other_cash", "start_taxable_cost_basis", "start_daf_savings"
    )
    private val textFields = mutableMapOf<String, JTextField>()
    private val checkboxes = mutableMapOf<String, JCheckBox>()
    private val lockCheckboxes = mutableMapOf<String, JCheckBox>()
    private val dateFields = mutableMapOf<String, com.moneydance.awt.JDateField>()
    private val comboBoxes = mutableMapOf<String, JComboBox<String>>()
    private val pensionsConfig = mutableMapOf<String, String>()
    private val eventsConfig = mutableMapOf<String, String>()
    private val incomeEventsModel = object : DefaultTableModel(arrayOf("Date", "Name", "Type", "Amount"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val incomeEventsTable = JTable(incomeEventsModel)
    private val expenseEventsModel = object : DefaultTableModel(arrayOf("Date", "Name", "Type", "Amount"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val expenseEventsTable = JTable(expenseEventsModel)
    
    // UI elements
    private val tableModel = DefaultTableModel()
    private val resultTable = JTable(tableModel)
    private val mainTabbedPane = JTabbedPane()
    private val managePensionsButtonSelf = JButton("Manage (0)")
    private val managePensionsButtonSpouse = JButton("Manage (0)")
    
    private val chartPanel = SavingsChartPanel()
    private val simChartPanel = SimulationChartPanel()
    
    // Data list representing current recalc results
    private val currentResults = mutableListOf<Map<String, Any>>()
    
    // Default form configuration
    private val defaultData = mapOf(
        "start_year" to "2026",
        "investment_return" to "6",
        "inflation" to "2.25",
        "interest_rate" to "3",
        "dividend_rate" to "0.5",
        "lifetime" to "100",
        "lifetime_spouse" to "100",
        "lifetime_locked" to "false",
        "lifetime_spouse_locked" to "false",
        "show_savings_types" to "false",
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
        "eldercare_light_years" to "2",
        "eldercare_light_cost" to "40000",
        "eldercare_acuity_years" to "1",
        "eldercare_acuity_cost" to "180000",
        "eldercare_facility_years" to "2",
        "eldercare_facility_cost" to "120000",
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
        "guardrail_percent" to "100",
        "num_simulations" to "100",
        "roth_enabled" to "false",
        "roth_start_age" to "60",
        "roth_end_age" to "75",
        "roth_max_ordinary_bracket" to "12%",
        "roth_max_cg_rate" to "0%",
        "roth_tax_impact_cap" to "",
        "roth_max_realized_loss" to ""
    )

    init {
        setupUI()

        // Initial populate from default data
        setFormData(defaultData)
        
        // Try to restore configuration from Moneydance localStorage
        restoreConfigFromLocalStorage()
        updateDefaultRothEndAge()

        // Set Start Date to the current date on load
        val today = LocalDate.now()
        dateFields["start_date"]?.dateInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        
        // Read account balances from MoneyDance if possible
        loadMoneyDanceBalances()
        
        // Perform initial calculation
        recalc()
        
        defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        
        // Save config and close console when window is closed
        this.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                extension.closeConsole()
            }
        })
        
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
        exportBtn.addActionListener { exportPlan() }
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
        simChartPanel.onRunSelected = { index ->
            showSimulationRun(index)
        }
        mainTabbedPane.addTab("Help", createHelpPanel())
        
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configTabs, mainTabbedPane)
        splitPane.dividerLocation = 380
        add(splitPane, BorderLayout.CENTER)
    }



    private fun addFieldRow(p: JPanel, labelText: String, key: String) {
        p.add(JLabel(labelText))
        val tf = JTextField()
        tf.horizontalAlignment = JTextField.RIGHT
        textFields[key] = tf
        p.add(tf)
    }

    private fun addLockableFieldRow(p: JPanel, labelText: String, key: String) {
        val labelPanel = JPanel(BorderLayout(5, 0))
        val cb = JCheckBox()
        cb.toolTipText = "Lock this age for optimization"
        lockCheckboxes[key] = cb
        labelPanel.add(cb, BorderLayout.WEST)
        
        val label = JLabel(labelText)
        labelPanel.add(label, BorderLayout.CENTER)
        
        p.add(labelPanel)
        
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
        
        fun addRow(row: Int, labelText: String, comp: JComponent) {
            gbc.gridy = row
            
            gbc.gridx = 0
            gbc.weightx = 0.3
            p.add(JLabel(labelText), gbc)
            
            gbc.gridx = 1
            gbc.weightx = 0.7
            p.add(comp, gbc)
        }
        
        val dfStartDate = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["start_date"] = dfStartDate
        addRow(0, "Start Date:", dfStartDate)
        
        val tfLifetime = JTextField()
        tfLifetime.horizontalAlignment = JTextField.RIGHT
        textFields["lifetime"] = tfLifetime
        
        val cbLifetimeLock = JCheckBox()
        cbLifetimeLock.toolTipText = "Lock Self life expectancy"
        checkboxes["lifetime_locked"] = cbLifetimeLock
        cbLifetimeLock.addActionListener {
            tfLifetime.isEnabled = !cbLifetimeLock.isSelected
        }
        
        val labelPanel = JPanel(BorderLayout(5, 0))
        labelPanel.add(cbLifetimeLock, BorderLayout.WEST)
        labelPanel.add(JLabel("Self Life Expectancy:"), BorderLayout.CENTER)
        
        gbc.gridy = 1
        gbc.gridx = 0
        gbc.weightx = 0.3
        p.add(labelPanel, gbc)
        
        gbc.gridx = 1
        gbc.weightx = 0.7
        p.add(tfLifetime, gbc)

        // Spouse Life Expectancy
        val tfLifetimeSpouse = JTextField()
        tfLifetimeSpouse.horizontalAlignment = JTextField.RIGHT
        textFields["lifetime_spouse"] = tfLifetimeSpouse
        
        val cbLifetimeSpouseLock = JCheckBox()
        cbLifetimeSpouseLock.toolTipText = "Lock Spouse life expectancy"
        checkboxes["lifetime_spouse_locked"] = cbLifetimeSpouseLock
        cbLifetimeSpouseLock.addActionListener {
            tfLifetimeSpouse.isEnabled = !cbLifetimeSpouseLock.isSelected
        }
        
        val labelPanelSpouse = JPanel(BorderLayout(5, 0))
        labelPanelSpouse.add(cbLifetimeSpouseLock, BorderLayout.WEST)
        labelPanelSpouse.add(JLabel("Spouse Life Expectancy:"), BorderLayout.CENTER)
        
        gbc.gridy = 2
        gbc.gridx = 0
        gbc.weightx = 0.3
        p.add(labelPanelSpouse, gbc)
        
        gbc.gridx = 1
        gbc.weightx = 0.7
        p.add(tfLifetimeSpouse, gbc)
        
        val tfReturn = JTextField()
        tfReturn.horizontalAlignment = JTextField.RIGHT
        textFields["investment_return"] = tfReturn
        addRow(3, "Investment Return (ROI) %:", tfReturn)
        
        val tfReturnDev = JTextField()
        tfReturnDev.horizontalAlignment = JTextField.RIGHT
        textFields["investment_std_dev"] = tfReturnDev
        addRow(4, "Investment Return Std Dev %:", tfReturnDev)
        
        val tfInflation = JTextField()
        tfInflation.horizontalAlignment = JTextField.RIGHT
        textFields["inflation"] = tfInflation
        addRow(5, "Inflation Rate %:", tfInflation)
        
        val tfInflationDev = JTextField()
        tfInflationDev.horizontalAlignment = JTextField.RIGHT
        textFields["inflation_std_dev"] = tfInflationDev
        addRow(6, "Inflation Std Dev %:", tfInflationDev)
        
        val tfInterestRate = JTextField()
        tfInterestRate.horizontalAlignment = JTextField.RIGHT
        textFields["interest_rate"] = tfInterestRate
        addRow(7, "Interest Rate %:", tfInterestRate)
        
        val tfDividendRate = JTextField()
        tfDividendRate.horizontalAlignment = JTextField.RIGHT
        textFields["dividend_rate"] = tfDividendRate
        addRow(8, "Dividend Rate %:", tfDividendRate)
        
        val container = JPanel(BorderLayout())
        container.add(p, BorderLayout.NORTH)
        return JScrollPane(container)
    }

    private fun createIncomePanel(): JScrollPane {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(10, 10, 10, 10)

        // General raise % and Optimize button
        val top = JPanel(FlowLayout(FlowLayout.LEFT))
        top.add(JLabel("Raise Percent %:"))
        val raiseF = JTextField(6)
        raiseF.horizontalAlignment = JTextField.RIGHT
        textFields["raise"] = raiseF
        top.add(raiseF)
        
        top.add(Box.createHorizontalStrut(10))
        val optBtn = JButton("Optimize")
        optBtn.addActionListener { runSocialSecurityPensionOptimization() }
        top.add(optBtn)
        
        container.add(top)

        // Self Column
        val selfPanel = JPanel(GridLayout(6, 2, 5, 5))
        selfPanel.border = TitledBorder("Self")
        
        selfPanel.add(JLabel("Birthdate:"))
        val bdSelf = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["birthdate"] = bdSelf
        selfPanel.add(bdSelf)
        
        addFieldRow(selfPanel, "Starting Salary:", "salary")
        addFieldRow(selfPanel, "Retirement Age:", "retirement_age")
        addLockableFieldRow(selfPanel, "SS Start Age:", "ss_age")
        addFieldRow(selfPanel, "SS PIA @ 67:", "ss_pia")
        
        selfPanel.add(JLabel("Pensions:"))
        managePensionsButtonSelf.addActionListener { showPensionManagerDialog(true) }
        selfPanel.add(managePensionsButtonSelf)
        container.add(selfPanel)

        // Spouse Column
        val spousePanel = JPanel(GridLayout(6, 2, 5, 5))
        spousePanel.border = TitledBorder("Spouse")
        
        spousePanel.add(JLabel("Birthdate:"))
        val bdSpouse = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        dateFields["birthdate_spouse"] = bdSpouse
        spousePanel.add(bdSpouse)
        
        addFieldRow(spousePanel, "Starting Salary:", "salary_spouse")
        addFieldRow(spousePanel, "Retirement Age:", "retirement_age_spouse")
        addLockableFieldRow(spousePanel, "SS Start Age:", "ss_age_spouse")
        addFieldRow(spousePanel, "SS PIA @ 67:", "ss_pia_spouse")
        
        spousePanel.add(JLabel("Pensions:"))
        managePensionsButtonSpouse.addActionListener { showPensionManagerDialog(false) }
        spousePanel.add(managePensionsButtonSpouse)
        container.add(spousePanel)
        container.add(Box.createVerticalStrut(10))
        container.add(createOneTimeEventsPanel(true))

        val wrapper = JPanel(BorderLayout())
        wrapper.add(container, BorderLayout.NORTH)
        return JScrollPane(wrapper)
    }

    private fun createGridBagPanel(): JPanel {
        val p = JPanel(GridBagLayout())
        p.border = EmptyBorder(10, 10, 10, 10)
        return p
    }

    private fun addGridBagFieldRow(p: JPanel, labelText: String, key: String, row: Int) {
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridy = row

        // Label
        gbc.gridx = 0
        gbc.weightx = 0.85
        p.add(JLabel(labelText), gbc)

        // Text Field
        gbc.gridx = 1
        gbc.weightx = 0.15
        val tf = JTextField(10)
        tf.horizontalAlignment = JTextField.RIGHT
        textFields[key] = tf
        p.add(tf, gbc)
    }

    private fun addGridBagComboBoxRow(p: JPanel, labelText: String, key: String, options: Array<String>, row: Int) {
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridy = row

        // Label
        gbc.gridx = 0
        gbc.weightx = 0.85
        p.add(JLabel(labelText), gbc)

        // Combo Box
        gbc.gridx = 1
        gbc.weightx = 0.15
        val combo = JComboBox(options)
        comboBoxes[key] = combo
        p.add(combo, gbc)
    }

    private fun addGridBagCheckRow(p: JPanel, labelText: String, key: String, row: Int) {
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridy = row

        // Label/Check
        gbc.gridx = 0
        gbc.weightx = 0.85
        val cb = JCheckBox(labelText)
        checkboxes[key] = cb
        p.add(cb, gbc)

        // Place holder for column 2
        gbc.gridx = 1
        gbc.weightx = 0.15
        p.add(Box.createGlue(), gbc)
    }

    private fun addGridBagDateRow(p: JPanel, labelText: String, key: String, row: Int, df: com.moneydance.awt.JDateField) {
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridy = row

        // Label
        gbc.gridx = 0
        gbc.weightx = 0.85
        p.add(JLabel(labelText), gbc)

        // Date Field
        gbc.gridx = 1
        gbc.weightx = 0.15
        dateFields[key] = df
        p.add(df, gbc)
    }

    private fun createExpensesPanel(): JScrollPane {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(10, 10, 10, 10)

        // Group 1: General Spending
        val generalPanel = createGridBagPanel()
        generalPanel.border = TitledBorder("General Spending")
        addGridBagFieldRow(generalPanel, "First Year Spending:", "start_other_spending", 0)
        addGridBagFieldRow(generalPanel, "Annual Travel:", "travel", 1)
        val trEnd = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        addGridBagDateRow(generalPanel, "Travel End Date:", "travel_end", 2, trEnd)
        container.add(generalPanel)
        container.add(Box.createVerticalStrut(10))

        // Group 2: Taxes
        val taxPanel = createGridBagPanel()
        taxPanel.border = TitledBorder("Taxes")
        addGridBagFieldRow(taxPanel, "Property Taxes:", "prop_taxes", 0)
        addGridBagFieldRow(taxPanel, "Payroll Tax Rate %:", "payroll_tax_rate", 1)
        addGridBagFieldRow(taxPanel, "Fed Std Deduction:", "fed_std_deduction", 2)
        addGridBagFieldRow(taxPanel, "State Std Deduction:", "state_std_deduction", 3)
        addGridBagFieldRow(taxPanel, "State Tax Rate %:", "state_tax_rate", 4)
        container.add(taxPanel)
        container.add(Box.createVerticalStrut(10))

        // Group 3: Housing
        val housingPanel = createGridBagPanel()
        housingPanel.border = TitledBorder("Housing")
        addGridBagFieldRow(housingPanel, "Monthly Mortgage:", "mortgage", 0)
        val mtgEnd = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        addGridBagDateRow(housingPanel, "Mortgage End Date:", "mortgage_end", 1, mtgEnd)
        container.add(housingPanel)
        container.add(Box.createVerticalStrut(10))

        // Group 4: Future Care
        val futureCarePanel = JPanel(GridBagLayout())
        futureCarePanel.border = TitledBorder("Future Care")
        
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        
        // Headers (Row 0)
        gbc.gridy = 0
        gbc.gridx = 0
        gbc.weightx = 0.75
        futureCarePanel.add(JLabel(""), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 0.08
        futureCarePanel.add(JLabel("Years", SwingConstants.CENTER), gbc)
        
        gbc.gridx = 2
        gbc.weightx = 0.17
        futureCarePanel.add(JLabel("Cost", SwingConstants.CENTER), gbc)
        
        fun addFutureCareRow(row: Int, labelText: String, yearsKey: String, costKey: String) {
            gbc.gridy = row
            
            // Label
            gbc.gridx = 0
            gbc.weightx = 0.75
            futureCarePanel.add(JLabel(labelText), gbc)
            
            // Years
            gbc.gridx = 1
            gbc.weightx = 0.08
            val tfYears = JTextField(5)
            tfYears.horizontalAlignment = JTextField.RIGHT
            textFields[yearsKey] = tfYears
            futureCarePanel.add(tfYears, gbc)
            
            // Cost
            gbc.gridx = 2
            gbc.weightx = 0.17
            val tfCost = JTextField(10)
            tfCost.horizontalAlignment = JTextField.RIGHT
            textFields[costKey] = tfCost
            futureCarePanel.add(tfCost, gbc)
        }
        
        addFutureCareRow(1, "Light Assistance:", "eldercare_light_years", "eldercare_light_cost")
        addFutureCareRow(2, "High-Acuity Care:", "eldercare_acuity_years", "eldercare_acuity_cost")
        addFutureCareRow(3, "Facility Care:", "eldercare_facility_years", "eldercare_facility_cost")
        
        container.add(futureCarePanel)
        container.add(Box.createVerticalStrut(10))

        // Group 5: Donor Advised Fund
        val dafPanel = createGridBagPanel()
        dafPanel.border = TitledBorder("Donor Advised Fund (DAF)")
        addGridBagFieldRow(dafPanel, "DAF Annual Dist:", "daf_distro", 0)
        addGridBagFieldRow(dafPanel, "DAF Excess Pct %:", "daf_excess_pct", 1)
        container.add(dafPanel)
        container.add(Box.createVerticalStrut(10))
        container.add(createOneTimeEventsPanel(false))

        val wrapper = JPanel(BorderLayout())
        wrapper.add(container, BorderLayout.NORTH)
        return JScrollPane(wrapper)
    }

    private fun createSavingsPanel(): JScrollPane {
        textFields["start_taxable_lots"] = JTextField()
        val p = createGridBagPanel()
        p.border = TitledBorder("Balances")
        addGridBagFieldRow(p, "IRA Savings:", "start_ira_savings", 0)
        addGridBagFieldRow(p, "  IRA Cash Portion:", "start_ira_cash", 1)
        addGridBagFieldRow(p, "Roth Savings:", "start_roth_savings", 2)
        addGridBagFieldRow(p, "  Roth Cash Portion:", "start_roth_cash", 3)
        addGridBagFieldRow(p, "Other Savings (Brokerage):", "start_other_savings", 4)
        addGridBagFieldRow(p, "  Other Cash Portion:", "start_other_cash", 5)
        addGridBagFieldRow(p, "Taxable Cost Basis:", "start_taxable_cost_basis", 6)
        addGridBagFieldRow(p, "DAF Savings:", "start_daf_savings", 7)
        
        val rothPanel = createGridBagPanel()
        rothPanel.border = TitledBorder("Roth Conversions")
        addGridBagCheckRow(rothPanel, "Enable Roth Conversions", "roth_enabled", 0)
        addGridBagFieldRow(rothPanel, "Start Age:", "roth_start_age", 1)
        addGridBagFieldRow(rothPanel, "End Age:", "roth_end_age", 2)
        addGridBagComboBoxRow(rothPanel, "Max Ordinary Bracket to Fill:", "roth_max_ordinary_bracket", arrayOf("None", "Std Ded", "10%", "12%", "22%", "24%", "32%", "35%", "37%"), 3)
        addGridBagComboBoxRow(rothPanel, "Max Cap Gains Rate to Trigger:", "roth_max_cg_rate", arrayOf("0%", "15%", "20%"), 4)
        addGridBagFieldRow(rothPanel, "Max Annual Tax Impact Cap ($):", "roth_tax_impact_cap", 5)
        addGridBagFieldRow(rothPanel, "Max Realized Loss Limit ($):", "roth_max_realized_loss", 6)

        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(10, 10, 10, 10)
        p.alignmentX = Component.LEFT_ALIGNMENT
        rothPanel.alignmentX = Component.LEFT_ALIGNMENT
        container.add(p)
        container.add(Box.createVerticalStrut(10))
        container.add(rothPanel)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(container, BorderLayout.NORTH)
        return JScrollPane(wrapper)
    }

    private fun createSettingsPanel(): JScrollPane {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = EmptyBorder(10, 10, 10, 10)

        fun addCheck(labelText: String, key: String) {
            val cb = JCheckBox(labelText)
            cb.alignmentX = Component.LEFT_ALIGNMENT
            cb.addActionListener { recalc() }
            checkboxes[key] = cb
            container.add(cb)
            container.add(Box.createVerticalStrut(5))
        }

        addCheck("Stack income / expense", "stack_incomes")
        addCheck("Stack savings", "stack_savings")
        addCheck("Show Savings types", "show_savings_types")
        addCheck("Show Distribution types", "show_distros")
        addCheck("Display Inflation & ROI", "show_inflation_roi")

        container.add(Box.createVerticalStrut(10))

        val p = JPanel(GridBagLayout())
        p.alignmentX = Component.LEFT_ALIGNMENT
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 0, 5, 10)

        gbc.gridy = 0
        gbc.gridx = 0
        gbc.weightx = 0.3
        p.add(JLabel("Guardrails %:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 0.7
        val tf1 = JTextField(10)
        tf1.horizontalAlignment = JTextField.RIGHT
        textFields["guardrail_percent"] = tf1
        p.add(tf1, gbc)

        gbc.gridy = 1
        gbc.gridx = 0
        gbc.weightx = 0.3
        p.add(JLabel("Simulations count:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 0.7
        val tf2 = JTextField(10)
        tf2.horizontalAlignment = JTextField.RIGHT
        textFields["num_simulations"] = tf2
        p.add(tf2, gbc)

        container.add(p)

        val wrapper = JPanel(BorderLayout())
        wrapper.add(container, BorderLayout.NORTH)
        return JScrollPane(wrapper)
    }

    private fun createTablePanel(): JPanel {
        val p = JPanel(BorderLayout())
        resultTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        
        // Add custom copy action to handle column headers and strip HTML (which avoids default TransferHandler crashes on RMD years)
        resultTable.actionMap.put("copy", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent) {
                val selectedRows = resultTable.selectedRows
                val selectedCols = if (resultTable.columnSelectionAllowed) {
                    resultTable.selectedColumns
                } else {
                    IntArray(resultTable.columnCount) { it }
                }
                if (selectedRows.isEmpty() || selectedCols.isEmpty()) return
                
                val sb = StringBuilder()
                // Column headers
                for (j in selectedCols.indices) {
                    val colIdx = selectedCols[j]
                    val colName = resultTable.getColumnName(colIdx)
                    sb.append(colName.replace(Regex("<[^>]*>"), ""))
                    if (j < selectedCols.size - 1) {
                        sb.append("\t")
                    }
                }
                sb.append("\n")
                
                // Rows
                for (i in selectedRows.indices) {
                    val rowIdx = selectedRows[i]
                    for (j in selectedCols.indices) {
                        val colIdx = selectedCols[j]
                        val value = resultTable.getValueAt(rowIdx, colIdx)
                        val strVal = value?.toString() ?: ""
                        val cleanVal = strVal.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ")
                        sb.append(cleanVal)
                        if (j < selectedCols.size - 1) {
                            sb.append("\t")
                        }
                    }
                    if (i < selectedRows.size - 1) {
                        sb.append("\n")
                    }
                }
                
                try {
                    val selection = java.awt.datatransfer.StringSelection(sb.toString())
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                } catch (_: Exception) {
                    // Fail silently
                }
            }
        })

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
        pensionsConfig.clear()
        eventsConfig.clear()
        for ((k, v) in data) {
            if (k.startsWith("pension_")) {
                pensionsConfig[k] = v
            } else if (k.startsWith("event_")) {
                eventsConfig[k] = v
            }
        }
        refreshEventsTable(true)
        refreshEventsTable(false)
        
        // Backward compatibility: Convert legacy fields if dynamic count not defined
        if (!pensionsConfig.containsKey("pension_count_self")) {
            val age = pensionsConfig["pension_age"]?.toDoubleOrNull() ?: -1.0
            val early = pensionsConfig["pension_early"]?.toDoubleOrNull() ?: 0.0
            val late = pensionsConfig["pension_late"]?.toDoubleOrNull() ?: 0.0
            if (age >= 0.0 && (early > 0.0 || late > 0.0)) {
                pensionsConfig["pension_count_self"] = "1"
                pensionsConfig["pension_name_self_0"] = "Default Pension"
                pensionsConfig["pension_start_age_self_0"] = age.toString()
                pensionsConfig["pension_schedule_self_0"] = "60.0:$early;70.0:$late"
                pensionsConfig["pension_locked_self_0"] = (pensionsConfig["pension_age_locked"] ?: "false")
            }
        }
        if (!pensionsConfig.containsKey("pension_count_spouse")) {
            val age = pensionsConfig["pension_age_spouse"]?.toDoubleOrNull() ?: -1.0
            val early = pensionsConfig["pension_early_spouse"]?.toDoubleOrNull() ?: 0.0
            val late = pensionsConfig["pension_late_spouse"]?.toDoubleOrNull() ?: 0.0
            if (age >= 0.0 && (early > 0.0 || late > 0.0)) {
                pensionsConfig["pension_count_spouse"] = "1"
                pensionsConfig["pension_name_spouse_0"] = "Default Pension"
                pensionsConfig["pension_start_age_spouse_0"] = age.toString()
                pensionsConfig["pension_schedule_spouse_0"] = "60.0:$early;70.0:$late"
                pensionsConfig["pension_locked_spouse_0"] = (pensionsConfig["pension_age_spouse_locked"] ?: "false")
            }
        }
        updatePensionButtonsText()

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
            val combo = comboBoxes[k]
            if (combo != null) {
                combo.selectedItem = v
                continue
            }
            val cb = checkboxes[k]
            if (cb != null) {
                cb.isSelected = v.toBoolean()
                if (k == "lifetime_locked") {
                    textFields["lifetime"]?.isEnabled = !cb.isSelected
                }
                if (k == "lifetime_spouse_locked") {
                    textFields["lifetime_spouse"]?.isEnabled = !cb.isSelected
                }
                continue
            }
            if (k == "start_taxable_mmf") {
                startTaxableMmf = v.toDoubleOrNull() ?: 0.0
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
                    } catch (_: Exception) {
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
        for ((k, combo) in comboBoxes) {
            map[k] = combo.selectedItem?.toString() ?: ""
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
        map.putAll(pensionsConfig)
        map.putAll(eventsConfig)
        map["start_taxable_mmf"] = startTaxableMmf.toString()
        return map
    }

    private var startTaxableMmf: Double = 0.0

    // MoneyDance Balance Loading
    private fun loadMoneyDanceBalances() {
        val bookContext = extension.getUnprotectedContext()
        val dateInt = dateFields["start_date"]?.dateInt ?: 0
        MoneydanceDataLoader.loadBalances(
            mdBook = mdBook,
            dateInt = dateInt,
            onProgress = { url -> bookContext?.showURL(url) },
            onSuccess = { res ->
                startTaxableMmf = res.taxableMmf
                textFields["start_taxable_lots"]?.text = res.taxableLotsStr
                textFields["start_ira_savings"]?.text = formatDollar(res.iraBal)
                textFields["start_ira_cash"]?.text = formatDollar(res.iraCash)
                textFields["start_roth_savings"]?.text = formatDollar(res.rothBal)
                textFields["start_roth_cash"]?.text = formatDollar(res.rothCash)
                textFields["start_other_savings"]?.text = formatDollar(res.otherBal)
                textFields["start_other_cash"]?.text = formatDollar(res.otherCash)
                textFields["start_taxable_cost_basis"]?.text = formatDollar(res.otherBasis)
                textFields["start_daf_savings"]?.text = formatDollar(res.dafBal)
                recalc()
            }
        )
    }

    private fun loadConfigurationFromFile(file: File) {
        CalculatorStorage.loadConfigurationFromFile(this, file) { map ->
            setFormData(map)
            recalc()
        }
    }

    private fun openConfigFile() {
        CalculatorStorage.openConfigFile(this) { map ->
            setFormData(map)
            recalc()
        }
    }

    // Main recalculate action
    private fun recalc() {
        updateDefaultRothEndAge()
        lastSimRuns = null
        activeSimIndex = null
        title = "Retirement Calculator"
        val form = getFormData()
        
        currentResults.clear()
        
        var curYear: YearRow? = null
        val lifetimeSelf = form.getInt("lifetime", 100)
        val lifetimeSpouse = form.getInt("lifetime_spouse", 100)
        
        while (true) {
            if (curYear != null && 
                curYear.ageSelf > lifetimeSelf && 
                curYear.ageSpouse > lifetimeSpouse && 
                min(curYear.ageSelf, curYear.ageSpouse) > 100) {
                break
            }
            val nextRow = YearRow(form, curYear, lifetimeSelf.toDouble(), lifetimeSpouse.toDouble())
            currentResults.add(nextRow.toMap())
            curYear = nextRow
        }
        
        updateTable()
        chartPanel.updateData(currentResults, getFormData())
        mainTabbedPane.setSelectedIndex(0)
    }

    private fun runSocialSecurityPensionOptimization() {
        val baseFormData = try {
            getFormData()
        } catch (_: Exception) {
            JOptionPane.showMessageDialog(this, "Please ensure all numeric settings are filled with valid values.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        val ssSelfLocked = lockCheckboxes["ss_age"]?.isSelected == true
        val ssSpouseLocked = lockCheckboxes["ss_age_spouse"]?.isSelected == true

        AgeOptimizationEngine.runOptimization(
            parent = this,
            baseFormData = baseFormData,
            ssSelfLocked = ssSelfLocked,
            ssSpouseLocked = ssSpouseLocked,
            pensionsConfig = pensionsConfig
        ) { best ->
            if (!ssSelfLocked) textFields["ss_age"]?.text = AgeOptimizationEngine.formatAge(best.ssSelf)
            if (!ssSpouseLocked) textFields["ss_age_spouse"]?.text = AgeOptimizationEngine.formatAge(best.ssSpouse)
            for ((k, age) in best.pensionAges) {
                pensionsConfig[k] = AgeOptimizationEngine.formatAge(age)
            }
            updatePensionButtonsText()
            recalc()
        }
    }

    // Monte Carlo simulation runner
    private fun runSimulation() {
        activeSimIndex = null
        title = "Retirement Calculator"
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
                    lastSimRuns = runs
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
        val cols = mutableListOf<String>()
        cols.add("Year")
        cols.add("Age")
        cols.add("Savings")
        
        val showSavingsTypes = checkboxes["show_savings_types"]?.isSelected == true
        if (showSavingsTypes) {
            cols.add("IRA")
            cols.add("Roth")
            cols.add("Taxable")
            cols.add("DAF")
        }
        
        val showDist = checkboxes["show_distros"]?.isSelected == true
        if (showDist) {
            cols.add("Taxable Basis")
        }
        
        cols.add("Salary")
        cols.add("SS/Pension")
        if (showDist) {
            cols.add("Div / Int Taxable")
        }
        cols.add("Gains")
        cols.add("Distrib")
        
        if (showDist) {
            cols.add("IRA Distro")
            cols.add("Roth Distro")
            cols.add("Taxable Distro")
        }
        
        cols.add("Taxes")
        cols.add("Housing")
        cols.add("Travel & Eldercare")
        cols.add("Other Spending")
        
        val showInfl = checkboxes["show_inflation_roi"]?.isSelected == true
        if (showInfl) {
            cols.add("Inflation %")
            cols.add("ROI %")
        }

        val colNames = cols.toTypedArray()
        tableModel.setDataVector(emptyArray<Array<Any>>(), colNames)
        
        val df = DecimalFormat("$#,##0")
        val pctDf = DecimalFormat("0.00%")
        
        for (rowData in currentResults) {
            val v = Vector<Any>()
            v.add(rowData["year"].toString())
            
            val ageSelfVal = (rowData["age"] as? Number)?.toInt() ?: 0
            val deathAgeSelfVal = (rowData["death_age_self"] as? Number)?.toDouble() ?: Double.MAX_VALUE
            val selfAgeStr = if (ageSelfVal > deathAgeSelfVal) "-" else ageSelfVal.toString()
            
            val ageSpouseVal = (rowData["age_spouse"] as? Number)?.toInt() ?: 0
            val deathAgeSpouseVal = (rowData["death_age_spouse"] as? Number)?.toDouble() ?: Double.MAX_VALUE
            val spouseAgeStr = if (ageSpouseVal > deathAgeSpouseVal) "-" else ageSpouseVal.toString()
            
            v.add("$selfAgeStr/$spouseAgeStr")
            
            v.add(df.format(rowData["savings"]))
            
            if (showSavingsTypes) {
                v.add(df.format(rowData["ira_savings"]))
                v.add(df.format(rowData["roth_savings"]))
                v.add(df.format(rowData["other_savings"]))
                v.add(df.format(rowData["daf_savings"]))
            }
            
            if (showDist) {
                v.add(df.format(rowData["cost_basis"]))
            }
            v.add(df.format(rowData["salary"]))
            v.add(df.format(rowData["ss"]))
            if (showDist) {
                val divVal = (rowData["taxable_dividends"] as? Number)?.toDouble() ?: 0.0
                val intVal = (rowData["taxable_interest"] as? Number)?.toDouble() ?: 0.0
                v.add(df.format(divVal + intVal))
            }
            v.add(df.format(rowData["roi"]))
            v.add(df.format(rowData["distro"]))
            if (showDist) {
                val iraDistVal = (rowData["ira_distro"] as? Number)?.toDouble() ?: 0.0
                val rmdVal = (rowData["rmd"] as? Number)?.toDouble() ?: 0.0
                val formattedIra = df.format(iraDistVal)
                if (iraDistVal > 0.01 && java.lang.Math.abs(iraDistVal - rmdVal) < 0.01) {
                    v.add("<html><b>$formattedIra</b></html>")
                } else {
                    v.add(formattedIra)
                }
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
        
        // Custom Table header rendering
        resultTable.tableHeader.defaultRenderer = CustomHeaderRenderer()
        
        resultTable.revalidate()
    }

    // Save configuration to JSON
    private fun saveConfigFile() {
        CalculatorStorage.saveConfigFile(this, getFormData())
    }

    fun saveConfigToLocalStorage() {
        CalculatorStorage.saveConfigToLocalStorage(mdBook, getFormData())
    }

    private fun restoreConfigFromLocalStorage() {
        val map = CalculatorStorage.restoreConfigFromLocalStorage(mdBook)
        if (map != null) {
            setFormData(map)
        }
    }

    private fun updateDefaultRothEndAge() {
        val bdSelf = dateFields["birthdate"]?.dateInt ?: 0
        val bdSpouse = dateFields["birthdate_spouse"]?.dateInt ?: 0
        
        val selfYear = if (bdSelf > 0) bdSelf / 10000 else 1968
        val spouseYear = if (bdSpouse > 0) bdSpouse / 10000 else 1970
        val oldestYear = min(selfYear, spouseYear)
        
        val defaultRmdAge = when {
            oldestYear >= 1960 -> 75
            oldestYear >= 1951 -> 73
            else -> 72
        }
        
        val currentEndAgeStr = textFields["roth_end_age"]?.text
        if (currentEndAgeStr.isNullOrEmpty() || currentEndAgeStr == "75" || currentEndAgeStr == "73" || currentEndAgeStr == "72") {
            textFields["roth_end_age"]?.text = defaultRmdAge.toString()
        }
    }


    // Print helper
    private fun printReport() {
        try {
            val printerJob = PrinterJob.getPrinterJob()
            val attr = javax.print.attribute.HashPrintRequestAttributeSet()
            attr.add(javax.print.attribute.standard.OrientationRequested.LANDSCAPE)
            
            val printable = Printable { g, pf, pageIndex ->
                if (pageIndex > 0) return@Printable Printable.NO_SUCH_PAGE
                
                val g2d = g as Graphics2D
                g2d.color = Color.BLACK
                g2d.font = Font("Arial", Font.BOLD, 14)
                g2d.drawString("Retirement Calculator Projections", pf.imageableX.toInt(), (pf.imageableY + 15).toInt())
                
                g2d.font = Font("Arial", Font.PLAIN, 10)
                val runTimeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a"))
                g2d.drawString("Report Generated: $runTimeStr", pf.imageableX.toInt(), (pf.imageableY + 30).toInt())
                
                val simIdx = activeSimIndex
                val shift = if (simIdx != null) {
                    g2d.drawString("Simulation Run #${simIdx + 1}", pf.imageableX.toInt(), (pf.imageableY + 42).toInt())
                    50.0
                } else {
                    38.0
                }
                
                val tablePrintable = resultTable.getPrintable(JTable.PrintMode.FIT_WIDTH, null, null)
                val paper = pf.paper
                paper.setImageableArea(
                    pf.imageableX,
                    pf.imageableY + shift,
                    pf.imageableWidth,
                    pf.imageableHeight - shift
                )
                val delegatePf = pf.clone() as PageFormat
                delegatePf.paper = paper
                
                tablePrintable.print(g, delegatePf, pageIndex)
            }
            
            printerJob.setPrintable(printable)
            if (printerJob.printDialog(attr)) {
                printerJob.print(attr)
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Error printing: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun showSimulationRun(index: Int) {
        val runs = lastSimRuns ?: return
        if (index !in runs.indices) return
        
        activeSimIndex = index
        title = "Retirement Calculator - Simulation #${index + 1}"
        
        currentResults.clear()
        for (yearRow in runs[index]) {
            currentResults.add(yearRow.toMap())
        }
        
        updateTable()
        chartPanel.updateData(currentResults, getFormData())
        mainTabbedPane.setSelectedIndex(0)
    }

    private fun exportPlan() {
        FinancialPlanExporter.exportPlan(this, currentResults, getFormData())
    }

    private fun copyStockLotsForSelectedRow() {
        FinancialPlanExporter.copyStockLots(this, resultTable.selectedRow, currentResults)
    }


    private fun updatePensionButtonsText() {
        val selfCount = pensionsConfig["pension_count_self"]?.toIntOrNull() ?: 0
        val spouseCount = pensionsConfig["pension_count_spouse"]?.toIntOrNull() ?: 0
        managePensionsButtonSelf.text = "Manage ($selfCount)"
        managePensionsButtonSpouse.text = "Manage ($spouseCount)"
    }

    private fun getUnlockedPensionStartAgeKeys(isSelf: Boolean): List<String> {
        return PensionDialogs.getUnlockedPensionStartAgeKeys(pensionsConfig, isSelf)
    }

    private fun showPensionManagerDialog(isSelf: Boolean) {
        PensionDialogs.showPensionManagerDialog(
            parent = this,
            isSelf = isSelf,
            pensionsConfig = pensionsConfig,
            onUpdate = { updatePensionButtonsText() },
            onRecalc = { recalc() }
        )
    }

    private fun createOneTimeEventsPanel(isIncome: Boolean): JPanel {
        val title = if (isIncome) "One-Time Income Events" else "One-Time Expense Events"
        val panel = JPanel(BorderLayout(5, 5))
        panel.border = TitledBorder(title)

        val table = if (isIncome) incomeEventsTable else expenseEventsTable
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.rowHeight = 22

        // Currency alignment for Amount column (index 3)
        val rightRenderer = DefaultTableCellRenderer()
        rightRenderer.horizontalAlignment = JLabel.RIGHT
        table.columnModel.getColumn(3).cellRenderer = rightRenderer

        // Center alignment for Date (index 0) and Type (index 2)
        val centerRenderer = DefaultTableCellRenderer()
        centerRenderer.horizontalAlignment = JLabel.CENTER
        table.columnModel.getColumn(0).cellRenderer = centerRenderer
        table.columnModel.getColumn(2).cellRenderer = centerRenderer

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && table.selectedRow >= 0) {
                    showFinancialEventDialog(isIncome, table.selectedRow)
                }
            }
        })

        val scroll = JScrollPane(table)
        scroll.preferredSize = Dimension(380, 110)
        panel.add(scroll, BorderLayout.CENTER)

        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 2))
        val addBtn = JButton("Add Event...")
        addBtn.addActionListener { showFinancialEventDialog(isIncome, -1) }
        val editBtn = JButton("Edit Selected...")
        editBtn.addActionListener {
            val sel = table.selectedRow
            if (sel >= 0) {
                showFinancialEventDialog(isIncome, sel)
            } else {
                JOptionPane.showMessageDialog(this, "Please select an event to edit.", "No Selection", JOptionPane.WARNING_MESSAGE)
            }
        }
        val delBtn = JButton("Delete Selected")
        delBtn.addActionListener {
            val sel = table.selectedRow
            if (sel >= 0) {
                deleteFinancialEvent(isIncome, sel)
            } else {
                JOptionPane.showMessageDialog(this, "Please select an event to delete.", "No Selection", JOptionPane.WARNING_MESSAGE)
            }
        }

        btnPanel.add(addBtn)
        btnPanel.add(editBtn)
        btnPanel.add(delBtn)
        panel.add(btnPanel, BorderLayout.SOUTH)

        refreshEventsTable(isIncome)
        return panel
    }

    private fun refreshEventsTable(isIncome: Boolean) {
        val prefix = if (isIncome) "income" else "expense"
        val model = if (isIncome) incomeEventsModel else expenseEventsModel
        model.rowCount = 0
        val count = eventsConfig["event_count_$prefix"]?.toIntOrNull() ?: 0
        val df = DecimalFormat("$#,##0.00")
        for (i in 0 until count) {
            val name = eventsConfig["event_name_${prefix}_$i"] ?: ""
            val date = eventsConfig["event_date_${prefix}_$i"] ?: ""
            val type = eventsConfig["event_type_${prefix}_$i"] ?: ""
            val amt = eventsConfig["event_amount_${prefix}_$i"]?.toDoubleOrNull() ?: 0.0
            val basis = eventsConfig["event_basis_${prefix}_$i"]?.toDoubleOrNull() ?: 0.0
            val amtStr = if (type == "NUA Transfer (IRA to Brokerage)" && basis > 0.0) {
                "${df.format(amt)} (Basis: ${df.format(basis)})"
            } else {
                df.format(amt)
            }
            model.addRow(arrayOf(date, name, type, amtStr))
        }
    }

    private fun deleteFinancialEvent(isIncome: Boolean, index: Int) {
        val prefix = if (isIncome) "income" else "expense"
        val count = eventsConfig["event_count_$prefix"]?.toIntOrNull() ?: 0
        if (index < 0 || index >= count) return

        for (i in index until count - 1) {
            val next = i + 1
            eventsConfig["event_name_${prefix}_$i"] = eventsConfig["event_name_${prefix}_$next"] ?: ""
            eventsConfig["event_date_${prefix}_$i"] = eventsConfig["event_date_${prefix}_$next"] ?: ""
            eventsConfig["event_type_${prefix}_$i"] = eventsConfig["event_type_${prefix}_$next"] ?: ""
            eventsConfig["event_amount_${prefix}_$i"] = eventsConfig["event_amount_${prefix}_$next"] ?: ""
            eventsConfig["event_basis_${prefix}_$i"] = eventsConfig["event_basis_${prefix}_$next"] ?: ""
        }
        val last = count - 1
        eventsConfig.remove("event_name_${prefix}_$last")
        eventsConfig.remove("event_date_${prefix}_$last")
        eventsConfig.remove("event_type_${prefix}_$last")
        eventsConfig.remove("event_amount_${prefix}_$last")
        eventsConfig.remove("event_basis_${prefix}_$last")
        eventsConfig["event_count_$prefix"] = last.toString()

        refreshEventsTable(isIncome)
        recalc()
    }

    private fun showFinancialEventDialog(isIncome: Boolean, editIndex: Int) {
        val startYear = textFields["start_year"]?.text?.toIntOrNull() ?: 2026
        FinancialEventDialogs.showFinancialEventDialog(
            parent = this,
            isIncome = isIncome,
            editIndex = editIndex,
            eventsConfig = eventsConfig,
            defaultStartYear = startYear,
            onSave = {
                refreshEventsTable(isIncome)
                recalc()
            }
        )
    }
}
