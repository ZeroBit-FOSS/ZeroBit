/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroBlockEditorTest {
    @Test
    fun updatesNestedConditionWithoutChangingOtherBlocks() {
        val document = document()

        val result = MacroBlockEditor.updateConfig(
            document = document,
            blockId = "nested",
            key = "ssid",
            value = MacroValue.Text("Home"),
        )

        require(result is BlockEditResult.Updated)
        val root = result.document.conditionTree as MacroConditionNode.Not
        val nested = (root.child as MacroConditionNode.Condition).block
        assertEquals(MacroValue.Text("Home"), nested.config["ssid"])
        assertEquals(document.actions, result.document.actions)
    }

    @Test
    fun removesOptionalConfigKeyAndReportsMissingBlock() {
        val document = document().copy(
            actions = listOf(
                MacroBlock(
                    id = "log",
                    type = "android.log.write",
                    config = mapOf("message" to MacroValue.Text("hello")),
                ),
            ),
        )
        val removed = MacroBlockEditor.updateConfig(document, "log", "message", null)
        require(removed is BlockEditResult.Updated)
        assertTrue(removed.document.actions.single().config.isEmpty())

        assertEquals(
            BlockEditResult.NotFound("missing"),
            MacroBlockEditor.updateConfig(document, "missing", "message", null),
        )
    }

    private fun document() = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("editor", "Editor"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        conditionTree = MacroConditionNode.Not(
            MacroConditionNode.Condition(
                MacroBlock(
                    id = "nested",
                    type = "android.wifi.connected",
                ),
            ),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("hello")),
            ),
        ),
    )
}
