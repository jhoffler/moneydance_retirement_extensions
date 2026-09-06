package com.moneydance.modules.features.retirement_calculator

import com.infinitekind.util.CustomDateFormat
import com.moneydance.awt.JDateField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

object FinancialEventDialogs {

    fun showFinancialEventDialog(
        parent: Component,
        isIncome: Boolean,
        editIndex: Int,
        eventsConfig: MutableMap<String, String>,
        defaultStartYear: Int,
        onSave: () -> Unit
    ) {
        val prefix = if (isIncome) "income" else "expense"
        val isEdit = editIndex >= 0
        val title = if (isEdit) {
            "Edit ${if (isIncome) "Income" else "Expense"} Event"
        } else {
            "Add ${if (isIncome) "Income" else "Expense"} Event"
        }

        val frame = (parent as? Frame) ?: SwingUtilities.getWindowAncestor(parent) as? Frame
        val dialog = JDialog(frame, title, true)
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
        val datePicker = JDateField(CustomDateFormat("yyyy-MM-dd"))
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
            datePicker.dateInt = defaultStartYear * 10000 + 101
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

            onSave()
            dialog.dispose()
        }
        cancelBtn.addActionListener { dialog.dispose() }
        btnPanel.add(okBtn)
        btnPanel.add(cancelBtn)
        dialog.add(btnPanel, BorderLayout.SOUTH)

        dialog.pack()
        dialog.setLocationRelativeTo(parent)
        dialog.isVisible = true
    }
}
