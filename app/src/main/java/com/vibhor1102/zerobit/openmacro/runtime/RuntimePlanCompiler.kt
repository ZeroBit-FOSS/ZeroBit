/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.validation.OpenMacroValidator
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

class RuntimePlanCompiler(
    private val registry: CapabilityRegistry,
) {
    fun compile(
        document: OpenMacroDocument,
        sourceFingerprint: String,
    ): PlanCompilationResult {
        val issues = OpenMacroValidator.validate(document, registry)
        if (issues.isNotEmpty()) {
            return PlanCompilationResult.Invalid(issues)
        }

        val permissions = mutableSetOf<AndroidPermission>()
        val triggers = compileBlocks(document.triggers, CapabilityLane.TRIGGER, permissions)
        val conditions = compileBlocks(document.conditions, CapabilityLane.CONDITION, permissions)
        val actions = compileBlocks(document.actions, CapabilityLane.ACTION, permissions)

        return PlanCompilationResult.Success(
            RuntimePlan(
                macroId = document.metadata.id,
                sourceFingerprint = sourceFingerprint,
                triggers = triggers,
                conditions = conditions,
                actions = actions,
                requiredPermissions = permissions,
            ),
        )
    }

    private fun compileBlocks(
        blocks: List<MacroBlock>,
        expectedLane: CapabilityLane,
        permissions: MutableSet<AndroidPermission>,
    ): List<RuntimeStep> = blocks.map { block ->
        val definition = checkNotNull(registry.find(block.type))
        check(definition.lane == expectedLane)
        permissions += definition.requiredPermissions(block)
        definition.compile(block)
    }
}

sealed interface PlanCompilationResult {
    data class Success(val plan: RuntimePlan) : PlanCompilationResult

    data class Invalid(val issues: List<ValidationIssue>) : PlanCompilationResult
}
