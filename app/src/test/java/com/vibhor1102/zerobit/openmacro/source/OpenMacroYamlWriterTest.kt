/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.validation.OpenMacroValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMacroYamlWriterTest {
    @Test
    fun writesStableSourceThatReadsBackWithoutMeaningChanges() {
        val document = document()

        val first = OpenMacroYamlWriter.write(document)
        val second = OpenMacroYamlWriter.write(document)
        val parsed = OpenMacroYamlReader.read(first)

        assertEquals(first, second)
        assertTrue(first.endsWith("\n"))
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(document, parsed.source.document)
    }

    @Test
    fun quotesEveryStringSoYamlLookingTextStaysText() {
        val document = document().copy(
            metadata = MacroMetadata(
                id = "quoted-text",
                name = "yes",
                description = "Line one\nLine \"two\"",
            ),
        )

        val yaml = OpenMacroYamlWriter.write(document)
        val parsed = OpenMacroYamlReader.read(yaml)

        assertTrue(yaml.contains("name: \"yes\""))
        assertTrue(yaml.contains("description: \"Line one\\nLine \\\"two\\\"\""))
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(document, parsed.source.document)
    }

    private fun document() = OpenMacroDocument(
        format = OpenMacroValidator.SUPPORTED_FORMAT,
        metadata = MacroMetadata(
            id = "charger-greeting",
            name = "Charger greeting",
        ),
        triggers = listOf(
            MacroBlock(
                id = "charger-connected",
                type = "android.power.connected",
            ),
        ),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "show-message",
                type = "android.notification.show",
                config = linkedMapOf(
                    "title" to MacroValue.Text("Charging started"),
                    "message" to MacroValue.Text("The charger is connected."),
                    "priority" to MacroValue.Number("1.50".toBigDecimal()),
                    "silent" to MacroValue.Boolean(false),
                    "metadata" to MacroValue.ObjectValue(
                        mapOf("source" to MacroValue.Text("ZeroBit")),
                    ),
                    "labels" to MacroValue.ListValue(
                        listOf(
                            MacroValue.Text("home"),
                            MacroValue.Null,
                        ),
                    ),
                ),
            ),
        ),
    )
}
