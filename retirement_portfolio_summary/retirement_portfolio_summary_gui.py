#!/usr/bin/env python
"""
Moneydance Retirement Portfolio Summary GUI Script
This script queries your active Bank and Investment accounts, groups them by tax
categories (IRA, Roth, Taxable), and displays the results in a clean Swing GUI
table format.
"""

import sys
import platform
from java.lang import System
from javax.swing import (JFrame, JPanel, JLabel, JTable, JScrollPane, Box, 
                         BoxLayout, BorderFactory, SwingConstants, UIManager, SwingUtilities, JButton)
from javax.swing.table import DefaultTableModel, DefaultTableCellRenderer
from java.awt import Dimension, BorderLayout, Font, Color, GridBagLayout, GridBagConstraints, Insets
from java.awt.print import Printable, PrinterJob, PrinterException
from com.infinitekind.moneydance.model import AccountUtil, AcctFilter, Account, InvestUtil

# Accessing Moneydance API Globals
global moneydance
mdGUI = moneydance.getUI()
book = moneydance.getCurrentAccountBook()

class AcctTypeFilter(AcctFilter):
    """Filter to select active Bank and Investment accounts."""
    my_types = [
        Account.AccountType.BANK,
        Account.AccountType.INVESTMENT,
    ]
    def format(self, acct):
        return acct.getAccountName()
    def matches(self, aNacct):
        acctType = aNacct.getAccountType()
        return acctType in self.my_types and not aNacct.getAccountIsInactive()

class AccountDivider():
    """Groups accounts into IRA, Roth/HSA, or Taxable categories."""
    def __init__(self):
        self.roths = []
        self.iras = []
        self.taxables = []
    
    def add(self, acct):
        if acct.getRecursiveBalance() == 0:
            return
        
        name_lower = acct.getAccountName().lower()
        if "christopher" in name_lower or "nikki" in name_lower or "dafgiving" in name_lower:
            return
        
        if "roth" in name_lower or "hsa" in name_lower:
            self.roths.append(acct)
        elif any(keyword in name_lower for keyword in ["ira", "pension", "retirement", "401", "457", "tiaa"]):
            self.iras.append(acct)
        else:
            self.taxables.append(acct)

def getCostBasis(acct):
    """Calculates the cost basis of the account."""
    if acct.getAccountType() == Account.AccountType.INVESTMENT:
        total_investment_cost_basis = 0
        for sub_acct in acct.getSubAccounts():
            if sub_acct.getAccountType() == Account.AccountType.SECURITY:
                sec_cost_basis = InvestUtil.getCostBasis(sub_acct)
                total_investment_cost_basis += sec_cost_basis
        return total_investment_cost_basis + acct.getBalance()
    else:
        return acct.getBalance()

def sortAndSum(grp):
    """Sorts alphabetically and sums values."""
    grp.sort(key=lambda a: a.getAccountName())
    total = 0
    cash = 0
    basis = 0
    for acct in grp:
        total += acct.getRecursiveBalance()
        cash += acct.getBalance()
        basis += getCostBasis(acct)
    return grp, total, cash, basis

def isDarkTheme():
    """Detects whether the system is currently using a dark theme based on UI colors."""
    bg = UIManager.getColor("Panel.background")
    if bg is not None:
        luminance = (0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue()) / 255.0
        return luminance < 0.5
    return False

class ComponentPrinter(Printable):
    """Prints a Swing component by scaling it to fit both width and height on a single page."""
    def __init__(self, component):
        self.component = component
        
    def print_page(self, graphics, pageFormat, pageIndex):
        if pageIndex > 0:
            return Printable.NO_SUCH_PAGE
            
        comp_width = float(self.component.getWidth())
        comp_height = float(self.component.getHeight())
        page_width = float(pageFormat.getImageableWidth())
        page_height = float(pageFormat.getImageableHeight())
        
        # Calculate scaling factors to fit both width and height on a single page
        scale_x = page_width / comp_width
        scale_y = page_height / comp_height
        scale = min(scale_x, scale_y)
        
        # Prevent enlarging if the component is already smaller than the page area
        if scale > 1.0:
            scale = 1.0
            
        g2d = graphics
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY())
        g2d.scale(scale, scale)
        
        # Paint component
        self.component.paint(g2d)
        
        return Printable.PAGE_EXISTS

