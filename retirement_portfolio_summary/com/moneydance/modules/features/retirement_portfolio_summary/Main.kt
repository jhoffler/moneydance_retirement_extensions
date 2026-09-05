package com.moneydance.modules.features.retirement_portfolio_summary

import com.moneydance.apps.md.controller.FeatureModule
import com.moneydance.apps.md.controller.FeatureModuleContext
import com.infinitekind.moneydance.model.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.border.EmptyBorder
import java.awt.*
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.awt.print.PageFormat
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.moneydance.awt.JDateField
import com.infinitekind.util.CustomDateFormat

class Main : FeatureModule() {
    private var summaryWindow: SummaryWindow? = null

    override fun init() {
        val bookContext = context
        try {
            bookContext?.registerFeature(this, "show_summary", null, getName())
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        }
    }

    override fun invoke(uri: String) {
        if (uri.startsWith("show_summary")) {
            showSummary()
        }
    }

    override fun cleanup() {
        closeConsole()
    }

    override fun getName(): String {
        return "Retirement Portfolio Summary"
    }

    @Synchronized
    private fun showSummary() {
        val bookContext = context
        bookContext?.showURL("moneydance:setprogress?label=Building Retirement Portfolio Summary...")
        
        try {
            if (summaryWindow == null) {
                val book = bookContext?.currentAccountBook
                if (book == null) {
                    JOptionPane.showMessageDialog(null, "No Moneydance dataset is currently open.", "Error", JOptionPane.ERROR_MESSAGE)
                    bookContext?.showURL("moneydance:setprogress?label=")
                    return
                }
                summaryWindow = SummaryWindow(this, book)
                summaryWindow?.isVisible = true
            } else {
                summaryWindow?.isVisible = true
                summaryWindow?.toFront()
                summaryWindow?.requestFocus()
                bookContext?.showURL("moneydance:setprogress?label=")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bookContext?.showURL("moneydance:setprogress?label=")
        }
    }

    @Synchronized
    fun closeConsole() {
        summaryWindow?.goAway()
        summaryWindow = null
        System.gc()
    }

    fun getUnprotectedContext(): FeatureModuleContext? {
        return context
    }
}

class AcctTypeFilter : AcctFilter() {
    private val myTypes = listOf(Account.AccountType.BANK, Account.AccountType.INVESTMENT)
    
    override fun format(acct: Account): String? {
        return acct.getAccountName()
    }
    
