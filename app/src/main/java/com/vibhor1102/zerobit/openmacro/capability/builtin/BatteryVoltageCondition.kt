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
import com.vibhor1102.zerobit.openmacro.runtime.BatteryVoltageComparison
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object BatteryVoltageCondition : CapabilityDefinition {
    override val type = "android.battery.voltage"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery voltage"
    override val description = "Compares current battery voltage in exact millivolts."
    override val creation = CapabilityCreation(
        idBase = "battery-voltage",
        defaultConfig = mapOf(
            "millivolts" to MacroValue.Number(BigDecimal("4000")),
            "comparison" to MacroValue.Text("below"),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "millivolts",
            label = "Millivolts",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Whole millivolts from 0 to 20000.",
        ),
        CapabilityField(
            key = "comparison",
            label = "Comparison",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose below, above, or equals.",
            allowedValues = listOf("below", "above", "equals"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("millivolts", "comparison"), path))
            if (block.batteryVoltageThresholdOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config",
                        code = "invalid_battery_voltage_threshold",
                        message = "Battery voltage requires whole millivolts from 0 to 20000 and a valid comparison.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val threshold = block.batteryVoltageThresholdOrNull()
            ?: return "Check an invalid battery voltage threshold."
        val comparison = when (threshold.comparison) {
            BatteryVoltageComparison.BELOW -> "below"
            BatteryVoltageComparison.ABOVE -> "above"
            BatteryVoltageComparison.EQUALS -> "equal to"
        }
        return "Continue while battery voltage is $comparison ${threshold.millivolts} mV."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val threshold = requireNotNull(block.batteryVoltageThresholdOrNull())
        return RuntimeStep.CheckBatteryVoltage(
            blockId = block.id,
            thresholdMillivolts = threshold.millivolts,
            comparison = threshold.comparison,
        )
    }
}

internal data class BatteryVoltageThreshold(
    val millivolts: Int,
    val comparison: BatteryVoltageComparison,
)

internal fun MacroBlock.batteryVoltageThresholdOrNull(): BatteryVoltageThreshold? {
    val millivolts = (config["millivolts"] as? MacroValue.Number)
        ?.value
        ?.toIntExactOrNull()
        ?.takeIf { it in 0..20_000 }
        ?: return null
    val comparison = when ((config["comparison"] as? MacroValue.Text)?.value) {
        "below" -> BatteryVoltageComparison.BELOW
        "above" -> BatteryVoltageComparison.ABOVE
        "equals" -> BatteryVoltageComparison.EQUALS
        else -> return null
    }
    return BatteryVoltageThreshold(millivolts, comparison)
}

private fun BigDecimal.toIntExactOrNull(): Int? =
    runCatching { intValueExact() }.getOrNull()
