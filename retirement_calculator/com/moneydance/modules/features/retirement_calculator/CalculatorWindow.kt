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
        
        val today = LocalDate.now()
        dateFields["start_date"]?.dateInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth

        // Initial populate from default data
        setFormData(defaultData)
        
        // Try to restore configuration from Moneydance localStorage
        restoreConfigFromLocalStorage()
        updateDefaultRothEndAge()
        
        // Read account balances from MoneyDance if possible
        loadMoneyDanceBalances()
        
        // Perform initial calculation
        recalc()
        
        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        
        // Save config when window is closed
        this.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                saveConfigToLocalStorage()
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
                                                } catch (_: Exception) {
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
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                    if (buySplit != null) {
                                                        val originalShares = try {
                                                            buySplit.javaClass.getMethod("getValue").invoke(buySplit) as Long
                                                        } catch (_: Exception) {
                                                            0L
                                                        }
                                                        var originalCostBasis = try {
                                                            val amt = buySplit.javaClass.getMethod("getAmount").invoke(buySplit) as Long
                                                            Math.abs(amt)
                                                        } catch (_: Exception) {
                                                            0L
                                                        }
                                                        if (originalCostBasis == 0L) {
                                                            originalCostBasis = try {
                                                                val method = Class.forName("com.infinitekind.moneydance.model.InvestUtil")
                                                                    .getDeclaredMethod("getCostBasis", com.infinitekind.moneydance.model.Account::class.java, com.infinitekind.moneydance.model.SplitTxn::class.java)
                                                                method.isAccessible = true
                                                                method.invoke(null, subAcct, buySplit) as Long
                                                            } catch (_: Exception) {
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
                                                        } catch (_: Exception) {
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

    private fun formatAge(age: Double): String {
        return if (age % 1.0 == 0.0) {
            String.format("%.0f", age)
        } else {
            String.format("%.1f", age)
        }
    }

    private fun runSocialSecurityPensionOptimization() {
        val baseFormData = try {
            getFormData()
        } catch (_: Exception) {
            JOptionPane.showMessageDialog(this, "Please ensure all numeric settings are filled with valid values.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }

        val retAgeSelf = max(60.0, baseFormData.getDouble("retirement_age", 62.0)).toInt()
        val retAgeSpouse = max(60.0, baseFormData.getDouble("retirement_age_spouse", 63.0)).toInt()

        val currentSsSelf = baseFormData.getDouble("ss_age", 67.0)
        val currentSsSpouse = baseFormData.getDouble("ss_age_spouse", 67.0)

        val ssSelfLocked = lockCheckboxes["ss_age"]?.isSelected == true
        val ssSpouseLocked = lockCheckboxes["ss_age_spouse"]?.isSelected == true

        val ssSelfRange1 = if (ssSelfLocked) listOf(currentSsSelf) else (retAgeSelf..70).map { it.toDouble() }
        val ssSpouseRange1 = if (ssSpouseLocked) listOf(currentSsSpouse) else (retAgeSpouse..70).map { it.toDouble() }

        val selfPenKeys = getUnlockedPensionStartAgeKeys(true)
        val spousePenKeys = getUnlockedPensionStartAgeKeys(false)

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
            JOptionPane.showMessageDialog(this, "No valid combinations to evaluate.", "Warning", JOptionPane.WARNING_MESSAGE)
            return
        }

        val progress = JProgressBar(0, 100)
        progress.value = 0
        progress.isStringPainted = true

        val dialog = JDialog(this, "Optimizing... 0% complete", true)
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
                    } else {
                        lockCheckboxes[key]?.isSelected == true
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
                    
                    val sbMsg = java.lang.StringBuilder()
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
                        this@CalculatorWindow,
                        sbMsg.toString(),
                        "Optimization Results",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE
                    )
                    
                    if (choice == JOptionPane.YES_OPTION) {
                        if (!ssSelfLocked) textFields["ss_age"]?.text = formatAge(best.ssSelf)
                        if (!ssSpouseLocked) textFields["ss_age_spouse"]?.text = formatAge(best.ssSpouse)
                        for ((k, age) in best.pensionAges) {
                            pensionsConfig[k] = formatAge(age)
                        }
                        updatePensionButtonsText()
                        recalc()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    JOptionPane.showMessageDialog(this@CalculatorWindow, "Error during optimization: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        }

        dialog.defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent) {
                worker.cancel(true)
                dialog.dispose()
            }
        })

        dialog.setSize(400, 120)
        dialog.setLocationRelativeTo(this)
        worker.execute()
        dialog.isVisible = true
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

    fun saveConfigToLocalStorage() {
        try {
            val data = getFormData()
            val sb = java.lang.StringBuilder()
            sb.append("[")
            var first = true
            for ((k, v) in data) {
                if (!first) sb.append(",\n")
                first = false
                sb.append("[\"").append(k.replace("\"", "\\\"")).append("\",\"").append(v.replace("\"", "\\\"")).append("\"]")
            }
            sb.append("]")
            
            val localStorage = mdBook.javaClass.getMethod("getLocalStorage").invoke(mdBook)
            localStorage.javaClass.getMethod("put", String::class.java, String::class.java).invoke(localStorage, "retirement_calculator_config", sb.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreConfigFromLocalStorage() {
        try {
            val localStorage = mdBook.javaClass.getMethod("getLocalStorage").invoke(mdBook)
            val content = localStorage.javaClass.getMethod("get", String::class.java).invoke(localStorage, "retirement_calculator_config") as? String
            if (content != null && content.isNotEmpty()) {
                val map = mutableMapOf<String, String>()
                val regex = Regex("""\[\s*"(.*?)"\s*,\s*"(.*?)"\s*\]""")
                val matches = regex.findAll(content)
                for (m in matches) {
                    val key = m.groupValues[1].replace("\\\"", "\"")
                    val valStr = m.groupValues[2].replace("\\\"", "\"")
                    map[key] = valStr
                }
                setFormData(map)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    private fun exportInstructions() {
        if (currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No calculated projections found. Please recalculate first.", "Error", JOptionPane.ERROR_MESSAGE)
            return
        }
        
        val form = getFormData()
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

    private fun updatePensionButtonsText() {
        val selfCount = pensionsConfig["pension_count_self"]?.toIntOrNull() ?: 0
        val spouseCount = pensionsConfig["pension_count_spouse"]?.toIntOrNull() ?: 0
        managePensionsButtonSelf.text = "Manage ($selfCount)"
        managePensionsButtonSpouse.text = "Manage ($spouseCount)"
    }

    private fun getUnlockedPensionStartAgeKeys(isSelf: Boolean): List<String> {
        val prefix = if (isSelf) "self" else "spouse"
        val count = pensionsConfig["pension_count_$prefix"]?.toIntOrNull() ?: 0
        val keys = mutableListOf<String>()
        for (i in 0 until count) {
            val locked = pensionsConfig["pension_locked_${prefix}_$i"]?.toBoolean() ?: false
            if (!locked) {
                keys.add("pension_start_age_${prefix}_$i")
            }
        }
        return keys
    }

    private fun showPensionManagerDialog(isSelf: Boolean) {
        val prefix = if (isSelf) "self" else "spouse"
        val who = if (isSelf) "Self" else "Spouse"
        val dialog = JDialog(this, "Manage Pensions - $who", true)
        dialog.layout = BorderLayout(10, 10)
        
        val cols = arrayOf("Pension Name", "Start Age", "Benefit Schedule Summary", "Locked")
        val managerModel = object : DefaultTableModel(cols, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        val managerTable = JTable(managerModel)
        
        fun refreshTable() {
            managerModel.rowCount = 0
            val count = pensionsConfig["pension_count_$prefix"]?.toIntOrNull() ?: 0
            for (i in 0 until count) {
                val name = pensionsConfig["pension_name_${prefix}_$i"] ?: "Pension ${i+1}"
                val startAge = pensionsConfig["pension_start_age_${prefix}_$i"] ?: "65.0"
                val sched = pensionsConfig["pension_schedule_${prefix}_$i"] ?: ""
                val locked = pensionsConfig["pension_locked_${prefix}_$i"]?.toBoolean() ?: false
                
                val lockedText = if (locked) "Yes" else "No"
                val schedText = sched.split(";").joinToString(" | ") { part ->
                    val sub = part.split(":")
                    if (sub.size == 2) "${sub[0]}: \$${sub[1]}" else ""
                }
                
                managerModel.addRow(arrayOf(name, startAge, schedText, lockedText))
            }
        }
        
        refreshTable()
        
        val scrollPane = JScrollPane(managerTable)
        scrollPane.border = EmptyBorder(10, 10, 10, 10)
        dialog.add(scrollPane, BorderLayout.CENTER)
        
        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val addBtn = JButton("Add Pension...")
        val editBtn = JButton("Edit Selected...")
        val deleteBtn = JButton("Delete Selected")
        val closeBtn = JButton("Close")
        
        addBtn.addActionListener {
            showPensionEditDialog(isSelf, -1) {
                refreshTable()
                updatePensionButtonsText()
            }
        }
        
        editBtn.addActionListener {
            val selected = managerTable.selectedRow
            if (selected >= 0) {
                showPensionEditDialog(isSelf, selected) {
                    refreshTable()
                    updatePensionButtonsText()
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select a pension to edit.", "Info", JOptionPane.WARNING_MESSAGE)
            }
        }
        
        deleteBtn.addActionListener {
            val selected = managerTable.selectedRow
            if (selected >= 0) {
                val name = pensionsConfig["pension_name_${prefix}_$selected"] ?: "Pension"
                val choice = JOptionPane.showConfirmDialog(dialog, "Are you sure you want to delete '$name'?", "Confirm Delete", JOptionPane.YES_NO_OPTION)
                if (choice == JOptionPane.YES_OPTION) {
                    deletePensionAtIndex(isSelf, selected)
                    refreshTable()
                    updatePensionButtonsText()
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select a pension to delete.", "Info", JOptionPane.WARNING_MESSAGE)
            }
        }
        
        closeBtn.addActionListener {
            dialog.dispose()
            recalc()
        }
        
        btnPanel.add(addBtn)
        btnPanel.add(editBtn)
        btnPanel.add(deleteBtn)
        btnPanel.add(closeBtn)
        dialog.add(btnPanel, BorderLayout.SOUTH)
        
        dialog.setSize(600, 350)
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
    
    private fun deletePensionAtIndex(isSelf: Boolean, index: Int) {
        val prefix = if (isSelf) "self" else "spouse"
        val count = pensionsConfig["pension_count_$prefix"]?.toIntOrNull() ?: 0
        
        for (i in index until count - 1) {
            val nextIdx = i + 1
            pensionsConfig["pension_name_${prefix}_$i"] = pensionsConfig["pension_name_${prefix}_$nextIdx"] ?: ""
            pensionsConfig["pension_start_age_${prefix}_$i"] = pensionsConfig["pension_start_age_${prefix}_$nextIdx"] ?: ""
            pensionsConfig["pension_schedule_${prefix}_$i"] = pensionsConfig["pension_schedule_${prefix}_$nextIdx"] ?: ""
            pensionsConfig["pension_locked_${prefix}_$i"] = pensionsConfig["pension_locked_${prefix}_$nextIdx"] ?: ""
        }
        
        val last = count - 1
        pensionsConfig.remove("pension_name_${prefix}_$last")
        pensionsConfig.remove("pension_start_age_${prefix}_$last")
        pensionsConfig.remove("pension_schedule_${prefix}_$last")
        pensionsConfig.remove("pension_locked_${prefix}_$last")
        
        pensionsConfig["pension_count_$prefix"] = last.toString()
    }

    private fun showPensionEditDialog(isSelf: Boolean, index: Int, onComplete: () -> Unit) {
        val prefix = if (isSelf) "self" else "spouse"
        val isEdit = index >= 0
        val dialog = JDialog(this, if (isEdit) "Edit Pension" else "Add Pension", true)
        dialog.layout = BorderLayout(10, 10)
        
        val topPanel = JPanel(GridBagLayout())
        topPanel.border = EmptyBorder(10, 10, 10, 10)
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        
        gbc.gridy = 0
        gbc.gridx = 0
        gbc.weightx = 0.3
        topPanel.add(JLabel("Pension Name:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 0.7
        val nameField = JTextField(20)
        if (isEdit) nameField.text = pensionsConfig["pension_name_${prefix}_$index"] ?: ""
        topPanel.add(nameField, gbc)
        
        gbc.gridy = 1
        gbc.gridx = 0
        gbc.weightx = 0.3
        topPanel.add(JLabel("Start Age:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 0.7
        val startAgeField = JTextField(10)
        if (isEdit) startAgeField.text = pensionsConfig["pension_start_age_${prefix}_$index"] ?: "65.0"
        topPanel.add(startAgeField, gbc)
        
        gbc.gridy = 2
        gbc.gridx = 0
        gbc.weightx = 0.3
        topPanel.add(JLabel("Lock Start Age:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 0.7
        val lockedCheck = JCheckBox("Lock during optimization")
        if (isEdit) lockedCheck.isSelected = pensionsConfig["pension_locked_${prefix}_$index"]?.toBoolean() ?: false
        topPanel.add(lockedCheck, gbc)
        
        dialog.add(topPanel, BorderLayout.NORTH)
        
        val schedCols = arrayOf("Age", "Monthly Benefit ($)")
        val schedModel = object : DefaultTableModel(schedCols, 0) {
            override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
        }
        val schedTable = JTable(schedModel)
        
        if (isEdit) {
            val schedStr = pensionsConfig["pension_schedule_${prefix}_$index"] ?: ""
            if (schedStr.isNotEmpty()) {
                val parts = schedStr.split(";")
                for (part in parts) {
                    val sub = part.split(":")
                    if (sub.size == 2) {
                        schedModel.addRow(arrayOf(sub[0], sub[1]))
                    }
                }
            }
        } else {
            schedModel.addRow(arrayOf("60.0", "1000.0"))
            schedModel.addRow(arrayOf("70.0", "1500.0"))
        }
        
        val tablePanel = JPanel(BorderLayout())
        tablePanel.border = TitledBorder("Benefit Schedule by Age")
        tablePanel.add(JScrollPane(schedTable), BorderLayout.CENTER)
        
        val gridBtns = JPanel(FlowLayout(FlowLayout.LEFT))
        val addPointBtn = JButton("Add Point")
        val delPointBtn = JButton("Delete Point")
        addPointBtn.addActionListener {
            schedModel.addRow(arrayOf("65.0", "1200.0"))
        }
        delPointBtn.addActionListener {
            val sel = schedTable.selectedRow
            if (sel >= 0) {
                schedModel.removeRow(sel)
            }
        }
        val pasteBtn = JButton("Paste Schedule")
        pasteBtn.toolTipText = "Puts schedule data from Excel, Google Sheets, or Markdown from clipboard"
        pasteBtn.addActionListener {
            pasteClipboardToScheduleTable(schedTable, schedModel)
        }
        gridBtns.add(addPointBtn)
        gridBtns.add(delPointBtn)
        gridBtns.add(pasteBtn)
        tablePanel.add(gridBtns, BorderLayout.SOUTH)
        
        schedTable.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.isControlDown && e.keyCode == KeyEvent.VK_V) {
                    pasteClipboardToScheduleTable(schedTable, schedModel)
                }
            }
        })
        
        tablePanel.add(gridBtns, BorderLayout.SOUTH)
        
        dialog.add(tablePanel, BorderLayout.CENTER)
        
        val dialogBtns = JPanel(FlowLayout(FlowLayout.RIGHT))
        val okBtn = JButton("OK")
        val cancelBtn = JButton("Cancel")
        
        okBtn.addActionListener {
            val name = nameField.text.trim()
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter a pension name.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }
            val startAge = startAgeField.text.toDoubleOrNull()
            if (startAge == null || startAge < 0.0) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid positive start age.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }
            
            val schedList = mutableListOf<Pair<Double, Double>>()
            for (r in 0 until schedModel.rowCount) {
                val ageCell = schedModel.getValueAt(r, 0)?.toString()?.toDoubleOrNull()
                val amtCell = schedModel.getValueAt(r, 1)?.toString()?.toDoubleOrNull()
                if (ageCell == null || ageCell < 0.0 || amtCell == null || amtCell < 0.0) {
                    JOptionPane.showMessageDialog(dialog, "Please ensure all schedule ages and benefit amounts are valid positive numbers.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                    return@addActionListener
                }
                schedList.add(Pair(ageCell, amtCell))
            }
            
            if (schedList.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please add at least one benefit point to the schedule.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }
            
            schedList.sortBy { it.first }
            val scheduleStr = schedList.joinToString(";") { "${it.first}:${it.second}" }
            
            val targetIdx = if (isEdit) index else pensionsConfig["pension_count_$prefix"]?.toIntOrNull() ?: 0
            pensionsConfig["pension_name_${prefix}_$targetIdx"] = name
            pensionsConfig["pension_start_age_${prefix}_$targetIdx"] = startAge.toString()
            pensionsConfig["pension_schedule_${prefix}_$targetIdx"] = scheduleStr
            pensionsConfig["pension_locked_${prefix}_$targetIdx"] = lockedCheck.isSelected.toString()
            
            if (!isEdit) {
                pensionsConfig["pension_count_$prefix"] = (targetIdx + 1).toString()
            }
            
            onComplete()
            dialog.dispose()
        }
        
        cancelBtn.addActionListener {
            dialog.dispose()
        }
        
        dialogBtns.add(okBtn)
        dialogBtns.add(cancelBtn)
        dialog.add(dialogBtns, BorderLayout.SOUTH)
        
        dialog.setSize(450, 450)
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }

    private fun pasteClipboardToScheduleTable(table: JTable, model: DefaultTableModel) {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null)
            if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                val text = contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
                val parsedRows = parsePastedPoints(text)
                if (parsedRows.isNotEmpty()) {
                    model.rowCount = 0
                    for (row in parsedRows) {
                        model.addRow(arrayOf(row.first.toString(), row.second.toString()))
                    }
                } else {
                    JOptionPane.showMessageDialog(table, "Could not find any valid numeric data in the clipboard to paste.\nFormat should be rows of: Age Value", "Paste Error", JOptionPane.ERROR_MESSAGE)
                }
            }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(table, "Failed to paste data: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private fun parsePastedPoints(text: String): List<Pair<Double, Double>> {
        val lines = text.split(Regex("[\r\n]+"))
        val points = mutableListOf<Pair<Double, Double>>()
        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isEmpty()) continue
            
            // Skip markdown header separators like |---|---|
            if (cleanLine.contains(Regex("^\\|?\\s*:?-+:?\\s*\\|\\s*:?-+:?\\s*\\|?"))) continue
            
            // Clean up thousands separators in numbers (e.g. 1,200 -> 1200)
            val processedLine = cleanLine.replace(Regex("(\\d),(\\d{3})"), "$1$2")
            
            // Split by tabs, commas, pipes, or general whitespace
            val parts = processedLine.split(Regex("[\t,\\|\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
            
            var num1: Double? = null
            var num2: Double? = null
            for (p in parts) {
                val cleanPart = p.replace("$", "").replace(",", "").replace("%", "")
                val d = cleanPart.toDoubleOrNull()
                if (d != null) {
                    if (num1 == null) {
                        num1 = d
                    } else if (num2 == null) {
                        num2 = d
                        break
                    }
                }
            }
            if (num1 != null && num2 != null) {
                points.add(Pair(num1, num2))
            }
        }
        return points
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
        val prefix = if (isIncome) "income" else "expense"
        val isEdit = editIndex >= 0
        val title = if (isEdit) {
            "Edit ${if (isIncome) "Income" else "Expense"} Event"
        } else {
            "Add ${if (isIncome) "Income" else "Expense"} Event"
        }

        val dialog = JDialog(this, title, true)
        dialog.layout = BorderLayout(10, 10)

        val formPanel = JPanel(GridBagLayout())
        formPanel.border = EmptyBorder(15, 15, 15, 15)
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)

        // 1. Name
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3
        formPanel.add(JLabel("Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        val nameField = JTextField(20)
        if (isEdit) nameField.text = eventsConfig["event_name_${prefix}_$editIndex"] ?: ""
        formPanel.add(nameField, gbc)

        // 2. Date
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3
        formPanel.add(JLabel("Date:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        val datePicker = com.moneydance.awt.JDateField(com.infinitekind.util.CustomDateFormat("yyyy-MM-dd"))
        if (isEdit) {
            val dStr = eventsConfig["event_date_${prefix}_$editIndex"] ?: ""
            if (dStr.contains("-")) {
                val p = dStr.split("-")
                if (p.size == 3) {
                    val y = p[0].toIntOrNull() ?: 2026
                    val m = p[1].toIntOrNull() ?: 1
                    val d = p[2].toIntOrNull() ?: 1
                    datePicker.dateInt = y * 10000 + m * 100 + d
                }
            }
        } else {
            val startYear = textFields["start_year"]?.text?.toIntOrNull() ?: 2026
            datePicker.dateInt = startYear * 10000 + 101
        }
        formPanel.add(datePicker, gbc)

        // 3. Type
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3
        formPanel.add(JLabel("Type:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        val types = if (isIncome) {
            arrayOf(
                "Bonus / Salary (Self)",
                "Bonus / Salary (Spouse)",
                "Taxable Ordinary Income",
                "Non-Taxable Income",
                "Traditional IRA Contribution",
                "Roth IRA Contribution",
                "Taxable Brokerage Contribution",
                "DAF Contribution",
                "NUA Transfer (IRA to Brokerage)"
            )
        } else {
            arrayOf(
                "General Expense",
                "Medical / Eldercare",
                "Traditional IRA Distribution",
                "Roth IRA Distribution",
                "Taxable Brokerage Distribution",
                "DAF Distribution"
            )
        }
        val typeCombo = JComboBox(types)
        if (isEdit) {
            val currentType = eventsConfig["event_type_${prefix}_$editIndex"]
            if (currentType != null) typeCombo.selectedItem = currentType
        }
        formPanel.add(typeCombo, gbc)

        // 4. Amount
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.3
        formPanel.add(JLabel("Amount:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        val amountField = JTextField(12)
        amountField.horizontalAlignment = JTextField.RIGHT
        if (isEdit) {
            val amt = eventsConfig["event_amount_${prefix}_$editIndex"]?.toDoubleOrNull() ?: 0.0
            amountField.text = formatDollar(amt)
        } else {
            amountField.text = "10000"
        }
        formPanel.add(amountField, gbc)

        // 5. Cost Basis (for NUA Transfer)
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.3
        val basisLabel = JLabel("Cost Basis (NUA):")
        formPanel.add(basisLabel, gbc)
        gbc.gridx = 1; gbc.weightx = 0.7
        val basisField = JTextField(12)
        basisField.horizontalAlignment = JTextField.RIGHT
        if (isEdit) {
            val basisVal = eventsConfig["event_basis_${prefix}_$editIndex"]?.toDoubleOrNull() ?: 0.0
            basisField.text = formatDollar(basisVal)
        } else {
            basisField.text = "0"
        }
        formPanel.add(basisField, gbc)

        fun updateNuaVisibility() {
            val isNua = isIncome && typeCombo.selectedItem?.toString() == "NUA Transfer (IRA to Brokerage)"
            basisLabel.isVisible = isNua
            basisField.isVisible = isNua
            dialog.pack()
        }
        typeCombo.addActionListener { updateNuaVisibility() }
        updateNuaVisibility()

        dialog.add(formPanel, BorderLayout.CENTER)

        val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val okBtn = JButton("OK")
        val cancelBtn = JButton("Cancel")
        okBtn.addActionListener {
            val name = nameField.text.trim()
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please enter an event name.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }
            val dInt = datePicker.dateInt
            if (dInt <= 0) {
                JOptionPane.showMessageDialog(dialog, "Please select a valid date.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }
            val amtClean = amountField.text.replace("$", "").replace(",", "").trim()
            val amt = amtClean.toDoubleOrNull()
            if (amt == null) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid amount.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }

            var basisAmt = 0.0
            val selectedType = typeCombo.selectedItem?.toString() ?: types[0]
            if (isIncome && selectedType == "NUA Transfer (IRA to Brokerage)") {
                val basisClean = basisField.text.replace("$", "").replace(",", "").trim()
                val parsedBasis = basisClean.toDoubleOrNull()
                if (parsedBasis == null || parsedBasis < 0.0) {
                    JOptionPane.showMessageDialog(dialog, "Please enter a valid cost basis for the NUA transfer.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                    return@addActionListener
                }
                if (parsedBasis > amt) {
                    JOptionPane.showMessageDialog(dialog, "Cost basis cannot exceed the total transfer amount.", "Validation Error", JOptionPane.ERROR_MESSAGE)
                    return@addActionListener
                }
                basisAmt = parsedBasis
            }

            val y = dInt / 10000
            val m = (dInt % 10000) / 100
            val d = dInt % 100
            val dateStr = String.format("%04d-%02d-%02d", y, m, d)

            val targetIdx = if (isEdit) editIndex else (eventsConfig["event_count_$prefix"]?.toIntOrNull() ?: 0)
            eventsConfig["event_name_${prefix}_$targetIdx"] = name
            eventsConfig["event_date_${prefix}_$targetIdx"] = dateStr
            eventsConfig["event_type_${prefix}_$targetIdx"] = selectedType
            eventsConfig["event_amount_${prefix}_$targetIdx"] = amt.toString()
            eventsConfig["event_basis_${prefix}_$targetIdx"] = basisAmt.toString()

            if (!isEdit) {
                eventsConfig["event_count_$prefix"] = (targetIdx + 1).toString()
            }

            refreshEventsTable(isIncome)
            dialog.dispose()
            recalc()
        }
        cancelBtn.addActionListener { dialog.dispose() }
        btnPanel.add(okBtn)
        btnPanel.add(cancelBtn)
        dialog.add(btnPanel, BorderLayout.SOUTH)

        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
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
            cell.font = cell.font.deriveFont(java.awt.Font.ITALIC)
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
        border = javax.swing.UIManager.getBorder("TableHeader.cellBorder") ?: javax.swing.border.LineBorder(Color.GRAY)
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
            else -> javax.swing.UIManager.getColor("TableHeader.background") ?: Color(240, 240, 240)
        }
        
        cell.background = bgColor
        cell.foreground = javax.swing.UIManager.getColor("TableHeader.foreground") ?: Color.BLACK
        cell.font = table.font.deriveFont(java.awt.Font.BOLD)
        
        return cell
    }
}

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

data class CombinationResult(
    val ssSelf: Double,
    val ssSpouse: Double,
    val pensionAges: Map<String, Double>,
    val successRate: Double,
    val medianSavings: Double
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