    override fun matches(acct: Account?): Boolean {
        if (acct == null) return false
        val acctType = acct.getAccountType()
        return acctType in myTypes && !acct.accountIsInactive
    }
}

fun getRecursiveBalanceAsOfDate(book: AccountBook, acct: Account, dateInt: Int): Long {
    val rawBal = AccountUtil.getBalanceAsOfDate(book, acct, dateInt)
    var bal = if (acct.getAccountType() == Account.AccountType.SECURITY) {
        val parent = acct.getParentAccount()
        val parentCurrency = parent?.currencyType ?: book.currencies.baseType
        CurrencyUtil.convertValue(rawBal, acct.currencyType, parentCurrency, dateInt)
    } else {
        rawBal
    }
    
    val subAccounts = acct.getSubAccounts()
    if (subAccounts != null) {
        for (subAcct in subAccounts) {
            bal += getRecursiveBalanceAsOfDate(book, subAcct, dateInt)
        }
    }
    return bal
}

fun getHistoricalSecurityCostBasis(book: AccountBook, subAcct: Account, dateInt: Int): Long {
    val actualShares = AccountUtil.getBalanceAsOfDate(book, subAcct, dateInt)
    if (actualShares == 0L) return 0L
    
    val today = java.time.LocalDate.now()
    val todayDateInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
    
    if (dateInt >= todayDateInt) {
        return InvestUtil.getCostBasis(subAcct)
    }
    
    try {
        val costCalc = InvestUtil.getCostCalculation(subAcct)
        costCalc.asOfDate = dateInt
        
        val sharesAndCostM = costCalc.javaClass.getMethod("getSharesAndCostBasisForAsOf")
        val sharesAndCost = sharesAndCostM.invoke(costCalc)
        if (sharesAndCost != null) {
            val scClass = sharesAndCost.javaClass
            val getSharesOwnedM = scClass.getMethod("getSharesOwnedAsOf")
            val getCostBasisAsOfM = scClass.getMethod("getCostBasisAsOf")
            
            val costCalcShares = getSharesOwnedM.invoke(sharesAndCost) as Long
            val costCalcCost = getCostBasisAsOfM.invoke(sharesAndCost) as Long
            
            if (costCalcShares <= 0L) {
                return 0L
            }
            val result = (costCalcCost.toDouble() * (actualShares.toDouble() / costCalcShares.toDouble())).toLong()
            return result
        }
    } catch (e: Exception) {
        return InvestUtil.getCostBasis(subAcct, dateInt)
    }
    return 0L
}


fun isMoneyMarketFund(subAcct: Account): Boolean {
    // 1. SecurityType override (CD)
    try {
        val secTypeM = subAcct.javaClass.getMethod("getSecurityType")
        val secType = secTypeM.invoke(subAcct)
        if (secType != null && secType.toString() == "CD") {
            return true
        }
    } catch (_: Throwable) {}

    // 2. Account Name matching
    val acctName = (subAcct.getAccountName() ?: "").lowercase()
    if (acctName.contains("money")) {
        return true
    }

    // 3. CurrencyType Name & Ticker matching
    try {
        val getCurrM = subAcct.javaClass.getMethod("getCurrencyType")
        val curr = getCurrM.invoke(subAcct)
        if (curr != null) {
            val getNameM = curr.javaClass.getMethod("getName")
            val currName = (getNameM.invoke(curr) as? String ?: "").lowercase()
            if (currName.contains("money")) {
                return true
            }
            val getTickerM = curr.javaClass.getMethod("getTickerSymbol")
            val ticker = (getTickerM.invoke(curr) as? String ?: "").trim().uppercase()
            if (ticker.length >= 4 && ticker.endsWith("XX")) {
                return true
            }
        }
    } catch (_: Throwable) {}

    return false
}

fun formatDateInt(dateInt: Int): String {
    try {
        val y = dateInt / 10000
        val m = (dateInt % 10000) / 100
        val d = dateInt % 100
        val date = java.time.LocalDate.of(y, m, d)
        return date.format(DateTimeFormatter.ofPattern("MMMM dd, YYYY"))
    } catch (e: Exception) {
        return dateInt.toString()
    }
}

class AccountDivider(private val book: AccountBook, private val dateInt: Int) {
    val roths = mutableListOf<Account>()
    val iras = mutableListOf<Account>()
    val taxables = mutableListOf<Account>()
    
    fun add(acct: Account) {
        if (getRecursiveBalanceAsOfDate(book, acct, dateInt) == 0L) return
        
        val nameLower = (acct.getAccountName() ?: "").lowercase()
        if (nameLower.contains("christopher") || nameLower.contains("nikki") || nameLower.contains("dafgiving")) {
            return
        }
        
        if (nameLower.contains("roth") || nameLower.contains("hsa")) {
            roths.add(acct)
        } else if (listOf("ira", "pension", "retirement", "401", "457", "tiaa").any { nameLower.contains(it) }) {
            iras.add(acct)
        } else {
            taxables.add(acct)
        }
    }
}

data class GroupSummary(
    val accounts: List<Account>,
    val total: Long,
    val cash: Long,
    val basis: Long
)

data class TableRowData(
    val name: String,
    val total: Long,
    val basis: Long,
    val gains: Long,
    val cash: Long
)

class ComponentPrinter(private val component: Component) : Printable {
    override fun print(graphics: Graphics, pageFormat: PageFormat, pageIndex: Int): Int {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE
        }
        val compWidth = component.width.toDouble()
        val compHeight = component.height.toDouble()
        val pageWidth = pageFormat.imageableWidth
        val pageHeight = pageFormat.imageableHeight
        
        var scaleX = pageWidth / compWidth
        var scaleY = pageHeight / compHeight
        var scale = Math.min(scaleX, scaleY)
        
