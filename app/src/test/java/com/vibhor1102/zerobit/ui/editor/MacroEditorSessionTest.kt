/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