# Dynamically bind the print method to avoid Python parser keyword syntax conflicts in Moneydance
setattr(ComponentPrinter, 'print', ComponentPrinter.print_page)

def buildTable(rows, name_width, is_grand_totals=False):
    """Helper that creates a JTable with standard formatting, custom rendering, and sizing."""
    column_names = ["Account Name", "Total Balance", "Cost Basis", "Gains", "Cash Balance"]
    model = DefaultTableModel(column_names, 0)
    
    for row in rows:
        name, total_val, basis_val, gains_val, cash_val = row
        model.addRow([
            name,
            "${:,.2f}".format(float(total_val) / 100.0),
            "${:,.2f}".format(float(basis_val) / 100.0),
            "${:,.2f}".format(float(gains_val) / 100.0),
            "${:,.2f}".format(float(cash_val) / 100.0)
        ])
        
    table = JTable(model)
    table.setEnabled(False) # Make cell editing disabled
    table.setRowSelectionAllowed(False)
    table.setShowGrid(True)
    table.setGridColor(UIManager.getColor("Table.gridColor") or Color.LIGHT_GRAY)
    
    # Custom cell renderer for rendering the table rows
    class RowRenderer(DefaultTableCellRenderer):
        def getTableCellRendererComponent(self, tbl, value, isSelected, hasFocus, row, col):
            cell = DefaultTableCellRenderer.getTableCellRendererComponent(self, tbl, value, isSelected, hasFocus, row, col)
            
            # Select colors that contrast well on light vs dark theme for the Grand Total bold blue row
            is_dark = isDarkTheme()
            blue_color = Color(102, 178, 255) if is_dark else Color(0, 102, 204)
            
            # Check if this is the last row (TOTAL or Grand Total)
            if row == tbl.getRowCount() - 1:
                cell.setFont(cell.getFont().deriveFont(Font.BOLD))
                if is_grand_totals:
                    cell.setForeground(blue_color)
                else:
                    cell.setForeground(UIManager.getColor("Table.foreground"))
            else:
                cell.setFont(cell.getFont().deriveFont(Font.PLAIN))
                cell.setForeground(UIManager.getColor("Table.foreground"))
                
            # Alignment: Left for name, Right for numbers
            if col == 0:
                cell.setHorizontalAlignment(SwingConstants.LEFT)
            else:
                cell.setHorizontalAlignment(SwingConstants.RIGHT)
            return cell

    renderer = RowRenderer()
    for col in range(len(column_names)):
        table.getColumnModel().getColumn(col).setCellRenderer(renderer)
        
    # Set preferred column widths
    table.getColumnModel().getColumn(0).setPreferredWidth(name_width)
    for col in [1, 2, 3, 4]:
        table.getColumnModel().getColumn(col).setPreferredWidth(95)
        
    return table

def createAccountTable(accounts, total, cash, basis, name_width):
    """Creates a JTable populated with accounts and group totals using buildTable."""
    rows = []
    for acct in accounts:
        bal = acct.getRecursiveBalance()
        bs = getCostBasis(acct)
        gains = bal - bs
        rows.append((
            acct.getAccountName(),
            bal,
            bs,
            gains,
            acct.getBalance()
        ))
        
    # Append group totals
    group_gains = total - basis
    rows.append((
        "TOTAL",
        total,
        basis,
        group_gains,
        cash
    ))
    
    return buildTable(rows, name_width, is_grand_totals=False)

