#!/usr/bin/env python
"""
Moneydance Securities Summary GUI Script
This script displays a table of all securities defined in your dataset,
including the number of historical price entries, current price, and
any accounts with transaction counts involving each security.
"""

import sys
import platform
from datetime import datetime
from java.lang import System
from javax.swing import (JFrame, JPanel, JLabel, JTable, JScrollPane, Box, 
                         BoxLayout, BorderFactory, SwingConstants, UIManager, SwingUtilities, JButton)
from javax.swing.table import DefaultTableModel, DefaultTableCellRenderer
from java.awt import Dimension, BorderLayout, Font, Color, GridBagLayout, GridBagConstraints, Insets
from java.awt.print import Printable, PrinterJob, PrinterException
from com.infinitekind.moneydance.model import Account, CurrencyType, CurrencyUtil

# Accessing Moneydance API Globals
global moneydance
mdGUI = moneydance.getUI()
book = moneydance.getCurrentAccountBook()

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

def isDarkTheme():
    """Detects whether the system is currently using a dark theme based on UI colors."""
    bg = UIManager.getColor("Panel.background")
    if bg is not None:
        luminance = (0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue()) / 255.0
        return luminance < 0.5
    return False

def buildTable(rows, name_width, acct_width):
    """Creates a configured JTable showing securities details with color-coded rows."""
    column_names = [
        "Security Name", 
        "Historical Prices", 
        "Current Price", 
        "Last Updated",
        "Accounts (Transactions)"
    ]
    model = DefaultTableModel(column_names, 0)
    
    for row in rows:
        name, hist_count, price_str, last_updated_str, accts_str = row
        model.addRow([
            name,
            str(hist_count),
            price_str,
            last_updated_str,
            accts_str
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
            cell.setFont(cell.getFont().deriveFont(Font.PLAIN))
            cell.setForeground(UIManager.getColor("Table.foreground"))
            
            # Retrieve color coding parameters
            hist_count_str = tbl.getModel().getValueAt(row, 1)
            accts_str = tbl.getModel().getValueAt(row, 4)
            
            hist_count = int(hist_count_str) if hist_count_str.isdigit() else 0
            has_accts = (accts_str != "-") and (accts_str != "")
            
            is_dark = isDarkTheme()
            
            # Apply color coding rules
            if has_accts:
                # 3) Security has account transactions -> Red
                bg_color = Color(80, 20, 20) if is_dark else Color(255, 204, 204)
            elif hist_count > 0:
                # 2) Security has no accounts, but does have historical prices -> Yellow
                bg_color = Color(70, 60, 15) if is_dark else Color(255, 255, 204)
            else:
                # 1) Security is in no accounts and has no historical prices -> Green
                bg_color = Color(15, 50, 15) if is_dark else Color(204, 255, 204)
                
            cell.setBackground(bg_color)
            
            # Alignment: Left for text columns, Right for numeric columns
            if col in [0, 4]:
                cell.setHorizontalAlignment(SwingConstants.LEFT)
            else:
                cell.setHorizontalAlignment(SwingConstants.RIGHT)
            return cell

    renderer = RowRenderer()
    for col in range(len(column_names)):
        table.getColumnModel().getColumn(col).setCellRenderer(renderer)
        
    # Set preferred column widths
    table.getColumnModel().getColumn(0).setPreferredWidth(name_width)
    table.getColumnModel().getColumn(1).setPreferredWidth(110)
    table.getColumnModel().getColumn(2).setPreferredWidth(95)
    table.getColumnModel().getColumn(3).setPreferredWidth(100)
    table.getColumnModel().getColumn(4).setPreferredWidth(acct_width)
    
    # Adjust scroll view port size to display all rows without inner scrolling
    row_count = model.getRowCount()
    row_height = table.getRowHeight()
    table_height = row_count * row_height
    table_width = name_width + 110 + 95 + 100 + acct_width
    table.setPreferredScrollableViewportSize(Dimension(table_width, table_height))
    
    return table

def runUI():
    if book is None:
        from javax.swing import JOptionPane
        JOptionPane.showMessageDialog(None, "No Moneydance dataset is currently open.", "Error", JOptionPane.ERROR_MESSAGE)
        return

    # Cache object to share variables across callbacks and closures
    ui_elements = {}
    txn_set = book.getTransactionSet()

    def fetch_data_and_update():
        # 1. Map securities to transactions and accounts
        security_txns = {} # currency_id -> { parent_account_name -> count }
        
        for txn in txn_set.iterableTxns():
            acct = txn.getAccount()
            if acct and acct.getAccountType() == Account.AccountType.SECURITY:
                sec = acct.getCurrencyType()
                if sec:
                    sec_id = sec.getIDString()
                    parent_acct = acct.getParentAccount()
                    parent_name = parent_acct.getAccountName() if parent_acct else "Unknown"
                    
                    if sec_id not in security_txns:
                        security_txns[sec_id] = {}
                    if parent_name not in security_txns[sec_id]:
                        security_txns[sec_id][parent_name] = 0
                    security_txns[sec_id][parent_name] += 1

        # 2. Query all securities from CurrencyTable
        currency_table = book.getCurrencies()
        base_curr = currency_table.getBaseType()
        
        rows = []
        
        for cur in currency_table.getAllCurrencies():
            if cur.getCurrencyType() == CurrencyType.Type.SECURITY:
                name = cur.getName()
                ticker = cur.getTickerSymbol()
                display_name = "%s (%s)" % (name, ticker) if ticker else name
                
                # Number of historical prices
                hist_count = len(cur.getSnapshots())
                
                # Current price relative to relative currency or base currency
                rel_curr = cur.getRelativeCurrency() or base_curr
                price = CurrencyUtil.getUserRate(cur, rel_curr)
                prefix = rel_curr.getPrefix() or ""
                suffix = rel_curr.getSuffix() or ""
                
                # Formatted current price
                price_str = "%s%s%s" % (prefix, "{:,.4f}".format(price), suffix)
                
                # Last updated date
                snapshots = cur.getSnapshots()
                if snapshots:
                    last_snap = max(snapshots, key=lambda s: s.getDateInt())
                    date_int = last_snap.getDateInt()
                    year = date_int // 10000
                    month = (date_int % 10000) // 100
                    day = date_int % 100
                    last_updated_str = "%04d-%02d-%02d" % (year, month, day)
                else:
                    last_updated_str = "-"
                
                # Accounts and transaction counts
                sec_id = cur.getIDString()
                acct_list = []
                if sec_id in security_txns:
                    for parent_name, count in sorted(security_txns[sec_id].items()):
                        acct_list.append("%s (%d)" % (parent_name, count))
                
                accts_str = ", ".join(acct_list) if acct_list else "-"
                
                rows.append((display_name, hist_count, price_str, last_updated_str, accts_str))
                
        # Sort securities alphabetically by name
        rows.sort(key=lambda r: r[0].lower())

        # 3. Calculate preferred widths using FontMetrics
        temp_table = JTable()
        fm_plain = temp_table.getFontMetrics(temp_table.getFont())
        
        max_name_width = fm_plain.stringWidth("Security Name")
        max_accts_width = fm_plain.stringWidth("Accounts (Transactions)")
        
        for row in rows:
            max_name_width = max(max_name_width, fm_plain.stringWidth(row[0]))
            max_accts_width = max(max_accts_width, fm_plain.stringWidth(row[4]))
            
        # Add safety margin padding and cap maximum size for accounts list
        max_name_width = max_name_width + 20
        max_accts_width = min(max_accts_width + 20, 450)
        max_accts_width = max(max_accts_width, 180)

        # 4. Populate table model if table exists, otherwise build it
        if 'table' in ui_elements:
            table = ui_elements['table']
            model = table.getModel()
            model.setRowCount(0)
            for row in rows:
                model.addRow([
                    row[0],
                    str(row[1]),
                    row[2],
                    row[3],
                    row[4]
                ])
                
            # Update columns width
            table.getColumnModel().getColumn(0).setPreferredWidth(max_name_width)
            table.getColumnModel().getColumn(1).setPreferredWidth(110)
            table.getColumnModel().getColumn(2).setPreferredWidth(95)
            table.getColumnModel().getColumn(3).setPreferredWidth(100)
            table.getColumnModel().getColumn(4).setPreferredWidth(max_accts_width)
            
            # Recalculate viewport size
            row_count = model.getRowCount()
            row_height = table.getRowHeight()
            table_height = row_count * row_height
            header_height = table.getTableHeader().getPreferredSize().height
            total_pane_height = table_height + header_height + 4
            table_width = max_name_width + 110 + 95 + 100 + max_accts_width
            
            table.setPreferredScrollableViewportSize(Dimension(table_width, table_height))
            
            scroll_pane = ui_elements['scroll_pane']
            scroll_pane.setMinimumSize(Dimension(table_width, total_pane_height))
            scroll_pane.setPreferredSize(Dimension(table_width, total_pane_height))
            scroll_pane.setMaximumSize(Dimension(32767, total_pane_height))
            
            # Re-fetch new run time and update label
            run_time_str = datetime.now().strftime("%B %d, %Y %I:%M %p")
            ui_elements['time_label'].setText("Report Generated: " + run_time_str)
            
            # Update transactions count in footer
            ui_elements['txn_label'].setText("{:,}".format(txn_set.getTransactionCount()))
            
            # Revalidate and pack frame to fit newly sized content
            ui_elements['frame'].pack()
            ui_elements['frame'].revalidate()
            ui_elements['frame'].repaint()
            
        return rows, max_name_width, max_accts_width

    # Initial data query
    rows, max_name_width, max_accts_width = fetch_data_and_update()

    # Main Frame setup
    frame = JFrame("Securities Summary")
    ui_elements['frame'] = frame
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE)
    frame.setPreferredSize(Dimension(800, 700))
    
    # Main content panel
    content_panel = JPanel()
    content_panel.setLayout(BoxLayout(content_panel, BoxLayout.Y_AXIS))
    content_panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15))
    
    # Title and human-friendly run date-time
    title_label = JLabel("Securities Summary", SwingConstants.CENTER)
    title_label.setFont(Font("SansSerif", Font.BOLD, 18))
    title_label.setAlignmentX(JLabel.CENTER_ALIGNMENT)
    
    run_time_str = datetime.now().strftime("%B %d, %Y %I:%M %p")
    time_label = JLabel("Report Generated: " + run_time_str, SwingConstants.CENTER)
    time_label.setFont(Font("SansSerif", Font.ITALIC, 11))
    time_label.setAlignmentX(JLabel.CENTER_ALIGNMENT)
    ui_elements['time_label'] = time_label
    
    title_panel = JPanel()
    title_panel.setLayout(BoxLayout(title_panel, BoxLayout.Y_AXIS))
    title_panel.setOpaque(False)
    title_panel.setAlignmentX(JPanel.LEFT_ALIGNMENT)
    title_panel.add(title_label)
    title_panel.add(Box.createVerticalStrut(4))
    title_panel.add(time_label)
    
    title_panel.setMaximumSize(Dimension(32767, title_label.getPreferredSize().height + time_label.getPreferredSize().height + 15))
    content_panel.add(title_panel)
    content_panel.add(Box.createVerticalStrut(15))
    
    # Table viewport setup
    table = buildTable(rows, max_name_width, max_accts_width)
    ui_elements['table'] = table
    
    scroll_pane = JScrollPane(table)
    ui_elements['scroll_pane'] = scroll_pane
    scroll_pane.setAlignmentX(JScrollPane.LEFT_ALIGNMENT)
    scroll_pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER)
    scroll_pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
    
    # Enforce exact height of table to avoid inner scrollbar or empty space
    row_height = table.getRowHeight()
    row_count = table.getModel().getRowCount()
    table_height = row_count * row_height
    header_height = table.getTableHeader().getPreferredSize().height
    total_pane_height = table_height + header_height + 4
    
    table_width = max_name_width + 110 + 95 + 100 + max_accts_width
    scroll_pane.setMinimumSize(Dimension(table_width, total_pane_height))
    scroll_pane.setPreferredSize(Dimension(table_width, total_pane_height))
    scroll_pane.setMaximumSize(Dimension(32767, total_pane_height))
    
    content_panel.add(scroll_pane)
    content_panel.add(Box.createVerticalStrut(20))
    
    # Footer section displaying Dataset Path and Total Transactions
    footer_panel = JPanel(GridBagLayout())
    footer_panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5))
    footer_panel.setOpaque(False)
    footer_panel.setAlignmentX(JPanel.LEFT_ALIGNMENT)
    
    fc = GridBagConstraints()
    fc.fill = GridBagConstraints.HORIZONTAL
    fc.insets = Insets(2, 5, 2, 5)
    
    txn_label = JLabel("{:,}".format(txn_set.getTransactionCount()))
    ui_elements['txn_label'] = txn_label
    
    def addFooterRow(label_str, val_component, row_idx):
        fc.gridy = row_idx
        fc.gridx = 0
        fc.weightx = 0.25
        l = JLabel(label_str)
        l.setFont(Font("SansSerif", Font.BOLD, 10))
        footer_panel.add(l, fc)
        
        fc.gridx = 1
        fc.weightx = 0.75
        val_component.setFont(Font("SansSerif", Font.PLAIN, 10))
        footer_panel.add(val_component, fc)
        
    addFooterRow("Dataset Path:", JLabel(str(book.getRootFolder())), 0)
    addFooterRow("Total Transactions:", txn_label, 1)
    
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
            job.setJobName("Securities Summary")
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
    
    # Refresh Button setup
    refresh_button = JButton("Refresh")
    refresh_button.setFont(Font("SansSerif", Font.BOLD, 12))
    
    def on_refresh(event):
        fetch_data_and_update()
        
    refresh_button.addActionListener(on_refresh)
    
    control_panel = JPanel()
    control_panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
    control_panel.add(print_button)
    control_panel.add(Box.createHorizontalStrut(15))
    control_panel.add(refresh_button)
    
    frame.getContentPane().add(main_scroll, BorderLayout.CENTER)
    frame.getContentPane().add(control_panel, BorderLayout.SOUTH)
    frame.pack()
    frame.setLocationRelativeTo(None) # Center on screen
    frame.setVisible(True)

# Run the UI on the Java Event Dispatch Thread
SwingUtilities.invokeLater(runUI)
