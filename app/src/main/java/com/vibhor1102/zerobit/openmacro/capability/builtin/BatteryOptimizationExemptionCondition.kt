/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object BatteryOptimizationExemptionCondition : CapabilityDefinition {
    override val type = "android.battery-optimization.exemption"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery optimization exemption"
    override val description = "Checks whether Android exempts ZeroBit from battery optimization."
    override val creation = CapabilityCreation(
        idBase = "battery-optimization-exemption",
        defaultConfig = mapOf("state" to MacroValue.Text("exempt")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose exempt or not exempt.",
            allowedValues = listOf("exempt", "not_exempt"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("state"), path))
        if (block.expectedBatteryOptimizationExemptOrNull() == null) {
            add(
                ValidationIssue(
                    path = "$path.config.state",
                    code = "invalid_battery_optimization_exemption",
                    message = "Battery optimization exemption must be 'exempt' or 'not_exempt'.",
                ),
            )
        }
    }

    override fun explain(block: MacroBlock): String =
        if (block.expectedBatteryOptimizationExemptOrNull() == false) {
            "Continue only while ZeroBit is not exempt from Android battery optimization."
        } else {
            "Continue only while ZeroBit is exempt from Android battery optimization."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckBatteryOptimizationExemption(
            blockId = block.id,
            expectedExempt = requireNotNull(block.expectedBatteryOptimizationExemptOrNull()),
        )
}

internal fun MacroBlock.expectedBatteryOptimizationExemptOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "exempt" -> true
        "not_exempt" -> false
        else -> null
    }
