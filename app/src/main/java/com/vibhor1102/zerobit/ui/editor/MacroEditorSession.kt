/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.ConditionGroupLogic
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
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
                FormSourceEditResult.Rejected(
                    "Block '${patch.blockId}' was not found.",
                )
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }

    fun addGroupedAction(
        current: MacroEditorState,
        groupBlockId: String,
        template: TopLevelBlockTemplate,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val updated = when (
            val result = MacroBlockEditor.addGroupedAction(document, groupBlockId, template)
        ) {
            is BlockEditResult.Updated -> result.document
            else -> return result.asFormRejection()
        }
        val group = MacroBlockEditor.findBlock(updated, groupBlockId)
            ?: return FormSourceEditResult.Rejected("Action group '$groupBlockId' was not found.")
        val child = MacroBlockEditor.nestedActions(group).last()
        return applySourcePatch(
            current,
            OpenMacroSourcePatcher.addGroupedAction(
                sourceText = current.sourceText,
                groupBlockId = groupBlockId,
                child = child,
            ),
        )
    }

    fun addGroupedLogAction(
        current: MacroEditorState,
        groupBlockId: String,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val template = MacroBlockEditor.groupedActionTemplates(
            CapabilityRegistry.builtIn(),
            document,
            groupBlockId,
        )
            .single { it.type == "android.log.write" }
        return addGroupedAction(current, groupBlockId, template)
    }

    fun addTopLevelBlock(
        current: MacroEditorState,
        template: TopLevelBlockTemplate,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val updated = when (val result = MacroBlockEditor.addTopLevelBlock(document, template)) {
            is BlockEditResult.Updated -> result.document
            else -> return result.asFormRejection()
        }
        val block = updated.topLevelBlocks(template.lane).last()
        return applySourcePatch(
            current,
            OpenMacroSourcePatcher.addTopLevelBlock(
                sourceText = current.sourceText,
                lane = template.lane,
                block = block,
            ),
        )
    }

    fun removeTopLevelBlock(
        current: MacroEditorState,
        blockId: String,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val lane = document.topLevelLaneFor(blockId)
            ?: return FormSourceEditResult.Rejected("Block '$blockId' was not found.")
        when (val result = MacroBlockEditor.removeTopLevelBlock(document, blockId)) {
            is BlockEditResult.Updated -> Unit
            else -> return result.asFormRejection()
        }
        return applySourcePatch(
            current,
            OpenMacroSourcePatcher.removeTopLevelBlock(
                sourceText = current.sourceText,
                lane = lane,
                blockId = blockId,
            ),
        )
    }

    fun moveTopLevelBlock(
        current: MacroEditorState,
        blockId: String,
        direction: NestedActionMoveDirection,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val lane = document.topLevelLaneFor(blockId)
            ?: return FormSourceEditResult.Rejected("Block '$blockId' was not found.")
        when (val result = MacroBlockEditor.moveTopLevelBlock(document, blockId, direction)) {
            is BlockEditResult.Updated -> Unit
            else -> return result.asFormRejection()
        }
        return applySourcePatch(
            current,
            OpenMacroSourcePatcher.moveTopLevelBlock(
                sourceText = current.sourceText,
                lane = lane,
                blockId = blockId,
                offset = when (direction) {
                    NestedActionMoveDirection.UP -> -1
                    NestedActionMoveDirection.DOWN -> 1
                },
            ),
        )
    }

    fun addVariable(
        current: MacroEditorState,
        template: VariableDeclarationTemplate,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val updated = when (val result = MacroBlockEditor.addVariable(document, template)) {
            is BlockEditResult.Updated -> result.document
            else -> return result.asFormRejection()
        }
        return applySourcePatch(
            current,
            OpenMacroSourcePatcher.addVariableDeclaration(
                sourceText = current.sourceText,
                variable = updated.variables.last(),
            ),
        )
    }

    fun removeVariable(
        current: MacroEditorState,
        variableName: String,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        when (val check = MacroBlockEditor.removeVariable(document, variableName)) {
            is BlockEditResult.Updated -> Unit
            is BlockEditResult.Rejected -> return FormSourceEditResult.Rejected(check.message)
            is BlockEditResult.NotFound -> return FormSourceEditResult.Rejected(
                "Variable '${check.blockId}' was not found.",
            )
            is BlockEditResult.Ambiguous -> return FormSourceEditResult.Rejected(
                "Variable '${check.blockId}' matched ${check.matchCount} places.",
            )
        }
        return when (
            val patch = OpenMacroSourcePatcher.removeVariableDeclaration(
                sourceText = current.sourceText,
                variableName = variableName,
            )
        ) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected("Variable '${patch.blockId}' was not found.")
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }
    }

    fun renameVariable(
        current: MacroEditorState,
        oldName: String,
        newName: String,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val trimmedNewName = newName.trim()
        when (val check = MacroBlockEditor.renameVariable(document, oldName, trimmedNewName)) {
            is BlockEditResult.Updated -> Unit
            is BlockEditResult.Rejected -> return FormSourceEditResult.Rejected(check.message)
            is BlockEditResult.NotFound -> return FormSourceEditResult.Rejected(
                "Variable '${check.blockId}' was not found.",
            )
            is BlockEditResult.Ambiguous -> return FormSourceEditResult.Rejected(
                "Variable '${check.blockId}' matched ${check.matchCount} places.",
            )
        }
        return when (
            val patch = OpenMacroSourcePatcher.renameVariable(
                sourceText = current.sourceText,
                oldName = oldName,
                newName = trimmedNewName,
            )
        ) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected("Variable '${patch.blockId}' was not found.")
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }
    }

    fun removeGroupedAction(
        current: MacroEditorState,
        childBlockId: String,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        when (val result = MacroBlockEditor.removeGroupedAction(document, childBlockId)) {
            is BlockEditResult.Updated -> Unit
            else -> return result.asFormRejection()
        }
        return applySourcePatch(
            current,
            OpenMacroSourcePatcher.removeGroupedAction(
                sourceText = current.sourceText,
                childBlockId = childBlockId,
            ),
        )
    }

    fun moveGroupedAction(
        current: MacroEditorState,
        childBlockId: String,
        direction: NestedActionMoveDirection,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        when (val result = MacroBlockEditor.moveGroupedAction(document, childBlockId, direction)) {
            is BlockEditResult.Updated -> Unit
            else -> return result.asFormRejection()
        }
        return applySourcePatch(
            current,
            OpenMacroSourcePatcher.moveGroupedAction(
                sourceText = current.sourceText,
                childBlockId = childBlockId,
                offset = when (direction) {
                    NestedActionMoveDirection.UP -> -1
                    NestedActionMoveDirection.DOWN -> 1
                },
            ),
        )
    }

    fun switchConditionGroup(
        current: MacroEditorState,
        groupPath: String,
        logic: ConditionGroupLogic,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        when (val check = MacroBlockEditor.switchConditionGroup(document, groupPath, logic)) {
            is BlockEditResult.Updated -> Unit
            is BlockEditResult.Rejected -> return FormSourceEditResult.Rejected(check.message)
            is BlockEditResult.NotFound -> return FormSourceEditResult.Rejected(
                "Condition group '${check.blockId}' was not found.",
            )
            is BlockEditResult.Ambiguous -> return FormSourceEditResult.Rejected(
                "Condition group '${check.blockId}' matched ${check.matchCount} places.",
            )
        }
        return when (
            val patch = OpenMacroSourcePatcher.switchConditionGroup(
                sourceText = current.sourceText,
                groupPath = groupPath,
                logic = logic,
            )
        ) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected("Condition group '${patch.blockId}' was not found.")
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }
    }

    fun addConditionTreeChild(
        current: MacroEditorState,
        groupPath: String,
        template: TopLevelBlockTemplate,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        val child = MacroBlockEditor.newConditionTreeChild(document, template)
            ?: return FormSourceEditResult.Rejected(
                "Only conditions can be added to a condition tree.",
            )
        when (val check = MacroBlockEditor.addConditionTreeChild(document, groupPath, child)) {
            is BlockEditResult.Updated -> Unit
            is BlockEditResult.Rejected -> return FormSourceEditResult.Rejected(check.message)
            is BlockEditResult.NotFound -> return FormSourceEditResult.Rejected(
                "Condition group '${check.blockId}' was not found.",
            )
            is BlockEditResult.Ambiguous -> return FormSourceEditResult.Rejected(
                "Condition group '${check.blockId}' matched ${check.matchCount} places.",
            )
        }
        return when (
            val patch = OpenMacroSourcePatcher.addConditionTreeChild(
                sourceText = current.sourceText,
                groupPath = groupPath,
                child = child,
            )
        ) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected("Condition group '${patch.blockId}' was not found.")
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }
    }

    fun removeConditionTreeChild(
        current: MacroEditorState,
        childPath: String,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        when (val check = MacroBlockEditor.removeConditionTreeChild(document, childPath)) {
            is BlockEditResult.Updated -> Unit
            is BlockEditResult.Rejected -> return FormSourceEditResult.Rejected(check.message)
            is BlockEditResult.NotFound -> return FormSourceEditResult.Rejected(
                "Condition child '${check.blockId}' was not found.",
            )
            is BlockEditResult.Ambiguous -> return FormSourceEditResult.Rejected(
                "Condition child '${check.blockId}' matched ${check.matchCount} places.",
            )
        }
        return when (
            val patch = OpenMacroSourcePatcher.removeConditionTreeChild(
                sourceText = current.sourceText,
                childPath = childPath,
            )
        ) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected("Condition child '${patch.blockId}' was not found.")
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(patch.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    patch.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }
    }

    fun wrapConditionTreeChildInNot(
        current: MacroEditorState,
        childPath: String,
    ): FormSourceEditResult =
        transformConditionTreeChildSource(
            current = current,
            childPath = childPath,
            check = { document ->
                MacroBlockEditor.wrapConditionTreeChildInNot(document, childPath)
            },
            patch = { sourceText ->
                OpenMacroSourcePatcher.wrapConditionTreeChildInNot(sourceText, childPath)
            },
        )

    fun unwrapConditionTreeNot(
        current: MacroEditorState,
        childPath: String,
    ): FormSourceEditResult =
        transformConditionTreeChildSource(
            current = current,
            childPath = childPath,
            check = { document ->
                MacroBlockEditor.unwrapConditionTreeNot(document, childPath)
            },
            patch = { sourceText ->
                OpenMacroSourcePatcher.unwrapConditionTreeNot(sourceText, childPath)
            },
        )

    private fun transformConditionTreeChildSource(
        current: MacroEditorState,
        childPath: String,
        check: (com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument) -> BlockEditResult,
        patch: (String) -> SourcePatchResult,
    ): FormSourceEditResult {
        val document = current.visibleProposal?.source?.document
            ?: return FormSourceEditResult.Rejected("No valid visual document is available.")
        when (val result = check(document)) {
            is BlockEditResult.Updated -> Unit
            is BlockEditResult.Rejected -> return FormSourceEditResult.Rejected(result.message)
            is BlockEditResult.NotFound -> return FormSourceEditResult.Rejected(
                "Condition child '${result.blockId}' was not found.",
            )
            is BlockEditResult.Ambiguous -> return FormSourceEditResult.Rejected(
                "Condition child '${result.blockId}' matched ${result.matchCount} places.",
            )
        }
        return when (val result = patch(current.sourceText)) {
            is SourcePatchResult.Success ->
                FormSourceEditResult.Updated(updateSource(current, result.sourceText))
            is SourcePatchResult.NotFound ->
                FormSourceEditResult.Rejected("Condition child '${result.blockId}' was not found.")
            is SourcePatchResult.Unsupported ->
                FormSourceEditResult.Rejected(result.message)
            is SourcePatchResult.InvalidSource ->
                FormSourceEditResult.Rejected(
                    result.issues.firstOrNull()?.message ?: "The source is invalid.",
                )
        }
    }

    private fun applySourcePatch(
        current: MacroEditorState,
        patch: SourcePatchResult,
    ): FormSourceEditResult = when (patch) {
        is SourcePatchResult.Success ->
            FormSourceEditResult.Updated(updateSource(current, patch.sourceText))
        is SourcePatchResult.NotFound ->
            FormSourceEditResult.Rejected("Block '${patch.blockId}' was not found.")
        is SourcePatchResult.Unsupported -> FormSourceEditResult.Rejected(patch.message)
        is SourcePatchResult.InvalidSource ->
            FormSourceEditResult.Rejected(
                patch.issues.firstOrNull()?.message ?: "The source is invalid.",
            )
    }

    private fun BlockEditResult.asFormRejection(): FormSourceEditResult.Rejected =
        FormSourceEditResult.Rejected(
            when (this) {
                is BlockEditResult.Rejected -> message
                is BlockEditResult.NotFound -> "Block '$blockId' was not found."
                is BlockEditResult.Ambiguous ->
                    "Block '$blockId' matched $matchCount places."
                is BlockEditResult.Updated -> "The block edit did not require a source change."
            },
        )

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

private fun OpenMacroDocument.topLevelLaneFor(blockId: String): CapabilityLane? = when {
    triggers.any { it.id == blockId } -> CapabilityLane.TRIGGER
    conditions.any { it.id == blockId } -> CapabilityLane.CONDITION
    actions.any { it.id == blockId } -> CapabilityLane.ACTION
    else -> null
}

private fun OpenMacroDocument.topLevelBlocks(lane: CapabilityLane) = when (lane) {
    CapabilityLane.TRIGGER -> triggers
    CapabilityLane.CONDITION -> conditions
    CapabilityLane.ACTION -> actions
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