        if (scale > 1.0) {
            scale = 1.0
        }
        
        val g2d = graphics as Graphics2D
        g2d.translate(pageFormat.imageableX, pageFormat.imageableY)
        g2d.scale(scale, scale)
        
        component.paint(g2d)
        
        return Printable.PAGE_EXISTS
    }
}

class SummaryWindow(private val extension: Main, private val book: AccountBook) : JFrame("Retirement Portfolio Summary") {
    private val dateField = JDateField(CustomDateFormat("yyyy-MM-dd"))
    private val reportPanel = JPanel()
    
    init {
        setupUI()
        
        defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                extension.closeConsole()
            }
        })
    }
    
    private fun getCostBasis(acct: Account, dateInt: Int): Long {
        if (acct.getAccountType() == Account.AccountType.INVESTMENT) {
            var totalInvestmentCostBasis = 0L
            val subAccounts = acct.getSubAccounts()
            if (subAccounts != null) {
                for (subAcct in subAccounts) {
                    if (subAcct.getAccountType() == Account.AccountType.SECURITY) {
                        val secCostBasis = getHistoricalSecurityCostBasis(book, subAcct, dateInt)
                        totalInvestmentCostBasis += secCostBasis
                    }
                }
            }
            return totalInvestmentCostBasis + AccountUtil.getBalanceAsOfDate(book, acct, dateInt)
        } else {
            return AccountUtil.getBalanceAsOfDate(book, acct, dateInt)
        }
    }

    private fun getCashBalance(acct: Account, dateInt: Int): Long {
        if (acct.getAccountType() == Account.AccountType.INVESTMENT) {
            var cash = AccountUtil.getBalanceAsOfDate(book, acct, dateInt)
            val subAccounts = acct.getSubAccounts()
            if (subAccounts != null) {
                for (subAcct in subAccounts) {
                    if (subAcct.getAccountType() == Account.AccountType.SECURITY && isMoneyMarketFund(subAcct)) {
                        cash += getRecursiveBalanceAsOfDate(book, subAcct, dateInt)
                    }
                }
            }
            return cash
        } else {
            return getRecursiveBalanceAsOfDate(book, acct, dateInt)
        }
    }
    
    private fun sortAndSum(grp: List<Account>, dateInt: Int): GroupSummary {
        val sorted = grp.sortedBy { it.getAccountName() ?: "" }
        var total = 0L
        var cash = 0L
        var basis = 0L
        for (acct in sorted) {
            total += getRecursiveBalanceAsOfDate(book, acct, dateInt)
            cash += getCashBalance(acct, dateInt)
            basis += getCostBasis(acct, dateInt)
        }
        return GroupSummary(sorted, total, cash, basis)
    }
    
    private fun isDarkTheme(): Boolean {
        val bg = UIManager.getColor("Panel.background")
        if (bg != null) {
            val luminance = (0.2126 * bg.red + 0.7152 * bg.green + 0.0722 * bg.blue) / 255.0
            return luminance < 0.5
        }
        return false
    }
    
    private fun formatCurrency(amount: Long): String {
        val df = DecimalFormat("$#,##0.00")
        return df.format(amount / 100.0)
    }
    
    private fun buildTable(rows: List<TableRowData>, nameWidth: Int, isGrandTotals: Boolean): JTable {
        val columnNames = arrayOf("Account Name", "Total Balance", "Cost Basis", "Gains", "Cash Balance")
        val model = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        
        for (row in rows) {
            model.addRow(arrayOf(
                row.name,
                formatCurrency(row.total),
                formatCurrency(row.basis),
                formatCurrency(row.gains),
                formatCurrency(row.cash)
            ))
        }
        
        val table = JTable(model)
        table.rowSelectionAllowed = false
        table.setShowGrid(true)
        table.gridColor = UIManager.getColor("Table.gridColor") ?: Color.LIGHT_GRAY
        
        class RowRenderer : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                tbl: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int
            ): Component {
                val cell = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col) as JLabel
                
                val isDark = isDarkTheme()
                val blueColor = if (isDark) Color(102, 178, 255) else Color(0, 102, 204)
                
                if (row == tbl.rowCount - 1) {
                    cell.font = cell.font.deriveFont(Font.BOLD)
                    if (isGrandTotals) {
                        cell.foreground = blueColor
                    } else {
                        cell.foreground = UIManager.getColor("Table.foreground")
                    }
                } else {
                    cell.font = cell.font.deriveFont(Font.PLAIN)
                    cell.foreground = UIManager.getColor("Table.foreground")
                }
                
                if (col == 0) {
                    cell.horizontalAlignment = SwingConstants.LEFT
                } else {
                    cell.horizontalAlignment = SwingConstants.RIGHT
                }
                return cell
            }
        }
        
        val renderer = RowRenderer()
        for (col in 0 until columnNames.size) {
            table.columnModel.getColumn(col).cellRenderer = renderer
        }
        
        table.columnModel.getColumn(0).preferredWidth = nameWidth
        for (col in 1..4) {
            table.columnModel.getColumn(col).preferredWidth = 95
        }
        
        return table
    }
    
    private fun createAccountTable(accounts: List<Account>, total: Long, cash: Long, basis: Long, nameWidth: Int, dateInt: Int): JTable {
        val rows = mutableListOf<TableRowData>()
        for (acct in accounts) {
            val bal = getRecursiveBalanceAsOfDate(book, acct, dateInt)
            val bs = getCostBasis(acct, dateInt)
            val gains = bal - bs
            val cashBal = getCashBalance(acct, dateInt)
            rows.add(TableRowData(
                acct.getAccountName() ?: "",
                bal,
                bs,
                gains,
                cashBal
            ))
        }
        
        val groupGains = total - basis
        rows.add(TableRowData(
            "TOTAL",
            total,
            basis,
            groupGains,
            cash
        ))
        
        return buildTable(rows, nameWidth, false)
    }
    
    private fun setupUI() {
        preferredSize = Dimension(700, 800)
        
        // Date control panel at the top
        val dateControlPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        dateControlPanel.border = EmptyBorder(5, 15, 5, 15)
        
        val dateLabel = JLabel("Effective Date:")
        dateLabel.font = Font("SansSerif", Font.BOLD, 12)
        
        dateField.preferredSize = Dimension(120, 26)
        
        val updateButton = JButton("Update")
        updateButton.font = Font("SansSerif", Font.BOLD, 12)
        
        val todayButton = JButton("Today")
        todayButton.font = Font("SansSerif", Font.BOLD, 12)
        
        val printButton = JButton("Print Report")
        printButton.font = Font("SansSerif", Font.BOLD, 12)
        printButton.addActionListener {
            val originalLf = UIManager.getLookAndFeel()
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
                SwingUtilities.updateComponentTreeUI(this)
                
                val job = PrinterJob.getPrinterJob()
                job.jobName = "Retirement Portfolio Summary"
                job.setPrintable(ComponentPrinter(reportPanel))
                if (job.printDialog()) {
                    job.print()
                }
            } catch (ex: Exception) {
                JOptionPane.showMessageDialog(this, "Error starting print job: " + ex.message, "Error", JOptionPane.ERROR_MESSAGE)
            } finally {
                if (originalLf != null) {
                    try {
                        UIManager.setLookAndFeel(originalLf)
                        SwingUtilities.updateComponentTreeUI(this)
                    } catch (e: Exception) {}
                }
            }
        }
        
        dateControlPanel.add(dateLabel)
        dateControlPanel.add(dateField)
        dateControlPanel.add(updateButton)
        dateControlPanel.add(todayButton)
        dateControlPanel.add(Box.createHorizontalStrut(15))
        dateControlPanel.add(printButton)
        
        fun triggerUpdate() {
            val dateInt = dateField.dateInt
            if (dateInt > 0) {
                updateReport(dateInt)
            } else {
                JOptionPane.showMessageDialog(this@SummaryWindow, "Please select or enter a valid date.", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
        
        updateButton.addActionListener { triggerUpdate() }
        todayButton.addActionListener {
            val t = java.time.LocalDate.now()
            val tInt = t.year * 10000 + t.monthValue * 100 + t.dayOfMonth
            dateField.dateInt = tInt
            updateReport(tInt)
        }
        
        reportPanel.layout = BoxLayout(reportPanel, BoxLayout.Y_AXIS)
        reportPanel.border = EmptyBorder(15, 15, 15, 15)
        
        val mainScroll = JScrollPane(reportPanel)
        mainScroll.verticalScrollBar.unitIncrement = 16
        
        contentPane.add(dateControlPanel, BorderLayout.NORTH)
        contentPane.add(mainScroll, BorderLayout.CENTER)
        
        preferredSize = Dimension(700, 600)
        minimumSize = Dimension(700, 600)
        
        // Initial setup for report with today's date
        val today = java.time.LocalDate.now()
        val todayDateInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        dateField.dateInt = todayDateInt
        updateReport(todayDateInt)
        
        setLocationRelativeTo(null)
    }
    
    private fun updateReport(dateInt: Int) {
        val bookContext = extension.getUnprotectedContext()
        bookContext?.showURL("moneydance:setprogress?label=Building Retirement Portfolio Summary...")
        
        Thread {
            try {
                val bookToUse = bookContext?.currentAccountBook ?: book
                val tSet = bookToUse.transactionSet
                val allAccounts = AccountUtil.allMatchesForSearch(bookToUse, AcctTypeFilter())
                
                val groups = AccountDivider(bookToUse, dateInt)
                if (allAccounts != null) {
                    for (acct in allAccounts) {
                        groups.add(acct)
                    }
                }
                
                val iraSummary = sortAndSum(groups.iras, dateInt)
                val rothSummary = sortAndSum(groups.roths, dateInt)
                val taxableSummary = sortAndSum(groups.taxables, dateInt)
                
                val grandTotal = iraSummary.total + rothSummary.total + taxableSummary.total
                val grandCash = iraSummary.cash + rothSummary.cash + taxableSummary.cash
                val grandBasis = iraSummary.basis + rothSummary.basis + taxableSummary.basis
                
                val tempTable = JTable()
                val fmPlain = tempTable.getFontMetrics(tempTable.font)
                val fmBold = tempTable.getFontMetrics(tempTable.font.deriveFont(Font.BOLD))
                
                fun checkWidth(text: String, isBold: Boolean): Int {
                    val fm = if (isBold) fmBold else fmPlain
                    return fm.stringWidth(text)
                }
                
                var maxNameWidth = checkWidth("Account Name", true)
                if (allAccounts != null) {
                    for (acct in allAccounts) {
                        maxNameWidth = maxNameWidth.coerceAtLeast(checkWidth(acct.getAccountName() ?: "", false))
                    }
                }
                
                val namesToCheck = listOf(
                    Pair("TOTAL", true),
                    Pair("IRA Account Total", false),
                    Pair("Roth Account Total", false),
                    Pair("Taxable Account Total", false),
                    Pair("Grand Total", true)
                )
                for (item in namesToCheck) {
                    maxNameWidth = maxNameWidth.coerceAtLeast(checkWidth(item.first, item.second))
                }
                
                val globalNameWidth = maxNameWidth + 20
                
                val iraTable = createAccountTable(iraSummary.accounts, iraSummary.total, iraSummary.cash, iraSummary.basis, globalNameWidth, dateInt)
                val rothTable = createAccountTable(rothSummary.accounts, rothSummary.total, rothSummary.cash, rothSummary.basis, globalNameWidth, dateInt)
                val taxableTable = createAccountTable(taxableSummary.accounts, taxableSummary.total, taxableSummary.cash, taxableSummary.basis, globalNameWidth, dateInt)
                
                val grandTotalsRows = listOf(
                    TableRowData("IRA Account Total", iraSummary.total, iraSummary.basis, iraSummary.total - iraSummary.basis, iraSummary.cash),
                    TableRowData("Roth Account Total", rothSummary.total, rothSummary.basis, rothSummary.total - rothSummary.basis, rothSummary.cash),
                    TableRowData("Taxable Account Total", taxableSummary.total, taxableSummary.basis, taxableSummary.total - taxableSummary.basis, taxableSummary.cash),
                    TableRowData("Grand Total", grandTotal, grandBasis, grandTotal - grandBasis, grandCash)
                )
                val grandTotalsTable = buildTable(grandTotalsRows, globalNameWidth, true)

                SwingUtilities.invokeLater {
                    reportPanel.removeAll()
                    
                    val effDateStr = formatDateInt(dateInt)
                    val titleLabel = JLabel("Retirement Portfolio Summary as of $effDateStr", SwingConstants.CENTER)
                    titleLabel.font = Font("SansSerif", Font.BOLD, 18)
                    titleLabel.alignmentX = Component.CENTER_ALIGNMENT
                    
                    val runTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, YYYY hh:mm a"))
                    val timeLabel = JLabel("Report Generated: $runTimeStr", SwingConstants.CENTER)
                    timeLabel.font = Font("SansSerif", Font.ITALIC, 10)
                    timeLabel.alignmentX = Component.CENTER_ALIGNMENT
                    
                    val titlePanel = JPanel()
                    titlePanel.layout = BoxLayout(titlePanel, BoxLayout.Y_AXIS)
                    titlePanel.isOpaque = false
                    titlePanel.alignmentX = Component.LEFT_ALIGNMENT
                    titlePanel.add(titleLabel)
                    titlePanel.add(Box.createVerticalStrut(4))
                    titlePanel.add(timeLabel)
                    
                    titlePanel.maximumSize = Dimension(32767, titleLabel.preferredSize.height + timeLabel.preferredSize.height + 20)
                    reportPanel.add(titlePanel)
                    reportPanel.add(Box.createVerticalStrut(20))
                    
                    fun addSection(title: String, table: JTable, nameWidth: Int) {
                        val sectionLabel = JLabel(title)
                        sectionLabel.font = Font("SansSerif", Font.BOLD, 14)
                        sectionLabel.alignmentX = Component.LEFT_ALIGNMENT
                        sectionLabel.maximumSize = Dimension(32767, sectionLabel.preferredSize.height)
                        reportPanel.add(sectionLabel)
                        reportPanel.add(Box.createVerticalStrut(5))
                        
                        val tablePanel = JPanel(BorderLayout())
                        tablePanel.alignmentX = Component.LEFT_ALIGNMENT
                        tablePanel.isOpaque = false
                        tablePanel.add(table.tableHeader, BorderLayout.NORTH)
                        tablePanel.add(table, BorderLayout.CENTER)
                        tablePanel.border = BorderFactory.createLineBorder(Color.LIGHT_GRAY)
                        
                        val rowHeight = table.rowHeight
                        val rowCount = table.model.rowCount
                        val tableHeight = rowCount * rowHeight
                        val headerHeight = table.tableHeader.preferredSize.height
                        val totalPaneHeight = tableHeight + headerHeight + 2
                        
                        val tableWidth = nameWidth + 380
                        
                        tablePanel.minimumSize = Dimension(tableWidth, totalPaneHeight)
                        tablePanel.preferredSize = Dimension(tableWidth, totalPaneHeight)
                        tablePanel.maximumSize = Dimension(tableWidth, totalPaneHeight)
                        
                        reportPanel.add(tablePanel)
                        reportPanel.add(Box.createVerticalStrut(20))
                    }
                    
                    addSection("Grand Totals", grandTotalsTable, globalNameWidth)
                    addSection("IRA Accounts", iraTable, globalNameWidth)
                    addSection("Roth Accounts", rothTable, globalNameWidth)
                    addSection("Taxable Accounts", taxableTable, globalNameWidth)
                    
                    reportPanel.revalidate()
                    reportPanel.repaint()
                    pack()
                    
                    bookContext?.showURL("moneydance:setprogress?label=")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                SwingUtilities.invokeLater {
                    bookContext?.showURL("moneydance:setprogress?label=")
                }
            }
        }.start()
    }
    
    fun goAway() {
        isVisible = false
        dispose()
    }
}
