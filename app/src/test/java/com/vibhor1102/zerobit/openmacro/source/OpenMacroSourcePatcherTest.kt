/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMacroSourcePatcherTest {
    @Test
    fun replacesOneScalarWhilePreservingCommentsAndFormatting() {
        val source = """
            # Keep this file header.
            format: openmacro/v0.1
            metadata:
              id: patch-test
              name: Patch test
            triggers:
              - id: power
                type: android.power.connected
            actions:
              - id: log
                type: android.log.write
                config:
                  # Keep this explanation.
                  message: old value # Keep inline note.
        """.trimIndent()

        val result = OpenMacroSourcePatcher.replaceScalarConfig(
            sourceText = source,
            blockId = "log",
            key = "message",
            value = MacroValue.Text("new: value"),
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.startsWith("# Keep this file header."))
        assertTrue(result.sourceText.contains("# Keep this explanation."))
        assertTrue(result.sourceText.contains("\"new: value\" # Keep inline note."))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(
            MacroValue.Text("new: value"),
            parsed.source.document.actions.single().config["message"],
        )
    }

    @Test
    fun patchesScalarInsideNestedConditionTree() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-tree
              name: Patch tree
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              not:
                condition:
                  id: wifi
                  type: android.wifi.connected
                  config:
                    ssid: Guest
            actions:
              - id: log
                type: android.log.write
                config:
                  message: done
        """.trimIndent()

        val result = OpenMacroSourcePatcher.replaceScalarConfig(
            sourceText = source,
            blockId = "wifi",
            key = "ssid",
            value = MacroValue.Text("Home"),
        )

        require(result is SourcePatchResult.Success)
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        val tree = parsed.source.document.conditionTree as MacroConditionNode.Not
        val block = (tree.child as MacroConditionNode.Condition).block
        assertEquals(MacroValue.Text("Home"), block.config["ssid"])
    }

    @Test
    fun replacesCollectionValueWithCompactLocalPatch() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-test
              name: Patch test
            triggers:
              - id: power
                type: android.power.connected
            actions:
              - id: log
                type: android.log.write
                config:
                  message: old
        """.trimIndent()

        val result = OpenMacroSourcePatcher.replaceScalarConfig(
            source,
            "log",
            "message",
            MacroValue.ListValue(emptyList()),
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.contains("message: []"))
    }

    @Test
    fun addsAndRemovesConfigKeysWithoutReformattingOtherEntries() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-test
              name: Patch test
            triggers:
              - id: power
                type: android.power.connected
            actions:
              - id: log
                type: android.log.write
                config:
                  message: old # keep
        """.trimIndent()

        val added = OpenMacroSourcePatcher.setConfig(
            source,
            "log",
            "extra",
            MacroValue.ObjectValue(
                mapOf("variable" to MacroValue.Text("name")),
            ),
        )
        require(added is SourcePatchResult.Success)
        assertTrue(added.sourceText.contains("\"extra\": {\"variable\": \"name\"}"))
        assertTrue(added.sourceText.contains("message: old # keep"))

        val removed = OpenMacroSourcePatcher.setConfig(
            added.sourceText,
            "log",
            "message",
            null,
        )
        require(removed is SourcePatchResult.Success)
        assertTrue(!removed.sourceText.contains("message: old"))
        assertTrue(removed.sourceText.contains("\"extra\": {\"variable\": \"name\"}"))
    }

    @Test
    fun createsConfigObjectWhenBlockPreviouslyHadNone() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-test
              name: Patch test
            triggers:
              - id: power
                type: android.power.connected
            actions:
              - id: log
                type: android.log.write
        """.trimIndent()

        val result = OpenMacroSourcePatcher.setConfig(
            source,
            "log",
            "message",
            MacroValue.Text("created"),
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.contains("    config:\n      \"message\": \"created\""))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(
            MacroValue.Text("created"),
            parsed.source.document.actions.single().config["message"],
        )
    }

    @Test
    fun patchesVariableInitialValueWithoutReformattingDeclaration() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-variable
              name: Patch variable
            variables:
              - name: count
                type: number
                initial: 1 # keep
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()

        val result = OpenMacroSourcePatcher.setVariableField(
            source,
            "count",
            "initial",
            MacroValue.Number(java.math.BigDecimal("5")),
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.contains("initial: 5 # keep"))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(
            MacroValue.Number(java.math.BigDecimal("5")),
            parsed.source.document.variables.single().initialValue,
        )
    }
}
