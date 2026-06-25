/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.proposal.ApprovedMacroSnapshot
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourcePatcher
import com.vibhor1102.zerobit.openmacro.source.SourcePatchResult

class MacroEditorSession(
    private val pipeline: OpenMacroProposalPipeline,
    initialApproved: ApprovedMacroSnapshot?,
) {
    private var approved: ApprovedMacroSnapshot? = initialApproved

    fun create(initialSource: String): MacroEditorState {
        val result = pipeline.propose(initialSource, approved)
        return MacroEditorState(
            sourceText = initialSource,
            result = result,
            visibleProposal = (result as? ProposalResult.Ready)?.proposal,
            visualIsStale = false,
            formEditError = null,
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
            formEditError = null,
        )
    }

    fun approveCurrent(current: MacroEditorState): MacroEditorState {
        val ready = current.result as? ProposalResult.Ready ?: return current
        val snap = ApprovedMacroSnapshot.from(ready.proposal)
        this.approved = snap
        val result = pipeline.propose(current.sourceText, snap)
        val readyProposal = (result as? ProposalResult.Ready)?.proposal
        return current.copy(
            result = result,
            visibleProposal = readyProposal ?: current.visibleProposal,
            visualIsStale = readyProposal == null && current.visibleProposal != null,
            formEditError = null,
        )
    }

    fun selectMode(
        current: MacroEditorState,
        mode: EditorMode,
    ): MacroEditorState = current.copy(mode = mode)

    fun reportFormEditError(
        current: MacroEditorState,
        message: String,
    ): MacroEditorState = current.copy(formEditError = message)

    fun updateScalarConfig(
        current: MacroEditorState,
        blockId: String,
        key: String,
        value: MacroValue,
    ): FormSourceEditResult = updateConfig(current, blockId, key, value)

    fun updateConfig(
        current: MacroEditorState,
        blockId: String,
        key: String,
        value: MacroValue?,
    ): FormSourceEditResult =
        when (
            val patch = OpenMacroSourcePatcher.setConfig(
                sourceText = current.sourceText,
                blockId = blockId,
                key = key,
                value = value,
            )
        ) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected("Block '${patch.blockId}' was not found.")
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }

    fun updateVariableField(
        current: MacroEditorState,
        variableName: String,
        key: String,
        value: MacroValue?,
    ): FormSourceEditResult =
        when (
            val patch = OpenMacroSourcePatcher.setVariableField(
                sourceText = current.sourceText,
                variableName = variableName,
                key = key,
                value = value,
            )
        ) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected(
                    "Variable '${patch.blockId}' was not found.",
                )
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }

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
                initialApproved = ApprovedMacroSnapshot.from(initial.proposal),
            )
            return session to session.create(initialSource)
        }
    }
}

sealed interface FormSourceEditResult {
    data class Updated(val state: MacroEditorState) : FormSourceEditResult

    data class Rejected(val message: String) : FormSourceEditResult
}

data class MacroEditorState(
    val mode: EditorMode = EditorMode.VISUAL,
    val sourceText: String,
    val result: ProposalResult,
    val visibleProposal: OpenMacroProposal?,
    val visualIsStale: Boolean,
    val formEditError: String? = null,
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
