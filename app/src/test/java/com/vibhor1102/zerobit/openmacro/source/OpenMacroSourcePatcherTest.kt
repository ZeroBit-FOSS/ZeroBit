/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.ConditionGroupLogic
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun removesVariableDeclarationWithoutReformattingOtherDeclarations() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-variable-remove
              name: Patch variable remove
            variables:
              - name: unused
                type: text
                initial: safe # remove this one
              - name: kept
                type: number
                initial: 1 # keep this note
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()

        val result = OpenMacroSourcePatcher.removeVariableDeclaration(source, "unused")

        require(result is SourcePatchResult.Success)
        assertTrue(!result.sourceText.contains("name: unused"))
        assertTrue(result.sourceText.contains("initial: 1 # keep this note"))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(listOf("kept"), parsed.source.document.variables.map { it.name })
    }

    @Test
    fun removesVariablesSectionWhenLastDeclarationIsRemoved() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-variable-remove-last
              name: Patch variable remove last
            variables:
              - name: only
                type: boolean
                initial: false
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()

        val result = OpenMacroSourcePatcher.removeVariableDeclaration(source, "only")

        require(result is SourcePatchResult.Success)
        assertTrue(!result.sourceText.contains("variables:"))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertTrue(parsed.source.document.variables.isEmpty())
    }

    @Test
    fun renamesVariableDeclarationAndReferencesWithoutReformattingComments() {
        val source = """
            # keep file comment
            format: openmacro/v0.1
            metadata:
              id: patch-variable-rename
              name: Patch variable rename
            variables:
              - name: message # keep name note
                type: text
                initial: hello
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: set-message
                type: openmacro.variable.set
                config:
                  name: message # keep target note
                  value: next
              - id: log-message
                type: android.log.write
                config:
                  message:
                    variable: message # keep reference note
        """.trimIndent()

        val result = OpenMacroSourcePatcher.renameVariable(
            source,
            oldName = "message",
            newName = "status_text",
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.startsWith("# keep file comment"))
        assertTrue(result.sourceText.contains("name: \"status_text\" # keep name note"))
        assertTrue(result.sourceText.contains("name: \"status_text\" # keep target note"))
        assertTrue(result.sourceText.contains("variable: \"status_text\" # keep reference note"))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(listOf("status_text"), parsed.source.document.variables.map { it.name })
    }

    @Test
    fun switchesConditionGroupKeyWithoutReformattingChildren() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-condition-group
              name: Patch condition group
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              all: # keep root note
                - condition:
                    id: unlocked
                    type: android.device.unlocked
                - any: # keep nested note
                    - condition:
                        id: wifi
                        type: android.wifi.connected
                        config:
                          ssid: Guest # keep child note
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()

        val rootResult = OpenMacroSourcePatcher.switchConditionGroup(
            source,
            groupPath = "root",
            logic = ConditionGroupLogic.OR,
        )

        require(rootResult is SourcePatchResult.Success)
        assertTrue(rootResult.sourceText.contains("any: # keep root note"))
        assertTrue(rootResult.sourceText.contains("any: # keep nested note"))

        val nestedResult = OpenMacroSourcePatcher.switchConditionGroup(
            rootResult.sourceText,
            groupPath = "root.1",
            logic = ConditionGroupLogic.AND,
        )

        require(nestedResult is SourcePatchResult.Success)
        assertTrue(nestedResult.sourceText.contains("all: # keep nested note"))
        assertTrue(nestedResult.sourceText.contains("ssid: Guest # keep child note"))
        val parsed = OpenMacroYamlReader.read(nestedResult.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertTrue(parsed.source.document.conditionTree is MacroConditionNode.Any)
    }

    @Test
    fun appendsConditionTreeChildWithoutReformattingExistingGroup() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-condition-add
              name: Patch condition add
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              all: # keep root note
                - condition:
                    id: unlocked
                    type: android.device.unlocked
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()

        val result = OpenMacroSourcePatcher.addConditionTreeChild(
            source,
            groupPath = "root",
            child = MacroBlock("device-unlocked-2", "android.device.unlocked"),
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.contains("all: # keep root note"))
        assertTrue(result.sourceText.contains("id: \"device-unlocked-2\""))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        val tree = parsed.source.document.conditionTree as MacroConditionNode.All
        assertEquals(2, tree.children.size)
        assertEquals(
            "device-unlocked-2",
            (tree.children.last() as MacroConditionNode.Condition).block.id,
        )
    }

    @Test
    fun removesConditionTreeChildWithoutReformattingSiblings() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-condition-remove
              name: Patch condition remove
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              all: # keep root note
                - condition:
                    id: first
                    type: android.device.unlocked
                - condition:
                    id: second
                    type: android.wifi.connected
                    config:
                      ssid: Home # keep child note
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()

        val result = OpenMacroSourcePatcher.removeConditionTreeChild(source, "root.0")

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.contains("all: # keep root note"))
        assertFalse(result.sourceText.contains("id: first"))
        assertTrue(result.sourceText.contains("ssid: Home # keep child note"))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        val tree = parsed.source.document.conditionTree as MacroConditionNode.All
        assertEquals(1, tree.children.size)
        assertEquals(
            "second",
            (tree.children.single() as MacroConditionNode.Condition).block.id,
        )
    }

    @Test
    fun wrapsAndUnwrapsConditionTreeChildWithoutReformattingIt() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: patch-condition-wrap
              name: Patch condition wrap
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              all: # keep root note
                - condition:
                    id: wifi
                    type: android.wifi.connected
                    config:
                      ssid: Home # keep child note
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()

        val wrapped = OpenMacroSourcePatcher.wrapConditionTreeChildInNot(source, "root.0")

        require(wrapped is SourcePatchResult.Success)
        assertTrue(wrapped.sourceText.contains("all: # keep root note"))
        assertTrue(wrapped.sourceText.contains("- not:"))
        assertTrue(wrapped.sourceText.contains("ssid: Home # keep child note"))
        val wrappedParsed = OpenMacroYamlReader.read(wrapped.sourceText)
        require(wrappedParsed is OpenMacroSourceResult.Success)
        val wrappedTree = wrappedParsed.source.document.conditionTree as MacroConditionNode.All
        assertTrue(wrappedTree.children.single() is MacroConditionNode.Not)

        val unwrapped = OpenMacroSourcePatcher.unwrapConditionTreeNot(
            wrapped.sourceText,
            "root.0",
        )

        require(unwrapped is SourcePatchResult.Success)
        assertTrue(unwrapped.sourceText.contains("all: # keep root note"))
        assertFalse(unwrapped.sourceText.contains("- not:"))
        assertTrue(unwrapped.sourceText.contains("ssid: Home # keep child note"))
        val unwrappedParsed = OpenMacroYamlReader.read(unwrapped.sourceText)
        require(unwrappedParsed is OpenMacroSourceResult.Success)
        val unwrappedTree = unwrappedParsed.source.document.conditionTree as MacroConditionNode.All
        val condition = unwrappedTree.children.single() as MacroConditionNode.Condition
        assertEquals("wifi", condition.block.id)
    }

    @Test
    fun addsTopLevelBlockWithoutReformattingExistingSource() {
        val source = topLevelPatchSource()
        val block = MacroBlock(
            id = "show",
            type = "android.notification.show",
            config = mapOf(
                "title" to MacroValue.Text("ZeroBit"),
                "message" to MacroValue.Text("Ran"),
            ),
        )

        val result = OpenMacroSourcePatcher.addTopLevelBlock(
            sourceText = source,
            lane = CapabilityLane.ACTION,
            block = block,
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.startsWith("# keep header"))
        assertTrue(result.sourceText.contains("type: openmacro.flow.stop # keep inline"))
        assertTrue(result.sourceText.contains("# keep action separator"))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(listOf("first-action", "second-action", "show"), parsed.source.document.actions.map { it.id })
    }

    @Test
    fun removesAndMovesTopLevelBlocksWhilePreservingOtherComments() {
        val source = topLevelPatchSource()

        val moved = OpenMacroSourcePatcher.moveTopLevelBlock(
            sourceText = source,
            lane = CapabilityLane.ACTION,
            blockId = "second-action",
            offset = -1,
        )
        require(moved is SourcePatchResult.Success)
        assertTrue(moved.sourceText.startsWith("# keep header"))
        assertTrue(moved.sourceText.contains("# keep action separator"))
        assertTrue(
            moved.sourceText.indexOf("id: second-action") <
                moved.sourceText.indexOf("id: first-action"),
        )

        val removed = OpenMacroSourcePatcher.removeTopLevelBlock(
            sourceText = moved.sourceText,
            lane = CapabilityLane.ACTION,
            blockId = "first-action",
        )
        require(removed is SourcePatchResult.Success)
        assertTrue(removed.sourceText.startsWith("# keep header"))
        assertTrue(removed.sourceText.contains("# keep trigger note"))
        assertFalse(removed.sourceText.contains("id: first-action"))
        val parsed = OpenMacroYamlReader.read(removed.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(listOf("second-action"), parsed.source.document.actions.map { it.id })
    }

    @Test
    fun addsAndRemovesOnlyConditionUsingLocalSequencePatch() {
        val source = topLevelPatchSource()
        val added = OpenMacroSourcePatcher.addTopLevelBlock(
            sourceText = source,
            lane = CapabilityLane.CONDITION,
            block = MacroBlock("unlocked", "android.device.unlocked"),
        )
        require(added is SourcePatchResult.Success)
        assertTrue(added.sourceText.contains("conditions: \n  - id: \"unlocked\""))

        val removed = OpenMacroSourcePatcher.removeTopLevelBlock(
            sourceText = added.sourceText,
            lane = CapabilityLane.CONDITION,
            blockId = "unlocked",
        )
        require(removed is SourcePatchResult.Success)
        assertTrue(removed.sourceText.contains("conditions: []"))
        assertTrue(removed.sourceText.contains("# keep header"))
        assertTrue(OpenMacroYamlReader.read(removed.sourceText) is OpenMacroSourceResult.Success)
    }

    @Test
    fun rejectsFlowStyleTopLevelListWithoutChangingIt() {
        val source = """
            format: openmacro/v0.1
            metadata: {id: flow-list, name: Flow list}
            triggers: [{id: power, type: android.power.connected}]
            conditions: []
            actions: [{id: stop, type: openmacro.flow.stop}]
        """.trimIndent()

        val result = OpenMacroSourcePatcher.addTopLevelBlock(
            sourceText = source,
            lane = CapabilityLane.ACTION,
            block = MacroBlock("log", "android.log.write"),
        )

        assertEquals(
            SourcePatchResult.Unsupported("Flow-style actions cannot be edited visually."),
            result,
        )
    }

    @Test
    fun addsActionToNestedGroupWithoutReformattingComments() {
        val source = nestedGroupPatchSource()
        val result = OpenMacroSourcePatcher.addGroupedAction(
            sourceText = source,
            groupBlockId = "inner-group",
            child = MacroBlock(
                id = "inner-log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("added")),
            ),
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.startsWith("# keep grouped header"))
        assertTrue(result.sourceText.contains("# keep inner separator"))
        assertTrue(result.sourceText.contains("- id: \"inner-log\""))
        assertTrue(OpenMacroYamlReader.read(result.sourceText) is OpenMacroSourceResult.Success)
    }

    @Test
    fun movesAndRemovesNestedGroupedActionsLocally() {
        val source = nestedGroupPatchSource()
        val moved = OpenMacroSourcePatcher.moveGroupedAction(
            sourceText = source,
            childBlockId = "inner-second",
            offset = -1,
        )
        require(moved is SourcePatchResult.Success)
        assertTrue(moved.sourceText.contains("# keep inner separator"))
        assertTrue(
            moved.sourceText.indexOf("id: inner-second") <
                moved.sourceText.indexOf("id: inner-first"),
        )

        val removed = OpenMacroSourcePatcher.removeGroupedAction(
            sourceText = moved.sourceText,
            childBlockId = "inner-first",
        )
        require(removed is SourcePatchResult.Success)
        assertFalse(removed.sourceText.contains("id: inner-first"))
        assertTrue(removed.sourceText.contains("# keep outer note"))
        assertTrue(OpenMacroYamlReader.read(removed.sourceText) is OpenMacroSourceResult.Success)
    }

    @Test
    fun refusesToRemoveFinalGroupedActionInPatcher() {
        val source = nestedGroupPatchSource()
        val firstRemoved = OpenMacroSourcePatcher.removeGroupedAction(
            sourceText = source,
            childBlockId = "outer-first",
        )
        require(firstRemoved is SourcePatchResult.Success)

        assertEquals(
            SourcePatchResult.Unsupported(
                "Action groups must keep at least one child action.",
            ),
            OpenMacroSourcePatcher.removeGroupedAction(
                sourceText = firstRemoved.sourceText,
                childBlockId = "inner-group",
            ),
        )
    }

    @Test
    fun addsVariableSectionWithoutReformattingFollowingSource() {
        val source = topLevelPatchSource()
        val result = OpenMacroSourcePatcher.addVariableDeclaration(
            sourceText = source,
            variable = MacroVariable(
                name = "text_value",
                type = MacroVariableType.TEXT,
                initialValue = MacroValue.Text("hello"),
            ),
        )

        require(result is SourcePatchResult.Success)
        assertTrue(result.sourceText.startsWith("# keep header"))
        assertTrue(result.sourceText.indexOf("variables:") < result.sourceText.indexOf("triggers:"))
        assertTrue(result.sourceText.contains("initial: \"hello\""))
        val parsed = OpenMacroYamlReader.read(result.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals("text_value", parsed.source.document.variables.single().name)
    }

    @Test
    fun addsVariableToEmptyAndPopulatedSectionsLocally() {
        val emptySource = topLevelPatchSource().replace(
            "triggers:",
            "variables: []\ntriggers:",
        )
        val first = OpenMacroSourcePatcher.addVariableDeclaration(
            sourceText = emptySource,
            variable = MacroVariable(
                name = "number_value",
                type = MacroVariableType.NUMBER,
                initialValue = MacroValue.Number(java.math.BigDecimal.ONE),
            ),
        )
        require(first is SourcePatchResult.Success)
        assertTrue(first.sourceText.contains("variables: \n  - name: \"number_value\""))

        val second = OpenMacroSourcePatcher.addVariableDeclaration(
            sourceText = first.sourceText.replace(
                "type: \"number\"",
                "type: \"number\" # keep variable note",
            ),
            variable = MacroVariable(
                name = "secret_value",
                type = MacroVariableType.SECRET,
                secretKey = "secret.value",
            ),
        )
        require(second is SourcePatchResult.Success)
        assertTrue(second.sourceText.contains("# keep variable note"))
        assertTrue(second.sourceText.contains("secret_key: \"secret.value\""))
        val parsed = OpenMacroYamlReader.read(second.sourceText)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(listOf("number_value", "secret_value"), parsed.source.document.variables.map { it.name })
    }

    @Test
    fun rejectsFlowStyleVariableList() {
        val source = topLevelPatchSource().replace(
            "triggers:",
            "variables: [{name: existing, type: text, initial: value}]\ntriggers:",
        )

        assertEquals(
            SourcePatchResult.Unsupported("Flow-style variables cannot be edited visually."),
            OpenMacroSourcePatcher.addVariableDeclaration(
                sourceText = source,
                variable = MacroVariable(
                    name = "new_value",
                    type = MacroVariableType.BOOLEAN,
                    initialValue = MacroValue.Boolean(false),
                ),
            ),
        )
    }

    private fun topLevelPatchSource(): String = """
        # keep header
        format: openmacro/v0.1
        metadata:
          id: top-level-patch
          name: Top level patch
        triggers:
          # keep trigger note
          - id: power
            type: android.power.connected
        conditions: []
        actions:
          - id: first-action
            type: android.log.write
            config:
              message: first
          # keep action separator
          - id: second-action
            type: openmacro.flow.stop # keep inline
    """.trimIndent()

    private fun nestedGroupPatchSource(): String = """
        # keep grouped header
        format: openmacro/v0.1
        metadata:
          id: nested-group-patch
          name: Nested group patch
        triggers:
          - id: power
            type: android.power.connected
        conditions: []
        actions:
          - id: outer-group
            type: openmacro.action.group
            config:
              failurePolicy: stop # keep outer note
              actions:
                - id: outer-first
                  type: openmacro.flow.stop
                - id: inner-group
                  type: openmacro.action.group
                  config:
                    failurePolicy: stop
                    actions:
                      - id: inner-first
                        type: openmacro.flow.stop
                      # keep inner separator
                      - id: inner-second
                        type: android.log.write
                        config:
                          message: second
    """.trimIndent()
}
