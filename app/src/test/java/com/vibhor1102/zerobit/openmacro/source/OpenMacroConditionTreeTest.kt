/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMacroConditionTreeTest {
    @Test
    fun readsAndWritesNestedConditionTreeWithoutChangingItsMeaning() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: condition-tree
              name: Condition tree
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              all:
                - condition:
                    id: unlocked
                    type: android.device.unlocked
                - not:
                    condition:
                      id: wrong-wifi
                      type: android.wifi.connected
                      config:
                        ssid: Guest
            actions:
              - id: log
                type: android.log.write
                config:
                  message: Passed
        """.trimIndent()

        val first = OpenMacroYamlReader.read(yaml)
        require(first is OpenMacroSourceResult.Success)
        val tree = first.source.document.conditionTree
        require(tree is MacroConditionNode.All)
        assertEquals(2, tree.children.size)
        assertEquals("unlocked", (tree.children[0] as MacroConditionNode.Condition).block.id)
        assertEquals(
            "wrong-wifi",
            ((tree.children[1] as MacroConditionNode.Not).child as MacroConditionNode.Condition).block.id,
        )

        val written = OpenMacroYamlWriter.write(first.source.document)
        val second = OpenMacroYamlReader.read(written)
        require(second is OpenMacroSourceResult.Success)

        assertEquals(first.source.document, second.source.document)
    }

    @Test
    fun rejectsEmptyConditionGroup() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: condition-tree
              name: Condition tree
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              any: []
            actions:
              - id: log
                type: android.log.write
                config:
                  message: Passed
        """.trimIndent()

        val result = OpenMacroYamlReader.read(yaml)

        require(result is OpenMacroSourceResult.Failure)
        assertEquals("empty_condition_group", result.issues.single().code)
    }

    @Test
    fun rejectsConditionListKeyAlongsideConditionTreeEvenWhenListIsEmpty() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: condition-tree
              name: Condition tree
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            condition_tree:
              condition:
                id: unlocked
                type: android.device.unlocked
            actions:
              - id: log
                type: android.log.write
                config:
                  message: Passed
        """.trimIndent()

        val result = OpenMacroYamlReader.read(yaml)

        require(result is OpenMacroSourceResult.Failure)
        assertEquals("mixed_condition_forms", result.issues.single().code)
    }
}
