/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.model.ConditionGroupLogic
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MacroEditorSessionTest {
    @Test
    fun visualVariableEditUsesSourcePatchAndProposalValidation() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: editor-variable
              name: Editor variable
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
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val result = session.updateVariableField(
            state,
            "count",
            "initial",
            MacroValue.Number(java.math.BigDecimal("7")),
        )

        require(result is FormSourceEditResult.Updated)
        assertTrue(result.state.sourceText.contains("initial: 7 # keep"))
        assertTrue(result.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualVariableCreateUsesBoundedTemplateAndProposalValidation() {
        val source = """
            # keep variable header
            format: openmacro/v0.1
            metadata:
              id: editor-variable-create
              name: Editor variable create
            variables:
              - name: text_value
                type: text
                initial: existing # keep existing variable
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val result = session.addVariable(
            current = state,
            template = VariableDeclarationTemplate.TEXT,
        )

        require(result is FormSourceEditResult.Updated)
        assertTrue(result.state.sourceText.contains("name: \"text_value_2\""))
        assertTrue(result.state.sourceText.contains("initial: \"\""))
        assertTrue(result.state.sourceText.startsWith("# keep variable header"))
        assertTrue(result.state.sourceText.contains("initial: existing # keep existing variable"))
        assertTrue(result.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualVariableDeleteRejectsReferencedVariables() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: editor-variable-delete
              name: Editor variable delete
            variables:
              - name: unused
                type: text
                initial: safe # keep removed-line neighbor honest
              - name: message
                type: text
                initial: hello # keep this note
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: log-message
                type: android.log.write
                config:
                  message:
                    variable: message
        """.trimIndent()
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val removed = session.removeVariable(
            current = state,
            variableName = "unused",
        )

        require(removed is FormSourceEditResult.Updated)
        assertTrue(!removed.state.sourceText.contains("name: unused"))
        assertTrue(removed.state.sourceText.contains("initial: hello # keep this note"))
        assertTrue(removed.state.result is ProposalResult.Ready)

        val rejected = session.removeVariable(
            current = removed.state,
            variableName = "message",
        )

        require(rejected is FormSourceEditResult.Rejected)
        assertTrue(rejected.message.contains("log-message"))
    }

    @Test
    fun visualVariableRenameUpdatesReferencesAndKeepsLocalSourceShape() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: editor-variable-rename
              name: Editor variable rename
            variables:
              - name: message # keep name note
                type: text
                initial: hello
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: set-current
                type: openmacro.variable.set
                config:
                  name: message # keep target note
                  value: next
              - id: log-current
                type: android.log.write
                config:
                  message:
                    variable: message # keep reference note
        """.trimIndent()
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val renamed = session.renameVariable(
            current = state,
            oldName = "message",
            newName = "status_text",
        )

        require(renamed is FormSourceEditResult.Updated)
        assertTrue(renamed.state.sourceText.contains("name: \"status_text\" # keep name note"))
        assertTrue(renamed.state.sourceText.contains("name: \"status_text\" # keep target note"))
        assertTrue(
            renamed.state.sourceText.contains("variable: \"status_text\" # keep reference note"),
        )
        assertTrue(renamed.state.result is ProposalResult.Ready)

        val duplicate = session.renameVariable(
            current = renamed.state,
            oldName = "status_text",
            newName = "status_text",
        )

        require(duplicate is FormSourceEditResult.Rejected)
        assertTrue(duplicate.message.contains("different variable name"))
    }

    @Test
    fun visualConditionTreeEditSwitchesGroupLogicWithLocalPatch() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: editor-condition-tree
              name: Editor condition tree
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              all: # keep root note
                - condition:
                    id: unlocked
                    type: android.device.unlocked
                - condition:
                    id: wifi
                    type: android.wifi.connected
                    config:
                      ssid: Guest # keep child note
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val switched = session.switchConditionGroup(
            current = state,
            groupPath = "root",
            logic = ConditionGroupLogic.OR,
        )

        require(switched is FormSourceEditResult.Updated)
        assertTrue(switched.state.sourceText.contains("any: # keep root note"))
        assertTrue(switched.state.sourceText.contains("ssid: Guest # keep child note"))
        assertTrue(switched.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualConditionTreeEditAddsDefaultConditionChildWithLocalPatch() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: editor-condition-add
              name: Editor condition add
            triggers:
              - id: power
                type: android.power.connected
            condition_tree:
              all: # keep root note
                - condition:
                    id: device-unlocked
                    type: android.device.unlocked
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val added = session.addConditionTreeChild(
            current = state,
            groupPath = "root",
            template = topLevelTemplate("android.device.unlocked"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("all: # keep root note"))
        assertTrue(added.state.sourceText.contains("id: \"device-unlocked-2\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualConditionTreeEditRemovesChildWithLocalPatch() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: editor-condition-remove
              name: Editor condition remove
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
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val removed = session.removeConditionTreeChild(
            current = state,
            childPath = "root.0",
        )

        require(removed is FormSourceEditResult.Updated)
        assertTrue(removed.state.sourceText.contains("all: # keep root note"))
        assertFalse(removed.state.sourceText.contains("id: first"))
        assertTrue(removed.state.sourceText.contains("ssid: Home # keep child note"))
        assertTrue(removed.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualConditionTreeEditWrapsAndUnwrapsNotWithLocalPatch() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: editor-condition-wrap
              name: Editor condition wrap
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
        val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val state = session.create(source)

        val wrapped = session.wrapConditionTreeChildInNot(
            current = state,
            childPath = "root.0",
        )

        require(wrapped is FormSourceEditResult.Updated)
        assertTrue(wrapped.state.sourceText.contains("- not:"))
        assertTrue(wrapped.state.sourceText.contains("ssid: Home # keep child note"))
        assertTrue(wrapped.state.result is ProposalResult.Ready)

        val unwrapped = session.unwrapConditionTreeNot(
            current = wrapped.state,
            childPath = "root.0",
        )

        require(unwrapped is FormSourceEditResult.Updated)
        assertFalse(unwrapped.state.sourceText.contains("- not:"))
        assertTrue(unwrapped.state.sourceText.contains("ssid: Home # keep child note"))
        assertTrue(unwrapped.state.result is ProposalResult.Ready)
    }

    private val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())

    @Test
    fun startsWithEquivalentVisualAndCodeViews() {
        val (_, state) = MacroEditorSession.withInitialSourceApproved(
            pipeline = pipeline,
            initialSource = SampleMacro.source,
        )

        require(state.result is ProposalResult.Ready)
        assertEquals(EditorMode.VISUAL, state.mode)
        assertFalse(state.result.proposal.comparison.approvalRequired)
        assertEquals(
            SampleMacro.source,
            state.visibleProposal?.source?.originalText,
        )
        assertFalse(state.visualIsStale)
    }

    @Test
    fun behaviorEditUpdatesVisualProposalAndRequiresApproval() {
        val (session, initial) = MacroEditorSession.withInitialSourceApproved(
            pipeline,
            SampleMacro.source,
        )

        val changed = session.updateSource(
            initial,
            initial.sourceText.replace(
                "message: The charger is connected.",
                "message: Time to charge.",
            ),
        )

        require(changed.result is ProposalResult.Ready)
        assertTrue(changed.result.proposal.comparison.approvalRequired)
        assertTrue(
            changed.visibleProposal
                ?.explanation
                ?.blocks
                ?.single { it.blockId == "show-message" }
                ?.summary
                .orEmpty()
                .contains("Time to charge."),
        )
        assertFalse(changed.visualIsStale)
    }

    @Test
    fun invalidCodeRetainsLastValidVisualVersion() {
        val (session, initial) = MacroEditorSession.withInitialSourceApproved(
            pipeline,
            SampleMacro.source,
        )

        val invalid = session.updateSource(initial, "format: [")

        assertTrue(invalid.result is ProposalResult.SourceRejected)
        assertTrue(invalid.problems.isNotEmpty())
        assertTrue(invalid.visualIsStale)
        assertEquals(
            initial.visibleProposal,
            invalid.visibleProposal,
        )
    }

    @Test
    fun fixingCodeClearsProblemsAndStaleState() {
        val (session, initial) = MacroEditorSession.withInitialSourceApproved(
            pipeline,
            SampleMacro.source,
        )
        val invalid = session.updateSource(initial, "format: [")

        val fixed = session.updateSource(invalid, SampleMacro.source)

        assertTrue(fixed.result is ProposalResult.Ready)
        assertTrue(fixed.problems.isEmpty())
        assertFalse(fixed.visualIsStale)
    }

    @Test
    fun modeSwitchDoesNotCreateASecondDocument() {
        val (session, initial) = MacroEditorSession.withInitialSourceApproved(
            pipeline,
            SampleMacro.source,
        )

        val code = session.selectMode(initial, EditorMode.CODE)
        val visual = session.selectMode(code, EditorMode.VISUAL)

        assertEquals(EditorMode.CODE, code.mode)
        assertEquals(EditorMode.VISUAL, visual.mode)
        assertEquals(initial.sourceText, visual.sourceText)
        assertEquals(initial.result, visual.result)
    }

    @Test
    fun approvingCurrentProposalClearsBehaviorChangesAndApprovalRequired() {
        val (session, initial) = MacroEditorSession.withInitialSourceApproved(
            pipeline,
            SampleMacro.source,
        )

        val edited = session.updateSource(
            initial,
            initial.sourceText.replace(
                "message: The charger is connected.",
                "message: Time to charge.",
            ),
        )

        require(edited.result is ProposalResult.Ready)
        assertTrue(edited.result.proposal.comparison.approvalRequired)

        val approved = session.approveCurrent(edited)

        require(approved.result is ProposalResult.Ready)
        assertFalse(approved.result.proposal.comparison.approvalRequired)
        assertFalse(approved.result.proposal.comparison.behaviorChanged)
    }

    @Test
    fun visualScalarEditPreservesCommentsAndRunsProposalValidation() {
        val source = "# Keep me\n${SampleMacro.source}"
        val (session, initial) = MacroEditorSession.withInitialSourceApproved(
            pipeline,
            source,
        )

        val result = session.updateScalarConfig(
            current = initial,
            blockId = "show-message",
            key = "message",
            value = MacroValue.Text("Updated safely"),
        )

        require(result is FormSourceEditResult.Updated)
        assertTrue(result.state.sourceText.startsWith("# Keep me"))
        assertTrue(result.state.sourceText.contains("\"Updated safely\""))
        assertTrue(result.state.result is ProposalResult.Ready)
        assertTrue(
            (result.state.result as ProposalResult.Ready)
                .proposal
                .comparison
                .approvalRequired,
        )
    }

    @Test
    fun visualEditCanReplaceScalarWithReferenceAndRemoveOptionalKey() {
        val source = SampleMacro.source.replace(
            "title: Charging started",
            "title: Charging started\n      optional: remove-me",
        )
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(source)

        val reference = session.updateConfig(
            current = initial,
            blockId = "show-message",
            key = "message",
            value = MacroValue.ObjectValue(
                mapOf("variable" to MacroValue.Text("message")),
            ),
        )
        require(reference is FormSourceEditResult.Updated)
        assertTrue(reference.state.sourceText.contains("{\"variable\": \"message\"}"))

        val removed = session.updateConfig(
            current = reference.state,
            blockId = "show-message",
            key = "optional",
            value = null,
        )
        require(removed is FormSourceEditResult.Updated)
        assertTrue(!removed.state.sourceText.contains("optional:"))
    }

    @Test
    fun visualEditCanUpdateNestedActionGroupChildThroughModelFallback() {
        val source = """
            format: openmacro/v0.1
            metadata:
              id: grouped-editor
              name: Grouped editor
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: group
                type: openmacro.action.group
                config:
                  failurePolicy: stop
                  actions:
                    - id: child-log
                      type: android.log.write
                      config:
                        message: before
        """.trimIndent() + "\n"
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(source)

        val result = session.updateConfig(
            current = initial,
            blockId = "child-log",
            key = "message",
            value = MacroValue.Text("after"),
        )

        require(result is FormSourceEditResult.Updated)
        assertTrue(result.state.sourceText.contains("message: \"after\""))
        assertTrue(result.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualEditCanAddMoveAndRemoveGroupedActions() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(groupedEditorSource())

        val added = session.addGroupedAction(
            current = initial,
            groupBlockId = "group",
            template = groupedActionTemplate(initial, "android.log.write"),
        )
        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"group-write-log\""))
        assertTrue(added.state.sourceText.startsWith("# keep grouped header"))
        assertTrue(added.state.sourceText.contains("failurePolicy: stop # keep group note"))
        assertTrue(added.state.result is ProposalResult.Ready)

        val addedWait = session.addGroupedAction(
            current = added.state,
            groupBlockId = "group",
            template = groupedActionTemplate(added.state, "openmacro.flow.delay"),
        )
        require(addedWait is FormSourceEditResult.Updated)
        assertTrue(addedWait.state.sourceText.contains("id: \"group-delay\""))
        assertTrue(addedWait.state.sourceText.contains("milliseconds: 1000"))
        assertTrue(addedWait.state.result is ProposalResult.Ready)

        val movedOnce = session.moveGroupedAction(
            current = addedWait.state,
            childBlockId = "group-write-log",
            direction = NestedActionMoveDirection.UP,
        )
        require(movedOnce is FormSourceEditResult.Updated)
        val moved = session.moveGroupedAction(
            current = movedOnce.state,
            childBlockId = "group-write-log",
            direction = NestedActionMoveDirection.UP,
        )
        require(moved is FormSourceEditResult.Updated)
        assertTrue(
            moved.state.sourceText.indexOf("id: \"group-write-log\"") <
                moved.state.sourceText.indexOf("id: first"),
        )

        val removed = session.removeGroupedAction(
            current = moved.state,
            childBlockId = "first",
        )
        require(removed is FormSourceEditResult.Updated)
        assertTrue(!removed.state.sourceText.contains("id: first"))
        assertTrue(removed.state.sourceText.contains("# keep child separator"))
        assertTrue(removed.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualEditCanAddMoveAndRemoveTopLevelBlocks() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val source = "# keep header\n" + SampleMacro.source.replace(
            "id: charger-greeting",
            "id: charger-greeting # keep metadata style",
        )
        val initial = session.create(source)

        val added = session.addTopLevelBlock(
            current = initial,
            template = topLevelTemplate("android.screen.on"),
        )
        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"screen-on\""))
        assertTrue(added.state.sourceText.startsWith("# keep header"))
        assertTrue(
            added.state.sourceText.contains(
                "id: charger-greeting # keep metadata style",
            ),
        )
        assertTrue(added.state.result is ProposalResult.Ready)

        val moved = session.moveTopLevelBlock(
            current = added.state,
            blockId = "screen-on",
            direction = NestedActionMoveDirection.UP,
        )
        require(moved is FormSourceEditResult.Updated)
        assertTrue(
            moved.state.sourceText.indexOf("id: \"screen-on\"") <
                moved.state.sourceText.indexOf("id: \"charger-connected\""),
        )

        val removed = session.removeTopLevelBlock(
            current = moved.state,
            blockId = "charger-connected",
        )
        require(removed is FormSourceEditResult.Updated)
        assertTrue(!removed.state.sourceText.contains("id: \"charger-connected\""))
        assertTrue(removed.state.result is ProposalResult.Ready)
    }

    @Test
    fun visualEditCanAddValidatorCleanActionGroupStarter() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            current = initial,
            template = topLevelTemplate("openmacro.action.group"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"action-group\""))
        assertTrue(added.state.sourceText.contains("failurePolicy: \"stop\""))
        assertTrue(added.state.sourceText.contains("id: \"group-step\""))
        assertTrue(added.state.sourceText.contains("type: \"openmacro.flow.stop\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredOpenWebSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val option = topLevelTemplate("android.web.open")
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            option,
            mapOf("url" to MacroValue.Text("https://example.com/docs")),
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"open-web\""))
        assertTrue(added.state.sourceText.contains("url: \"https://example.com/docs\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredSmsSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            topLevelTemplate("android.sms.send"),
            mapOf(
                "phoneNumber" to MacroValue.Text("+15551234567"),
                "message" to MacroValue.Text("Automation ran"),
            ),
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"send-sms\""))
        assertTrue(added.state.sourceText.contains("phoneNumber: \"+15551234567\""))
        assertTrue(added.state.sourceText.contains("message: \"Automation ran\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredDialSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            topLevelTemplate("android.phone.dial"),
            mapOf("phoneNumber" to MacroValue.Text("+15551234567")),
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"dial-number\""))
        assertTrue(added.state.sourceText.contains("phoneNumber: \"+15551234567\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredEmailSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            topLevelTemplate("android.email.compose"),
            mapOf(
                "recipient" to MacroValue.Text("person@example.com"),
                "subject" to MacroValue.Text("Automation report"),
                "body" to MacroValue.Text("The macro completed."),
            ),
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"compose-email\""))
        assertTrue(added.state.sourceText.contains("recipient: \"person@example.com\""))
        assertTrue(added.state.sourceText.contains("subject: \"Automation report\""))
        assertTrue(added.state.sourceText.contains("body: \"The macro completed.\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredMapSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            topLevelTemplate("android.map.open"),
            mapOf("query" to MacroValue.Text("India Gate, New Delhi")),
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"open-map-location\""))
        assertTrue(added.state.sourceText.contains("query: \"India Gate, New Delhi\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredAlarmSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            topLevelTemplate("android.alarm.set"),
            mapOf(
                "hour" to MacroValue.Number(BigDecimal("7")),
                "minute" to MacroValue.Number(BigDecimal("30")),
                "label" to MacroValue.Text("Morning"),
                "skipUi" to MacroValue.Boolean(false),
            ),
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"set-alarm\""))
        assertTrue(added.state.sourceText.contains("hour: 7"))
        assertTrue(added.state.sourceText.contains("minute: 30"))
        assertTrue(added.state.sourceText.contains("label: \"Morning\""))
        assertTrue(added.state.sourceText.contains("skipUi: false"))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredTimerSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            topLevelTemplate("android.timer.set"),
            mapOf(
                "seconds" to MacroValue.Number(BigDecimal("300")),
                "label" to MacroValue.Text("Tea"),
                "skipUi" to MacroValue.Boolean(true),
            ),
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"set-timer\""))
        assertTrue(added.state.sourceText.contains("seconds: 300"))
        assertTrue(added.state.sourceText.contains("label: \"Tea\""))
        assertTrue(added.state.sourceText.contains("skipUi: true"))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredNotificationTriggerSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val option = topLevelTemplate("android.notification.received")
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            option,
            option.defaultConfig,
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"notification-received\""))
        assertTrue(added.state.sourceText.contains("capture: [\"title\"]"))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun configuredScheduleSetupCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val option = topLevelTemplate("android.time.schedule")
        val configured = MacroBlockEditor.configureTemplate(
            CapabilityRegistry.builtIn(),
            requireNotNull(initial.visibleProposal).source.document,
            option,
            option.defaultConfig,
        )
        require(configured is TemplateConfigurationResult.Configured)

        val added = session.addTopLevelBlock(initial, configured.template)

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"time-schedule\""))
        assertTrue(added.state.sourceText.contains("time: \"08:00\""))
        assertTrue(added.state.sourceText.contains("timezone: \"UTC\""))
        assertTrue(added.state.sourceText.contains("delivery: \"windowed\""))
        assertTrue(added.state.sourceText.contains("window_minutes: 15"))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun vibrationStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.device.vibrate"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"vibrate\""))
        assertTrue(added.state.sourceText.contains("milliseconds: 250"))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun clipboardStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.clipboard.set-text"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"copy-to-clipboard\""))
        assertTrue(added.state.sourceText.contains("text: \"Copied by ZeroBit\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun chargingConditionStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.battery.charging"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"battery-charging\""))
        assertTrue(added.state.sourceText.contains("state: \"charging\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun batteryLevelConditionStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.battery.level-condition"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"battery-level-condition\""))
        assertTrue(added.state.sourceText.contains("level: 50"))
        assertTrue(added.state.sourceText.contains("direction: \"goes_below\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun powerConnectionConditionStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.power.connection-state"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"power-connection\""))
        assertTrue(added.state.sourceText.contains("state: \"plugged_in\""))
        assertTrue(added.state.sourceText.contains("source: \"any\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun screenStateConditionStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.screen.interactive-state"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"screen-state\""))
        assertTrue(added.state.sourceText.contains("state: \"screen_on\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun deviceLockStateStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.device.unlocked"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"device-unlocked-2\""))
        assertTrue(added.state.sourceText.contains("state: \"unlocked\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun powerDisconnectedStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)

        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.power.disconnected"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"power-disconnected\""))
        assertTrue(added.state.sourceText.contains("type: \"android.power.disconnected\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun wifiConnectivityStartersCanEnterSource() {
        listOf(
            "android.wifi.connected-trigger" to "wifi-connected-trigger",
            "android.wifi.disconnected-trigger" to "wifi-disconnected-trigger",
        ).forEach { (type, id) ->
            val session = MacroEditorSession(pipeline, initialApproved = null)
            val initial = session.create(SampleMacro.source)
            val added = session.addTopLevelBlock(initial, topLevelTemplate(type))

            require(added is FormSourceEditResult.Updated)
            assertTrue(added.state.sourceText.contains("id: \"$id\""))
            assertTrue(added.state.result is ProposalResult.Ready)
        }
    }

    @Test
    fun airplaneModeConditionStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.airplane-mode.state"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"airplane-mode\""))
        assertTrue(added.state.sourceText.contains("state: \"enabled\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun airplaneModeTriggerStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.airplane-mode.changed"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"airplane-mode-changed\""))
        assertTrue(added.state.sourceText.contains("state: \"enabled\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun ringerModeConditionStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.ringer-mode.state"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"ringer-mode\""))
        assertTrue(added.state.sourceText.contains("mode: \"normal\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun ringerModeTriggerStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("android.ringer-mode.changed"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"ringer-mode-changed\""))
        assertTrue(added.state.sourceText.contains("mode: \"normal\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun batterySaverStartersCanEnterSource() {
        listOf(
            "android.battery-saver.state" to "battery-saver",
            "android.battery-saver.changed" to "battery-saver-changed",
        ).forEach { (type, id) ->
            val session = MacroEditorSession(pipeline, initialApproved = null)
            val initial = session.create(SampleMacro.source)
            val added = session.addTopLevelBlock(initial, topLevelTemplate(type))

            require(added is FormSourceEditResult.Updated)
            assertTrue(added.state.sourceText.contains("id: \"$id\""))
            assertTrue(added.state.sourceText.contains("state: \"enabled\""))
            assertTrue(added.state.result is ProposalResult.Ready)
        }
    }

    @Test
    fun timeWindowConditionStarterCanEnterSource() {
        val session = MacroEditorSession(pipeline, initialApproved = null)
        val initial = session.create(SampleMacro.source)
        val added = session.addTopLevelBlock(
            initial,
            topLevelTemplate("openmacro.time.window"),
        )

        require(added is FormSourceEditResult.Updated)
        assertTrue(added.state.sourceText.contains("id: \"time-window\""))
        assertTrue(added.state.sourceText.contains("start_time: \"09:00\""))
        assertTrue(added.state.sourceText.contains("end_time: \"17:00\""))
        assertTrue(added.state.sourceText.contains("timezone: \"UTC\""))
        assertTrue(added.state.result is ProposalResult.Ready)
    }

    @Test
    fun rejectedVisualEditCanBeSurfacedWithoutChangingSource() {
        val (session, state) = MacroEditorSession.withInitialSourceApproved(
            pipeline,
            SampleMacro.source,
        )

        val rejected = session.reportFormEditError(
            state,
            "This edit needs a safer patch.",
        )

        assertEquals(state.sourceText, rejected.sourceText)
        assertEquals("This edit needs a safer patch.", rejected.formEditError)
    }

    private fun groupedEditorSource() = """
        # keep grouped header
        format: openmacro/v0.1
        metadata:
          id: grouped-editor
          name: Grouped editor
        triggers:
          - id: power
            type: android.power.connected
        conditions: []
        actions:
          - id: group
            type: openmacro.action.group
            config:
              failurePolicy: stop # keep group note
              actions:
                - id: first
                  type: android.log.write
                  config:
                    message: first
                # keep child separator
                - id: second
                  type: openmacro.flow.stop
    """.trimIndent() + "\n"

    private fun topLevelTemplate(type: String): TopLevelBlockTemplate {
        val sample = pipeline.propose(SampleMacro.source) as ProposalResult.Ready
        return CapabilityLane.values()
            .flatMap { lane ->
                MacroBlockEditor.topLevelTemplates(
                    CapabilityRegistry.builtIn(),
                    lane,
                    sample.proposal.source.document,
                )
            }
            .single { it.type == type }
    }

    private fun groupedActionTemplate(
        state: MacroEditorState,
        type: String,
    ): TopLevelBlockTemplate {
        val document = requireNotNull(state.visibleProposal).source.document
        return MacroBlockEditor.groupedActionTemplates(
            CapabilityRegistry.builtIn(),
            document,
            "group",
        ).single { it.type == type }
    }
}
