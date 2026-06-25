/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.proposal.ApprovedMacroSnapshot
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult

class MacroEditorSession(
    private val pipeline: OpenMacroProposalPipeline,
    private val approved: ApprovedMacroSnapshot?,
) {
    fun create(initialSource: String): MacroEditorState {
        val result = pipeline.propose(initialSource, approved)
        return MacroEditorState(
            sourceText = initialSource,
            result = result,
            visibleProposal = (result as? ProposalResult.Ready)?.proposal,
            visualIsStale = false,
        )
    }

    fun updateSource(
        current: MacroEditorState,
        sourceText: String,
    ): MacroEditorState {
        val result = pipeline.propose(sourceText, approved)
        val ready = (result as? ProposalResult.Ready)?.proposal
        return current.copy(
            sourceText = sourceText,
            result = result,
            visibleProposal = ready ?: current.visibleProposal,
            visualIsStale = ready == null && current.visibleProposal != null,
        )
    }

    fun selectMode(
        current: MacroEditorState,
        mode: EditorMode,
    ): MacroEditorState = current.copy(mode = mode)

    companion object {
        fun withInitialSourceApproved(
            pipeline: OpenMacroProposalPipeline,
            initialSource: String,
        ): Pair<MacroEditorSession, MacroEditorState> {
            val initial = pipeline.propose(initialSource)
            require(initial is ProposalResult.Ready) {
                "The initial editor source must be a valid OpenMacro."
            }
            val session = MacroEditorSession(
                pipeline = pipeline,
                approved = ApprovedMacroSnapshot.from(initial.proposal),
            )
            return session to session.create(initialSource)
        }
    }
}

data class MacroEditorState(
    val mode: EditorMode = EditorMode.VISUAL,
    val sourceText: String,
    val result: ProposalResult,
    val visibleProposal: OpenMacroProposal?,
    val visualIsStale: Boolean,
) {
    val problems: List<EditorProblem>
        get() = when (val current = result) {
            is ProposalResult.Ready -> emptyList()
            is ProposalResult.SourceRejected -> current.issues.map {
                EditorProblem(
                    message = it.message,
                    path = it.path,
                    line = it.line,
                    column = it.column,
                )
            }
            is ProposalResult.ValidationRejected -> current.issues.map {
                EditorProblem(
                    message = it.message,
                    path = it.path,
                )
            }
        }
}

data class EditorProblem(
    val message: String,
    val path: String,
    val line: Int? = null,
    val column: Int? = null,
)

enum class EditorMode {
    VISUAL,
    CODE,
}
