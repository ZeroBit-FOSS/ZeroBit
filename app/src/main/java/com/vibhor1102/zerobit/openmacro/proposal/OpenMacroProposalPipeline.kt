/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.proposal

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlan
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSource
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourceResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroYamlReader

class OpenMacroProposalPipeline(
    private val registry: CapabilityRegistry,
) {
    private val compiler = RuntimePlanCompiler(registry)

    fun propose(
        sourceText: String,
        approved: ApprovedMacroSnapshot? = null,
    ): ProposalResult {
        val sourceResult = OpenMacroYamlReader.read(sourceText)
        if (sourceResult is OpenMacroSourceResult.Failure) {
            return ProposalResult.SourceRejected(sourceResult.issues)
        }

        val source = (sourceResult as OpenMacroSourceResult.Success).source
        return when (
            val compilation = compiler.compile(
                document = source.document,
                sourceFingerprint = source.fingerprint,
            )
        ) {
            is PlanCompilationResult.Invalid -> ProposalResult.ValidationRejected(
                source = source,
                issues = compilation.issues,
            )

            is PlanCompilationResult.Success -> {
                val explanation = explain(source.document)
                ProposalResult.Ready(
                    OpenMacroProposal(
                        source = source,
                        explanation = explanation,
                        runtimePlan = compilation.plan,
                        comparison = compare(
                            proposedSource = source,
                            proposedExplanation = explanation,
                            proposedPlan = compilation.plan,
                            approved = approved,
                        ),
                    ),
                )
            }
        }
    }

    private fun explain(document: OpenMacroDocument): MacroExplanation {
        val blocks = buildList {
            addAll(explainBlocks(document.triggers, CapabilityLane.TRIGGER))
            addAll(
                explainBlocks(
                    document.conditionTree?.conditionBlocks() ?: document.conditions,
                    CapabilityLane.CONDITION,
                ),
            )
            addAll(explainBlocks(document.actions, CapabilityLane.ACTION))
        }
        return MacroExplanation(
            macroId = document.metadata.id,
            name = document.metadata.name,
            blocks = blocks,
            requiredPermissions = blocks
                .flatMapTo(mutableSetOf()) { it.requiredPermissions },
        )
    }

    private fun explainBlocks(
        blocks: List<MacroBlock>,
        lane: CapabilityLane,
    ): List<BlockExplanation> = blocks.map { block ->
        val definition = checkNotNull(registry.find(block.type))
        check(definition.lane == lane)
        BlockExplanation(
            blockId = block.id,
            capabilityType = block.type,
            lane = lane,
            displayName = definition.displayName,
            summary = definition.explain(block),
            requiredPermissions = definition.requiredPermissions(block),
        )
    }

    private fun compare(
        proposedSource: OpenMacroSource,
        proposedExplanation: MacroExplanation,
        proposedPlan: RuntimePlan,
        approved: ApprovedMacroSnapshot?,
    ): ProposalComparison {
        if (approved == null) {
            return ProposalComparison(
                sourceChanged = true,
                behaviorChanged = true,
                approvalRequired = true,
                changes = listOf(
                    BehaviorChange(
                        kind = BehaviorChangeKind.NEW_MACRO,
                        after = "Create macro '${proposedExplanation.name}'.",
                    ),
                ),
                permissionsAdded = proposedPlan.requiredPermissions,
                permissionsRemoved = emptySet(),
            )
        }

        val sourceChanged = proposedSource.fingerprint != approved.source.fingerprint
        val changes = buildList {
            if (proposedPlan.macroId != approved.runtimePlan.macroId) {
                add(
                    BehaviorChange(
                        kind = BehaviorChangeKind.MACRO_ID_CHANGED,
                        before = approved.runtimePlan.macroId,
                        after = proposedPlan.macroId,
                    ),
                )
            }
            addAll(
                compareLane(
                    lane = CapabilityLane.TRIGGER,
                    beforeSteps = approved.runtimePlan.triggers,
                    afterSteps = proposedPlan.triggers,
                    beforeExplanation = approved.explanation,
                    afterExplanation = proposedExplanation,
                ),
            )
            if (approved.runtimePlan.conditionTree != proposedPlan.conditionTree) {
                add(
                    BehaviorChange(
                        kind = BehaviorChangeKind.CONDITION_TREE_CHANGED,
                        lane = CapabilityLane.CONDITION,
                        before = "Previous condition logic",
                        after = "Updated condition logic",
                    ),
                )
            }
            addAll(
                compareLane(
                    lane = CapabilityLane.CONDITION,
                    beforeSteps = approved.runtimePlan.conditions,
                    afterSteps = proposedPlan.conditions,
                    beforeExplanation = approved.explanation,
                    afterExplanation = proposedExplanation,
                ),
            )
            addAll(
                compareLane(
                    lane = CapabilityLane.ACTION,
                    beforeSteps = approved.runtimePlan.actions,
                    afterSteps = proposedPlan.actions,
                    beforeExplanation = approved.explanation,
                    afterExplanation = proposedExplanation,
                ),
            )
        }

        val permissionsAdded =
            proposedPlan.requiredPermissions - approved.runtimePlan.requiredPermissions
        val permissionsRemoved =
            approved.runtimePlan.requiredPermissions - proposedPlan.requiredPermissions
        val behaviorChanged = changes.isNotEmpty() ||
            permissionsAdded.isNotEmpty() ||
            permissionsRemoved.isNotEmpty()

        return ProposalComparison(
            sourceChanged = sourceChanged,
            behaviorChanged = behaviorChanged,
            approvalRequired = behaviorChanged,
            changes = changes,
            permissionsAdded = permissionsAdded,
            permissionsRemoved = permissionsRemoved,
        )
    }

    private fun compareLane(
        lane: CapabilityLane,
        beforeSteps: List<RuntimeStep>,
        afterSteps: List<RuntimeStep>,
        beforeExplanation: MacroExplanation,
        afterExplanation: MacroExplanation,
    ): List<BehaviorChange> {
        val beforeById = beforeSteps.associateBy { it.blockId }
        val afterById = afterSteps.associateBy { it.blockId }
        val beforeDescriptions = beforeExplanation.blocksIn(lane).associateBy { it.blockId }
        val afterDescriptions = afterExplanation.blocksIn(lane).associateBy { it.blockId }

        return buildList {
            beforeSteps.filter { it.blockId !in afterById }.forEach { step ->
                add(
                    BehaviorChange(
                        kind = BehaviorChangeKind.BLOCK_REMOVED,
                        lane = lane,
                        blockId = step.blockId,
                        before = beforeDescriptions[step.blockId]?.summary,
                    ),
                )
            }
            afterSteps.filter { it.blockId !in beforeById }.forEach { step ->
                add(
                    BehaviorChange(
                        kind = BehaviorChangeKind.BLOCK_ADDED,
                        lane = lane,
                        blockId = step.blockId,
                        after = afterDescriptions[step.blockId]?.summary,
                    ),
                )
            }
            afterSteps.forEachIndexed { afterIndex, step ->
                if (step.blockId !in beforeById) {
                    return@forEachIndexed
                }
                val beforeStep = beforeById.getValue(step.blockId)
                if (beforeStep != step) {
                    add(
                        BehaviorChange(
                            kind = BehaviorChangeKind.BLOCK_CHANGED,
                            lane = lane,
                            blockId = step.blockId,
                            before = beforeDescriptions[step.blockId]?.summary,
                            after = afterDescriptions[step.blockId]?.summary,
                        ),
                    )
                }
                val beforeIndex = beforeSteps.indexOfFirst { it.blockId == step.blockId }
                if (beforeIndex != afterIndex) {
                    add(
                        BehaviorChange(
                            kind = BehaviorChangeKind.BLOCK_REORDERED,
                            lane = lane,
                            blockId = step.blockId,
                            before = "Position ${beforeIndex + 1}",
                            after = "Position ${afterIndex + 1}",
                        ),
                    )
                }
            }
        }
    }
}

private fun MacroConditionNode.conditionBlocks(): List<MacroBlock> = when (this) {
    is MacroConditionNode.Condition -> listOf(block)
    is MacroConditionNode.All -> children.flatMap { it.conditionBlocks() }
    is MacroConditionNode.Any -> children.flatMap { it.conditionBlocks() }
    is MacroConditionNode.Not -> child.conditionBlocks()
}
