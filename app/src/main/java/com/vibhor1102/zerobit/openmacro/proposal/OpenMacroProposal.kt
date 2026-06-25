/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.proposal

import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlan
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSource
import com.vibhor1102.zerobit.openmacro.source.SourceIssue
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

data class OpenMacroProposal(
    val source: OpenMacroSource,
    val explanation: MacroExplanation,
    val runtimePlan: RuntimePlan,
    val comparison: ProposalComparison,
)

data class ApprovedMacroSnapshot(
    val source: OpenMacroSource,
    val explanation: MacroExplanation,
    val runtimePlan: RuntimePlan,
) {
    companion object {
        fun from(proposal: OpenMacroProposal): ApprovedMacroSnapshot =
            ApprovedMacroSnapshot(
                source = proposal.source,
                explanation = proposal.explanation,
                runtimePlan = proposal.runtimePlan,
            )
    }
}

sealed interface ProposalResult {
    data class SourceRejected(
        val issues: List<SourceIssue>,
    ) : ProposalResult

    data class ValidationRejected(
        val source: OpenMacroSource,
        val issues: List<ValidationIssue>,
    ) : ProposalResult

    data class Ready(
        val proposal: OpenMacroProposal,
    ) : ProposalResult
}
