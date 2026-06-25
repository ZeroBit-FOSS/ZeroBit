/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument

/**
 * Produces stable, Git-friendly source for new files and explicit formatting.
 */
object OpenMacroYamlWriter {
    fun write(document: OpenMacroDocument): String = buildString {
        append("format: ")
        appendQuoted(document.format)
        append("\n\nmetadata:\n")
        append("  id: ")
        appendQuoted(document.metadata.id)
        append("\n  name: ")
        appendQuoted(document.metadata.name)
        document.metadata.description?.let {
            append("\n  description: ")
            appendQuoted(it)
        }
        append("\n\n")
        if (document.variables.isNotEmpty()) {
            append("variables:\n")
            document.variables.forEach { variable ->
                append("  - name: ")
                appendQuoted(variable.name)
                append("\n    type: ")
                appendQuoted(variable.type.name.lowercase())
                variable.initialValue?.let {
                    append("\n    initial:")
                    appendValue(it, indent = 6)
                }
                variable.secretKey?.let {
                    append("\n    secret_key: ")
                    appendQuoted(it)
                    append("\n")
                }
                if (variable.initialValue == null && variable.secretKey == null) {
                    append("\n")
                }
            }
            append("\n")
        }

        appendBlocks("triggers", document.triggers)
        append("\n")
        appendBlocks("conditions", document.conditions)
        append("\n")
        appendBlocks("actions", document.actions)
    }

    private fun StringBuilder.appendBlocks(
        name: String,
        blocks: List<MacroBlock>,
    ) {
        if (blocks.isEmpty()) {
            append("$name: []\n")
            return
        }

        append("$name:\n")
        blocks.forEach { block ->
            append("  - id: ")
            appendQuoted(block.id)
            append("\n    type: ")
            appendQuoted(block.type)
            if (block.config.isNotEmpty()) {
                append("\n    config:\n")
                appendObject(block.config, indent = 6)
            } else {
                append("\n")
            }
        }
    }

    private fun StringBuilder.appendObject(
        values: Map<String, MacroValue>,
        indent: Int,
    ) {
        values.toSortedMap().forEach { (key, value) ->
            append(" ".repeat(indent))
            appendQuoted(key)
            append(":")
            appendValue(value, indent)
        }
    }

    private fun StringBuilder.appendValue(value: MacroValue, indent: Int) {
        when (value) {
            is MacroValue.Text -> {
                append(" ")
                appendQuoted(value.value)
                append("\n")
            }
            is MacroValue.Number -> append(" ${value.value.toPlainString()}\n")
            is MacroValue.Boolean -> append(" ${value.value}\n")
            is MacroValue.ListValue -> {
                if (value.values.isEmpty()) {
                    append(" []\n")
                } else {
                    append("\n")
                    value.values.forEach { child ->
                        append(" ".repeat(indent + 2))
                        append("-")
                        appendValue(child, indent + 2)
                    }
                }
            }
            is MacroValue.ObjectValue -> {
                if (value.values.isEmpty()) {
                    append(" {}\n")
                } else {
                    append("\n")
                    appendObject(value.values, indent + 2)
                }
            }
            MacroValue.Null -> append(" null\n")
        }
    }

    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }
}
