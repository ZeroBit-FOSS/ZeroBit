/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import com.vibhor1102.zerobit.openmacro.source.SourceIssue
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

/**
 * Reads the editable workspace and compares it with app-private approval state.
 * It never changes approval state merely because a workspace file changed.
 */
class WorkspaceReviewService(
    private val workspace: WorkspaceMacroStore,
    private val approvals: ApprovalStore,
    private val pipeline: OpenMacroProposalPipeline,
) {
    fun review(macroId: String): WorkspaceReviewResult {
        val workspaceSource = when (val result = workspace.read(macroId)) {
            is WorkspaceMacroResult.Success -> result.source
            WorkspaceMacroResult.Missing -> return WorkspaceReviewResult.Missing
            is WorkspaceMacroResult.InvalidId ->
                return WorkspaceReviewResult.StorageFailure("invalid_macro_id", result.message)
            is WorkspaceMacroResult.InvalidSource ->
                return WorkspaceReviewResult.SourceRejected(result.issues)
            is WorkspaceMacroResult.IoFailure ->
                return WorkspaceReviewResult.StorageFailure("workspace_read_failed", result.message)
        }

        val approved = when (val result = approvals.loadCurrent(macroId)) {
            is ApprovalStoreResult.Success -> result.value?.snapshot
            is ApprovalStoreResult.Failure ->
                return WorkspaceReviewResult.StorageFailure(result.code, result.message)
        }

        return when (
            val proposal = pipeline.propose(
                sourceText = workspaceSource.originalText,
                approved = approved,
            )
        ) {
            is ProposalResult.Ready -> WorkspaceReviewResult.Ready(proposal.proposal)
            is ProposalResult.SourceRejected ->
                WorkspaceReviewResult.SourceRejected(proposal.issues)
            is ProposalResult.ValidationRejected ->
                WorkspaceReviewResult.ValidationRejected(proposal.issues)
        }
    }

    fun approve(proposal: OpenMacroProposal): ApprovalStoreResult<ApprovedRevision> =
        approvals.approve(proposal)
}

sealed interface WorkspaceReviewResult {
    data class Ready(val proposal: OpenMacroProposal) : WorkspaceReviewResult

    data object Missing : WorkspaceReviewResult

    data class SourceRejected(val issues: List<SourceIssue>) : WorkspaceReviewResult

    data class ValidationRejected(val issues: List<ValidationIssue>) : WorkspaceReviewResult

    data class StorageFailure(
        val code: String,
        val message: String,
    ) : WorkspaceReviewResult
}
