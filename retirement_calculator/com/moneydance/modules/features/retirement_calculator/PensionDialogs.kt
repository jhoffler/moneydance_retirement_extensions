package com.moneydance.modules.features.retirement_calculator

import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

object PensionDialogs {

    fun getUnlockedPensionStartAgeKeys(pensionsConfig: Map<String, String>, isSelf: Boolean): List<String> {
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

    fun showPensionManagerDialog(
        parent: Component,
        isSelf: Boolean,
        pensionsConfig: MutableMap<String, String>,
        onUpdate: () -> Unit,
        onRecalc: () -> Unit
    ) {
        val prefix = if (isSelf) "self" else "spouse"
        val who = if (isSelf) "Self" else "Spouse"
        val frame = (parent as? Frame) ?: SwingUtilities.getWindowAncestor(parent) as? Frame
        val dialog = JDialog(frame, "Manage Pensions - $who", true)
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
                val name = pensionsConfig["pension_name_${prefix}_$i"] ?: "Pension ${i + 1}"
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
            showPensionEditDialog(dialog, isSelf, -1, pensionsConfig) {
                refreshTable()
                onUpdate()
            }
        }

        editBtn.addActionListener {
            val selected = managerTable.selectedRow
            if (selected >= 0) {
                showPensionEditDialog(dialog, isSelf, selected, pensionsConfig) {
                    refreshTable()
                    onUpdate()
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
                    deletePensionAtIndex(isSelf, selected, pensionsConfig)
                    refreshTable()
                    onUpdate()
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select a pension to delete.", "Info", JOptionPane.WARNING_MESSAGE)
            }
        }

        closeBtn.addActionListener {
            dialog.dispose()
            onRecalc()
        }

        btnPanel.add(addBtn)
        btnPanel.add(editBtn)
        btnPanel.add(deleteBtn)
        btnPanel.add(closeBtn)
        dialog.add(btnPanel, BorderLayout.SOUTH)

        dialog.setSize(600, 350)
        dialog.setLocationRelativeTo(parent)
        dialog.isVisible = true
    }

    fun deletePensionAtIndex(isSelf: Boolean, index: Int, pensionsConfig: MutableMap<String, String>) {
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

    fun showPensionEditDialog(
        parent: Component,
        isSelf: Boolean,
        index: Int,
        pensionsConfig: MutableMap<String, String>,
        onComplete: () -> Unit
    ) {
        val prefix = if (isSelf) "self" else "spouse"
        val isEdit = index >= 0
        val frame = (parent as? Frame) ?: SwingUtilities.getWindowAncestor(parent) as? Frame
        val dialog = JDialog(frame, if (isEdit) "Edit Pension" else "Add Pension", true)
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

            val targetIdx = if (isEdit) index else (pensionsConfig["pension_count_$prefix"]?.toIntOrNull() ?: 0)
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
        dialog.setLocationRelativeTo(parent)
        dialog.isVisible = true
    }

    fun pasteClipboardToScheduleTable(table: JTable, model: DefaultTableModel) {
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

    fun parsePastedPoints(text: String): List<Pair<Double, Double>> {
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
}
