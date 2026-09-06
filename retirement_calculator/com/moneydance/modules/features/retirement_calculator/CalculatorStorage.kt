package com.moneydance.modules.features.retirement_calculator

import java.awt.Component
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.JOptionPane

object CalculatorStorage {

    fun serializeConfig(data: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("[")
        var first = true
        for ((k, v) in data) {
            if (!first) sb.append(",\n")
            first = false
            sb.append("[\"").append(k.replace("\"", "\\\"")).append("\",\"").append(v.replace("\"", "\\\"")).append("\"]")
        }
        sb.append("]")
        return sb.toString()
    }

    fun deserializeConfig(content: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("""\[\s*"(.*?)"\s*,\s*"(.*?)"\s*\]""")
        val matches = regex.findAll(content)
        for (m in matches) {
            val key = m.groupValues[1].replace("\\\"", "\"")
            val valStr = m.groupValues[2].replace("\\\"", "\"")
            map[key] = valStr
        }
        return map
    }

    fun saveConfigFile(parent: Component, data: Map<String, String>) {
        val fc = JFileChooser()
        if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fc.selectedFile
                val json = serializeConfig(data)
                Files.writeString(file.toPath(), json, StandardCharsets.UTF_8)
                JOptionPane.showMessageDialog(parent, "Configuration saved successfully.", "Success", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(parent, "Error saving config: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    fun openConfigFile(parent: Component, onLoaded: (Map<String, String>) -> Unit) {
        val fc = JFileChooser()
        if (fc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            loadConfigurationFromFile(parent, fc.selectedFile, onLoaded)
        }
    }

    fun loadConfigurationFromFile(parent: Component, file: File, onLoaded: (Map<String, String>) -> Unit) {
        try {
            val content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
            val map = deserializeConfig(content)
            onLoaded(map)
            JOptionPane.showMessageDialog(parent, "Configuration loaded successfully.", "Success", JOptionPane.INFORMATION_MESSAGE)
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Error loading config: " + e.message, "Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    fun saveConfigToLocalStorage(book: Any, data: Map<String, String>) {
        try {
            val json = serializeConfig(data)
            val localStorage = book.javaClass.getMethod("getLocalStorage").invoke(book)
            localStorage.javaClass.getMethod("put", String::class.java, String::class.java).invoke(localStorage, "retirement_calculator_config", json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreConfigFromLocalStorage(book: Any): Map<String, String>? {
        return try {
            val localStorage = book.javaClass.getMethod("getLocalStorage").invoke(book)
            val content = localStorage.javaClass.getMethod("get", String::class.java).invoke(localStorage, "retirement_calculator_config") as? String
            if (!content.isNullOrEmpty()) {
                val map = deserializeConfig(content).toMutableMap()
                map.remove("start_date")
                map.remove("start_year")
                map.remove("as_of_date")
                map
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
