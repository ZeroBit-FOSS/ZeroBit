/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroEditorSessionTest {
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
}
