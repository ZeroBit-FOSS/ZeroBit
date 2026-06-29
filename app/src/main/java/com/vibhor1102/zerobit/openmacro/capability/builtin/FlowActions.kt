/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreationContext
import com.vibhor1102.zerobit.openmacro.capability.CapabilityInsertion
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.ActionGroupFailurePolicy
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object DelayAction : CapabilityDefinition {
    override val type = "openmacro.flow.delay"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Wait"
    override val description = "Waits for a bounded duration before the next action."
    override val creation = CapabilityCreation(
        idBase = "delay",
        defaultConfig = mapOf("milliseconds" to MacroValue.Number(BigDecimal("1000"))),
    )
    override val fields = listOf(
        CapabilityField(
            key = "milliseconds",
            label = "Milliseconds",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Wait from 1 millisecond up to 24 hours.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("milliseconds"), path))
            val value = block.config["milliseconds"]
            when {
                value == null -> add(
                    ValidationIssue(
                        "$path.config.milliseconds",
                        "missing_config",
                        "Configuration 'milliseconds' is required.",
                    ),
                )
                value !is MacroValue.Number -> add(
                    ValidationIssue(
                        "$path.config.milliseconds",
                        "wrong_config_type",
                        "Configuration 'milliseconds' must be a whole number.",
                    ),
                )
                value.value.stripTrailingZeros().scale() > 0 -> add(
                    ValidationIssue(
                        "$path.config.milliseconds",
                        "invalid_delay",
                        "Delay milliseconds must be a whole number.",
                    ),
                )
                value.value < BigDecimal.ONE ||
                    value.value > BigDecimal(MAX_DELAY_MILLIS) -> add(
                    ValidationIssue(
                        "$path.config.milliseconds",
                        "invalid_delay",
                        "Delay must be between 1 and $MAX_DELAY_MILLIS milliseconds.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        "Wait ${(block.config["milliseconds"] as? MacroValue.Number)?.value ?: BigDecimal.ZERO} milliseconds."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.Delay(
            blockId = block.id,
            durationMillis =
                (block.config.getValue("milliseconds") as MacroValue.Number).value.longValueExact(),
        )

    const val MAX_DELAY_MILLIS = 86_400_000L
}

object StopActionsAction : CapabilityDefinition {
    override val type = "openmacro.flow.stop"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Stop actions"
    override val description = "Completes this run without executing later actions."
    override val creation = CapabilityCreation("stop-actions")
    override val fields: List<CapabilityField> = emptyList()

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun explain(block: MacroBlock): String =
        "Stop this run successfully before later actions."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.StopActions(block.id)
}

object StopIfAction : CapabilityDefinition {
    override val type = "openmacro.flow.stop-if"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Stop actions if"
    override val description =
        "Stops later actions when a typed value comparison passes."
    override val creation = CapabilityCreation(
        idBase = "stop-if",
        defaultConfig = defaultValueComparisonConfig(),
    )
    override val fields = ValueCompareCondition.fields

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        ValueCompareCondition.validate(block, path)

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        ValueCompareCondition.validateDocument(block, path, document, registry)

    override fun explain(block: MacroBlock): String =
        ValueCompareCondition.explain(block)
            .replaceFirst("Continue if", "Stop later actions if")

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val comparison = ValueCompareCondition.compile(block) as RuntimeStep.CompareValues
        return RuntimeStep.StopIf(
            blockId = block.id,
            left = comparison.left,
            operator = comparison.operator,
            right = comparison.right,
        )
    }
}

object ActionGroupAction : CapabilityDefinition {
    override val type = "openmacro.action.group"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Action group"
    override val description =
        "Runs a bounded nested list of actions with an explicit failure policy."
    override val creation = CapabilityCreation(
        idBase = "action-group",
        contextConfig = ::starterConfig,
    )
    override val fields = listOf(
        CapabilityField(
            key = "failurePolicy",
            label = "Failure policy",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Use stop to stop this run on failure, or continue to run later actions.",
            allowedValues = listOf("stop", "continue"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("failurePolicy", "actions"), path))
            validateFailurePolicy(block, path)?.let(::add)
            val actions = block.config["actions"]
            when {
                actions == null -> add(
                    ValidationIssue(
                        "$path.config.actions",
                        "missing_config",
                        "Configuration 'actions' is required.",
                    ),
                )
                actions !is MacroValue.ListValue -> add(
                    ValidationIssue(
                        "$path.config.actions",
                        "wrong_config_type",
                        "Configuration 'actions' must be a list of action blocks.",
                    ),
                )
                actions.values.isEmpty() -> add(
                    ValidationIssue(
                        "$path.config.actions",
                        "empty_action_group",
                        "Action groups must contain at least one action.",
                    ),
                )
                actions.values.size > MAX_GROUP_ACTIONS -> add(
                    ValidationIssue(
                        "$path.config.actions",
                        "too_many_group_actions",
                        "Action groups may contain at most $MAX_GROUP_ACTIONS actions.",
                    ),
                )
            }
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        buildList {
            addAll(
                validateNestedActions(
                    block = block,
                    path = path,
                    document = document,
                    registry = registry,
                    depth = 1,
                ),
            )
            val topLevelIds = buildSet {
                addAll(document.triggers.map(MacroBlock::id))
                addAll(document.conditions.map(MacroBlock::id))
                addAll(document.actions.map(MacroBlock::id))
                document.conditionTree?.conditionBlocks()?.mapTo(this, MacroBlock::id)
            }
            nestedActionsDeep(block)
                .filter { it.id in topLevelIds }
                .forEach { child ->
                    add(
                        ValidationIssue(
                            "$path.config.actions",
                            "duplicate_block_id",
                            "Nested action id '${child.id}' is already used by a top-level block.",
                        ),
                    )
                }
            nestedActionsDeep(block)
                .groupBy(MacroBlock::id)
                .filterValues { it.size > 1 }
                .keys
                .forEach { duplicate ->
                    add(
                        ValidationIssue(
                            "$path.config.actions",
                            "duplicate_nested_action_id",
                            "Nested action id '$duplicate' is used more than once in this action group.",
                        ),
                    )
                }
        }

    override fun explain(block: MacroBlock): String {
        val count = nestedActions(block).size
        val policy = block.failurePolicyOrNull()?.name?.lowercase() ?: "invalid"
        return "Run $count grouped action${if (count == 1) "" else "s"} with $policy on failure."
    }

    override fun explain(
        block: MacroBlock,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): String {
        val base = explain(block)
        val childSummaries = nestedActions(block)
            .joinToString("; ") { child ->
                registry.find(child.type)?.explain(child, document, registry) ?: child.type
            }
        return if (childSummaries.isBlank()) {
            base
        } else {
            "$base Includes: $childSummaries"
        }
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun requiredPermissions(
        block: MacroBlock,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): Set<AndroidPermission> = nestedActions(block)
        .flatMapTo(mutableSetOf()) { child ->
            registry.find(child.type)?.requiredPermissions(child, document, registry).orEmpty()
        }

    override fun compile(block: MacroBlock): RuntimeStep =
        error("Action groups require registry-aware compilation.")

    override fun compile(
        block: MacroBlock,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): RuntimeStep =
        RuntimeStep.ActionGroup(
            blockId = block.id,
            failurePolicy = checkNotNull(block.failurePolicyOrNull()),
            actions = nestedActions(block).map { child ->
                checkNotNull(registry.find(child.type)).compile(child, document, registry)
            },
        )

    private fun validateNestedActions(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
        depth: Int,
    ): List<ValidationIssue> {
        val actions = block.config["actions"] as? MacroValue.ListValue ?: return emptyList()
        if (depth > MAX_GROUP_DEPTH) {
            return listOf(
                ValidationIssue(
                    "$path.config.actions",
                    "action_group_too_deep",
                    "Action groups may be nested at most $MAX_GROUP_DEPTH levels.",
                ),
            )
        }
        return buildList {
            actions.values.forEachIndexed { index, value ->
                val childPath = "$path.config.actions[$index]"
                val child = decodeNestedBlock(value, childPath, this) ?: return@forEachIndexed
                if (!CAPABILITY_TYPE_PATTERN.matches(child.type)) {
                    return@forEachIndexed
                }
                val definition = registry.find(child.type)
                when {
                    definition == null -> add(
                        ValidationIssue(
                            "$childPath.type",
                            "unsupported_capability",
                            "This app version does not support '${child.type}'.",
                        ),
                    )
                    definition.lane != CapabilityLane.ACTION -> add(
                        ValidationIssue(
                            "$childPath.type",
                            "wrong_lane",
                            "'${child.type}' belongs in ${definition.lane.name.lowercase()}, not actions.",
                        ),
                    )
                    else -> {
                        addAll(definition.validate(child, childPath))
                        if (definition == ActionGroupAction) {
                            addAll(
                                validateNestedActions(
                                    block = child,
                                    path = childPath,
                                    document = document,
                                    registry = registry,
                                    depth = depth + 1,
                                ),
                            )
                        } else {
                            addAll(definition.validateDocument(child, childPath, document, registry))
                        }
                    }
                }
            }
        }
    }

    private fun validateFailurePolicy(block: MacroBlock, path: String): ValidationIssue? {
        val value = block.config["failurePolicy"]
        return when {
            value == null -> ValidationIssue(
                "$path.config.failurePolicy",
                "missing_config",
                "Configuration 'failurePolicy' is required.",
            )
            value !is MacroValue.Text -> ValidationIssue(
                "$path.config.failurePolicy",
                "wrong_config_type",
                "Configuration 'failurePolicy' must be text.",
            )
            value.value !in setOf("stop", "continue") -> ValidationIssue(
                "$path.config.failurePolicy",
                "invalid_failure_policy",
                "Use 'stop' or 'continue'.",
            )
            else -> null
        }
    }

    private fun nestedActions(block: MacroBlock): List<MacroBlock> =
        (block.config["actions"] as? MacroValue.ListValue)
            ?.values
            ?.mapNotNull { decodeNestedBlock(it, path = "", issues = null) }
            .orEmpty()

    private fun nestedActionsDeep(block: MacroBlock): List<MacroBlock> =
        nestedActions(block).flatMap { child ->
            if (child.type == type) {
                listOf(child) + nestedActionsDeep(child)
            } else {
                listOf(child)
            }
        }

    private fun starterConfig(context: CapabilityCreationContext): Map<String, MacroValue>? {
        val insertion = context.insertion
        if (
            insertion is CapabilityInsertion.ActionGroup &&
            insertion.parentDepth >= MAX_GROUP_DEPTH
        ) {
            return null
        }
        val usedIds = buildSet {
            context.document.triggers.mapTo(this, MacroBlock::id)
            context.document.conditions.mapTo(this, MacroBlock::id)
            context.document.actions.forEach { action ->
                add(action.id)
                nestedActionsDeep(action).mapTo(this, MacroBlock::id)
            }
            context.document.conditionTree?.conditionBlocks()?.mapTo(this, MacroBlock::id)
        }
        val childId = uniqueStarterChildId(usedIds)
        return mapOf(
            "failurePolicy" to MacroValue.Text("stop"),
            "actions" to MacroValue.ListValue(
                listOf(
                    MacroValue.ObjectValue(
                        mapOf(
                            "id" to MacroValue.Text(childId),
                            "type" to MacroValue.Text(StopActionsAction.type),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun uniqueStarterChildId(usedIds: Set<String>): String {
        val base = "group-step"
        if (base !in usedIds) return base
        var suffix = 2
        while ("$base-$suffix" in usedIds) suffix += 1
        return "$base-$suffix"
    }

    private fun decodeNestedBlock(
        value: MacroValue,
        path: String,
        issues: MutableList<ValidationIssue>?,
    ): MacroBlock? {
        val values = (value as? MacroValue.ObjectValue)?.values
        if (values == null) {
            issues?.add(
                ValidationIssue(
                    path,
                    "wrong_config_type",
                    "Nested actions must be objects with id, type, and optional config.",
                ),
            )
            return null
        }
        values.keys
            .filterNot { it in setOf("id", "type", "config") }
            .sorted()
            .forEach { key ->
                issues?.add(
                    ValidationIssue(
                        "$path.$key",
                        "unknown_config",
                        "Nested action key '$key' is not supported.",
                    ),
                )
            }
        val id = (values["id"] as? MacroValue.Text)?.value
        val type = (values["type"] as? MacroValue.Text)?.value
        if (id == null) {
            issues?.add(
                ValidationIssue(
                    "$path.id",
                    "missing_config",
                    "Nested action 'id' is required.",
                ),
            )
        }
        if (type == null) {
            issues?.add(
                ValidationIssue(
                    "$path.type",
                    "missing_config",
                    "Nested action 'type' is required.",
                ),
            )
        }
        if (id != null && !STABLE_ID_PATTERN.matches(id)) {
            issues?.add(
                ValidationIssue(
                    "$path.id",
                    "invalid_id",
                    "Nested action id must be 1-64 lowercase letters, numbers, or hyphens.",
                ),
            )
        }
        if (type != null && !CAPABILITY_TYPE_PATTERN.matches(type)) {
            issues?.add(
                ValidationIssue(
                    "$path.type",
                    "invalid_capability_type",
                    "Nested action type must use a dotted name such as 'android.notification.show'.",
                ),
            )
        }
        val configValue = values["config"]
        val config = when (configValue) {
            null -> emptyMap()
            is MacroValue.ObjectValue -> configValue.values
            else -> {
                issues?.add(
                    ValidationIssue(
                        "$path.config",
                        "wrong_config_type",
                        "Nested action 'config' must be an object.",
                    ),
                )
                emptyMap()
            }
        }
        return if (id != null && type != null) {
            MacroBlock(id = id, type = type, config = config)
        } else {
            null
        }
    }

    private fun MacroBlock.failurePolicyOrNull(): ActionGroupFailurePolicy? =
        when ((config["failurePolicy"] as? MacroValue.Text)?.value) {
            "stop" -> ActionGroupFailurePolicy.STOP
            "continue" -> ActionGroupFailurePolicy.CONTINUE
            else -> null
        }

    private const val MAX_GROUP_ACTIONS = 20
    private const val MAX_GROUP_DEPTH = 4
    private val STABLE_ID_PATTERN = Regex("^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$")
    private val CAPABILITY_TYPE_PATTERN =
        Regex("^[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+$")
}

private fun MacroConditionNode.conditionBlocks(): List<MacroBlock> =
    when (this) {
        is MacroConditionNode.Condition -> listOf(block)
        is MacroConditionNode.All ->
            children.flatMap { it.conditionBlocks() }
        is MacroConditionNode.Any ->
            children.flatMap { it.conditionBlocks() }
        is MacroConditionNode.Not ->
            child.conditionBlocks()
    }
