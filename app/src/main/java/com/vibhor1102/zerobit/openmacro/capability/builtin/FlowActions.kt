/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object DelayAction : CapabilityDefinition {
    override val type = "openmacro.flow.delay"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Wait"
    override val description = "Waits for a bounded duration before the next action."
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
