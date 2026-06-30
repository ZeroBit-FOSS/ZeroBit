/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.BatteryTemperatureComparison
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object BatteryTemperatureTrigger : CapabilityDefinition {
    override val type = "android.battery.temperature-crossing"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery temperature"
    override val description = "Starts when battery temperature crosses a Celsius threshold."
    override val creation = CapabilityCreation(
        idBase = "battery-temperature",
        defaultConfig = mapOf(
            "celsius" to MacroValue.Number(BigDecimal("40")),
            "comparison" to MacroValue.Text("above"),
        ),
    )
    override val fields = BatteryTemperatureCondition.fields
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery.temperature_celsius",
            type = MacroVariableType.NUMBER,
            description = "The exact battery temperature that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("celsius", "comparison"), path))
            if (block.batteryTemperatureThresholdOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config",
                        code = "invalid_battery_temperature_trigger",
                        message = "Battery temperature trigger requires -100.0 to 100.0 Celsius with at most one decimal place and a valid comparison.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val threshold = block.batteryTemperatureThresholdOrNull()
            ?: return "Start on an invalid battery temperature threshold."
        val comparison = when (threshold.comparison) {
            BatteryTemperatureComparison.BELOW -> "below"
            BatteryTemperatureComparison.ABOVE -> "above"
            BatteryTemperatureComparison.EQUALS -> "equal to"
        }
        return "Start when battery temperature crosses $comparison ${threshold.displayCelsius} C."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val threshold = requireNotNull(block.batteryTemperatureThresholdOrNull())
        return RuntimeStep.ObserveBatteryTemperature(
            blockId = block.id,
            thresholdTenthsCelsius = threshold.tenthsCelsius,
            comparison = threshold.comparison,
        )
    }
}
