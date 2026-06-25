/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.validation

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument

object OpenMacroValidator {
    const val SUPPORTED_FORMAT = "openmacro/v0.1"

    private val stableIdPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$")
    private val capabilityTypePattern =
        Regex("^[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+$")
    private val variableNamePattern = Regex("^[a-z][a-z0-9_]{0,63}$")
    private val secretKeyPattern =
        Regex("^[a-z0-9](?:[a-z0-9._-]{0,126}[a-z0-9])?$")

    fun validate(
        document: OpenMacroDocument,
        registry: CapabilityRegistry? = null,
    ): List<ValidationIssue> = buildList {
        if (document.format != SUPPORTED_FORMAT) {
            add(
                ValidationIssue(
                    path = "$.format",
                    code = "unsupported_format",
                    message = "Expected format '$SUPPORTED_FORMAT'.",
                ),
            )
        }

        validateStableId(document.metadata.id, "$.metadata.id", "Macro", this)

        if (document.metadata.name.isBlank()) {
            add(
                ValidationIssue(
                    path = "$.metadata.name",
                    code = "blank_name",
                    message = "Macro name must not be blank.",
                ),
            )
        } else if (document.metadata.name.length > 120) {
            add(
                ValidationIssue(
                    path = "$.metadata.name",
                    code = "name_too_long",
                    message = "Macro name must be 120 characters or fewer.",
                ),
            )
        }

        if (document.triggers.isEmpty()) {
            add(
                ValidationIssue(
                    path = "$.triggers",
                    code = "missing_trigger",
                    message = "A macro needs at least one trigger.",
                ),
            )
        }

        if (document.actions.isEmpty()) {
            add(
                ValidationIssue(
                    path = "$.actions",
                    code = "missing_action",
                    message = "A macro needs at least one action.",
                ),
            )
        }

        validateVariables(document, this)

        val locationsById = mutableMapOf<String, String>()
        validateBlocks(
            section = "triggers",
            expectedLane = CapabilityLane.TRIGGER,
            blocks = document.triggers,
            locationsById = locationsById,
            registry = registry,
            document = document,
            issues = this,
        )
        if (document.conditionTree != null && document.conditions.isNotEmpty()) {
            add(
                ValidationIssue(
                    path = "$.condition_tree",
                    code = "mixed_condition_forms",
                    message = "Use either 'conditions' or 'condition_tree', not both.",
                ),
            )
        }
        if (document.conditionTree == null) {
            validateBlocks(
                section = "conditions",
                expectedLane = CapabilityLane.CONDITION,
                blocks = document.conditions,
                locationsById = locationsById,
                registry = registry,
                document = document,
                issues = this,
            )
        } else {
            validateConditionNode(
                node = document.conditionTree,
                path = "$.condition_tree",
                depth = 1,
                locationsById = locationsById,
                registry = registry,
                document = document,
                issues = this,
            )
        }
        validateBlocks(
            section = "actions",
            expectedLane = CapabilityLane.ACTION,
            blocks = document.actions,
            locationsById = locationsById,
            registry = registry,
            document = document,
            issues = this,
        )
    }

    private fun validateVariables(
        document: OpenMacroDocument,
        issues: MutableList<ValidationIssue>,
    ) {
        val locationsByName = mutableMapOf<String, String>()
        document.variables.forEachIndexed { index, variable ->
            val path = "$.variables[$index]"
            if (!variableNamePattern.matches(variable.name)) {
                issues += ValidationIssue(
                    path = "$path.name",
                    code = "invalid_variable_name",
                    message = "Variable names must be 1-64 lowercase letters, numbers, or underscores, and start with a letter.",
                )
            }

            val earlierPath = locationsByName.putIfAbsent(variable.name, path)
            if (earlierPath != null) {
                issues += ValidationIssue(
                    path = "$path.name",
                    code = "duplicate_variable_name",
                    message = "Variable '${variable.name}' is already declared at $earlierPath.",
                )
            }

            if (variable.type == MacroVariableType.SECRET) {
                when {
                    variable.secretKey == null -> issues += ValidationIssue(
                        path = "$path.secret_key",
                        code = "missing_secret_key",
                        message = "Secret variables must declare a local secret key.",
                    )

                    !secretKeyPattern.matches(variable.secretKey) -> issues += ValidationIssue(
                        path = "$path.secret_key",
                        code = "invalid_secret_key",
                        message = "Secret keys must be 1-128 lowercase letters, numbers, dots, underscores, or hyphens.",
                    )
                }
                if (variable.initialValue != null) {
                    issues += ValidationIssue(
                        path = "$path.initial",
                        code = "invalid_secret_initial",
                        message = "Secret values must not be stored in an OpenMacro file.",
                    )
                }
            } else {
                if (variable.secretKey != null) {
                    issues += ValidationIssue(
                        path = "$path.secret_key",
                        code = "invalid_variable_secret_key",
                        message = "Only secret variables may declare a secret key.",
                    )
                }
                val initial = variable.initialValue
                if (initial != null && !variable.type.accepts(initial)) {
                    issues += ValidationIssue(
                        path = "$path.initial",
                        code = "variable_type_mismatch",
                        message = "The initial value does not match variable type '${variable.type.name.lowercase()}'.",
                    )
                }
            }
        }
    }