def runUI():
    if book is None:
        from javax.swing import JOptionPane
        JOptionPane.showMessageDialog(None, "No Moneydance dataset is currently open.", "Error", JOptionPane.ERROR_MESSAGE)
        return

    # Query and process data
    tSet = book.getTransactionSet()
    allAccounts = AccountUtil.allMatchesForSearch(book, AcctTypeFilter())
    groups = AccountDivider()
    for acct in allAccounts:
        groups.add(acct)
        
    # Summarize groups
    iras, iratotal, iracash, irabasis = sortAndSum(groups.iras)
    roths, rothtotal, rothcash, rothbasis = sortAndSum(groups.roths)
    taxables, taxabletotal, taxablecash, taxablebasis = sortAndSum(groups.taxables)
    
    # Grand Totals calculation
    grandtotal = iratotal + rothtotal + taxabletotal
    grandcash = iracash + rothcash + taxablecash
    grandbasis = irabasis + rothbasis + taxablebasis

    # Calculate global max_name_width across all tables (accounts, TOTAL, and grand totals)
    temp_table = JTable()
    fm_plain = temp_table.getFontMetrics(temp_table.getFont())
    fm_bold = temp_table.getFontMetrics(temp_table.getFont().deriveFont(Font.BOLD))
    
    def check_width(text, is_bold=False):
        if text:
            fm = fm_bold if is_bold else fm_plain
            return fm.stringWidth(str(text))
        return 0
        
    # Start with the width of the "Account Name" header as the absolute minimum width
    max_name_width = check_width("Account Name", True)
    
    for acct in allAccounts:
        max_name_width = max(max_name_width, check_width(acct.getAccountName(), False))
        
    names_to_check = [
        ("TOTAL", True),
        ("IRA Account Total", False),
        ("Roth Account Total", False),
        ("Taxable Account Total", False),
        ("Grand Total", True)
    ]
    for text, is_bold in names_to_check:
        max_name_width = max(max_name_width, check_width(text, is_bold))
        
    global_name_width = max_name_width + 20 # Add padding

    # Main Frame setup
    frame = JFrame("Retirement Portfolio Summary")
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
    frame.setPreferredSize(Dimension(700, 800))
    
    # Main content panel using Y_AXIS BoxLayout
    content_panel = JPanel()
    content_panel.setLayout(BoxLayout(content_panel, BoxLayout.Y_AXIS))
    content_panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15))
    
    # Title and human-friendly run date-time
    title_label = JLabel("Retirement Portfolio Summary", SwingConstants.CENTER)
    title_label.setFont(Font("SansSerif", Font.BOLD, 18))
    title_label.setAlignmentX(JLabel.CENTER_ALIGNMENT)
    
    from datetime import datetime
    run_time_str = datetime.now().strftime("%B %d, %Y %I:%M %p")
    time_label = JLabel("Report Generated: " + run_time_str, SwingConstants.CENTER)
    time_label.setFont(Font("SansSerif", Font.ITALIC, 11))
    time_label.setAlignmentX(JLabel.CENTER_ALIGNMENT)
    
    title_panel = JPanel()
    title_panel.setLayout(BoxLayout(title_panel, BoxLayout.Y_AXIS))
    title_panel.setOpaque(False)
    title_panel.setAlignmentX(JPanel.LEFT_ALIGNMENT)
    title_panel.add(title_label)
    title_panel.add(Box.createVerticalStrut(4))
    title_panel.add(time_label)
    
    title_panel.setMaximumSize(Dimension(32767, title_label.getPreferredSize().height + time_label.getPreferredSize().height + 15))
    content_panel.add(title_panel)
    content_panel.add(Box.createVerticalStrut(20))
    
    # Helper to create a section
    def addSection(title, table, name_width):
        section_label = JLabel(title)
        section_label.setFont(Font("SansSerif", Font.BOLD, 14))
        section_label.setAlignmentX(JLabel.LEFT_ALIGNMENT)
        content_panel.add(section_label)
        content_panel.add(Box.createVerticalStrut(5))
        
        scroll_pane = JScrollPane(table)
        scroll_pane.setAlignmentX(JScrollPane.LEFT_ALIGNMENT)
        
        # Disable scrollbars to achieve inline table display
        scroll_pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER)
        scroll_pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
        
        # Compute exact heights to display content without vertical scrolling
        row_height = table.getRowHeight()
        row_count = table.getModel().getRowCount()
        table_height = row_count * row_height
        header_height = table.getTableHeader().getPreferredSize().height
        total_pane_height = table_height + header_height + 4 # 4px margin for borders
        
        table_width = name_width + 380
        
        # Enforce exact bounds to prevent compression/scrolling
        scroll_pane.setMinimumSize(Dimension(table_width, total_pane_height))
        scroll_pane.setPreferredSize(Dimension(table_width, total_pane_height))
        scroll_pane.setMaximumSize(Dimension(32767, total_pane_height))
        
        content_panel.add(scroll_pane)
        content_panel.add(Box.createVerticalStrut(20))
        
    # Create the tables
    ira_table = createAccountTable(iras, iratotal, iracash, irabasis, global_name_width)
    roth_table = createAccountTable(roths, rothtotal, rothcash, rothbasis, global_name_width)
    taxable_table = createAccountTable(taxables, taxabletotal, taxablecash, taxablebasis, global_name_width)
    
    # Construct the fourth table for Grand Totals
    grand_totals_rows = [
        ("IRA Account Total", iratotal, irabasis, iratotal - irabasis, iracash),
        ("Roth Account Total", rothtotal, rothbasis, rothtotal - rothbasis, rothcash),
        ("Taxable Account Total", taxabletotal, taxablebasis, taxabletotal - taxablebasis, taxablecash),
        ("Grand Total", grandtotal, grandbasis, grandtotal - grandbasis, grandcash)
    ]
    grand_totals_table = buildTable(grand_totals_rows, global_name_width, is_grand_totals=True)
    
    # Add all four sections
    addSection("Grand Totals", grand_totals_table, global_name_width)
    addSection("IRA Accounts", ira_table, global_name_width)
    addSection("Roth Accounts", roth_table, global_name_width)
    addSection("Taxable Accounts", taxable_table, global_name_width)
    
    # Footer section displaying Dataset Path and Total Transactions
    footer_panel = JPanel(GridBagLayout())
    footer_panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5))
    footer_panel.setOpaque(False)
    footer_panel.setAlignmentX(JPanel.LEFT_ALIGNMENT)
    
    fc = GridBagConstraints()
    fc.fill = GridBagConstraints.HORIZONTAL
    fc.insets = Insets(2, 5, 2, 5)
    
    def addFooterRow(label_str, val_str, row_idx):
        fc.gridy = row_idx
        fc.gridx = 0
        fc.weightx = 0.25
        l = JLabel(label_str)
        l.setFont(Font("SansSerif", Font.BOLD, 10))
        footer_panel.add(l, fc)
        
        fc.gridx = 1
        fc.weightx = 0.75
        v = JLabel(val_str)
        v.setFont(Font("SansSerif", Font.PLAIN, 10))
        footer_panel.add(v, fc)
        
    addFooterRow("Dataset Path:", str(book.getRootFolder()), 0)
    addFooterRow("Total Transactions:", "{:,}".format(tSet.getTransactionCount()), 1)
    
    footer_panel.setMaximumSize(Dimension(32767, footer_panel.getPreferredSize().height))
    content_panel.add(footer_panel)
    
    # Wrap everything in a main JScrollPane in case the screen height is small
    main_scroll = JScrollPane(content_panel)
    main_scroll.getVerticalScrollBar().setUnitIncrement(16)
    
    # Print Button setup
    print_button = JButton("Print Report")
    print_button.setFont(Font("SansSerif", Font.BOLD, 12))
    
    def on_print(event):
        original_lf = UIManager.getLookAndFeel()
        try:
            # Temporarily switch to Cross-Platform L&F to avoid Windows L&F HTHEME printing bug
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
            SwingUtilities.updateComponentTreeUI(frame)
            
            job = PrinterJob.getPrinterJob()
            job.setJobName("Retirement Portfolio Summary")
            job.setPrintable(ComponentPrinter(content_panel))
            if job.printDialog():
                job.print()
        except Exception as ex:
            from javax.swing import JOptionPane
            JOptionPane.showMessageDialog(frame, "Error starting print job: " + str(ex), "Error", JOptionPane.ERROR_MESSAGE)
        finally:
            if original_lf:
                try:
                    UIManager.setLookAndFeel(original_lf)
                    SwingUtilities.updateComponentTreeUI(frame)
                except Exception:
                    pass
            
    print_button.addActionListener(on_print)
    
    control_panel = JPanel()
    control_panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
    control_panel.add(print_button)
    
    frame.getContentPane().add(main_scroll, BorderLayout.CENTER)
    frame.getContentPane().add(control_panel, BorderLayout.SOUTH)
    frame.pack()
    frame.setLocationRelativeTo(None) # Center on screen
    frame.setVisible(True)

# Run the UI on the Java Event Dispatch Thread
SwingUtilities.invokeLater(runUI)