    private fun validateBlocks(
        section: String,
        expectedLane: CapabilityLane,
        blocks: List<MacroBlock>,
        locationsById: MutableMap<String, String>,
        registry: CapabilityRegistry?,
        document: OpenMacroDocument,
        issues: MutableList<ValidationIssue>,
    ) {
        blocks.forEachIndexed { index, block ->
            val path = "$.$section[$index]"
            validateBlock(
                block = block,
                path = path,
                expectedLane = expectedLane,
                laneLabel = section,
                locationsById = locationsById,
                registry = registry,
                document = document,
                issues = issues,
            )
        }
    }

    private fun validateConditionNode(
        node: MacroConditionNode,
        path: String,
        depth: Int,
        locationsById: MutableMap<String, String>,
        registry: CapabilityRegistry?,
        document: OpenMacroDocument,
        issues: MutableList<ValidationIssue>,
    ) {
        if (depth > MAX_CONDITION_TREE_DEPTH) {
            issues += ValidationIssue(
                path = path,
                code = "condition_tree_too_deep",
                message = "Condition trees may be nested at most $MAX_CONDITION_TREE_DEPTH levels.",
            )
            return
        }
        when (node) {
            is MacroConditionNode.Condition -> validateBlock(
                block = node.block,
                path = "$path.condition",
                expectedLane = CapabilityLane.CONDITION,
                laneLabel = "conditions",
                locationsById = locationsById,
                registry = registry,
                document = document,
                issues = issues,
            )
            is MacroConditionNode.All -> {
                validateConditionGroup(node.children, "$path.all", depth, locationsById, registry, document, issues)
            }
            is MacroConditionNode.Any -> {
                validateConditionGroup(node.children, "$path.any", depth, locationsById, registry, document, issues)
            }
            is MacroConditionNode.Not -> validateConditionNode(
                node.child,
                "$path.not",
                depth + 1,
                locationsById,
                registry,
                document,
                issues,
            )
        }
    }

    private fun validateConditionGroup(
        children: List<MacroConditionNode>,
        path: String,
        depth: Int,
        locationsById: MutableMap<String, String>,
        registry: CapabilityRegistry?,
        document: OpenMacroDocument,
        issues: MutableList<ValidationIssue>,
    ) {
        if (children.isEmpty()) {
            issues += ValidationIssue(
                path = path,
                code = "empty_condition_group",
                message = "Condition groups must contain at least one child.",
            )
        }
        children.forEachIndexed { index, child ->
            validateConditionNode(
                child,
                "$path[$index]",
                depth + 1,
                locationsById,
                registry,
                document,
                issues,
            )
        }
    }

    private fun validateBlock(
        block: MacroBlock,
        path: String,
        expectedLane: CapabilityLane,
        laneLabel: String,
        locationsById: MutableMap<String, String>,
        registry: CapabilityRegistry?,
        document: OpenMacroDocument,
        issues: MutableList<ValidationIssue>,
    ) {
        validateStableId(block.id, "$path.id", "Block", issues)

        val earlierPath = locationsById.putIfAbsent(block.id, path)
        if (earlierPath != null) {
            issues += ValidationIssue(
                path = "$path.id",
                code = "duplicate_block_id",
                message = "Block id '${block.id}' is already used at $earlierPath.",
            )
        }

        if (!capabilityTypePattern.matches(block.type)) {
            issues += ValidationIssue(
                path = "$path.type",
                code = "invalid_capability_type",
                message = "Capability type must use a dotted name such as 'android.notification.show'.",
            )
            return
        }

        if (registry != null) {
            val definition = registry.find(block.type)
            when {
                definition == null -> issues += ValidationIssue(
                    path = "$path.type",
                    code = "unsupported_capability",
                    message = "This app version does not support '${block.type}'.",
                )

                definition.lane != expectedLane -> issues += ValidationIssue(
                    path = "$path.type",
                    code = "wrong_lane",
                    message = "'${block.type}' belongs in ${definition.lane.name.lowercase()}, not $laneLabel.",
                )

                else -> {
                    issues += definition.validate(block, path)
                    issues += definition.validateDocument(block, path, document, registry)
                }
            }
        }
    }

    private fun validateStableId(
        value: String,
        path: String,
        label: String,
        issues: MutableList<ValidationIssue>,
    ) {
        if (!stableIdPattern.matches(value)) {
            issues += ValidationIssue(
                path = path,
                code = "invalid_id",
                message = "$label id must be 1-64 lowercase letters, numbers, or hyphens.",
            )
        }
    }

    private const val MAX_CONDITION_TREE_DEPTH = 32
}

private fun MacroVariableType.accepts(value: MacroValue): Boolean = when (this) {
    MacroVariableType.TEXT -> value is MacroValue.Text
    MacroVariableType.NUMBER -> value is MacroValue.Number
    MacroVariableType.BOOLEAN -> value is MacroValue.Boolean
    MacroVariableType.SECRET -> false
}

data class ValidationIssue(
    val path: String,
    val code: String,
    val message: String,
)
