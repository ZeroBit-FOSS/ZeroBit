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
import com.vibhor1102.zerobit.openmacro.runtime.BatteryVoltageComparison
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object BatteryVoltageTrigger : CapabilityDefinition {
    override val type = "android.battery.voltage-crossing"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery voltage"
    override val description = "Starts when exact battery millivolts cross a threshold."
    override val creation = CapabilityCreation(
        idBase = "battery-voltage",
        defaultConfig = mapOf(
            "millivolts" to MacroValue.Number(BigDecimal("4000")),
            "comparison" to MacroValue.Text("below"),
        ),
    )
    override val fields = BatteryVoltageCondition.fields
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery.voltage_millivolts",
            type = MacroVariableType.NUMBER,
            description = "The exact bounded millivolts that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("millivolts", "comparison"), path))
            if (block.batteryVoltageThresholdOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config",
                        code = "invalid_battery_voltage_trigger",
                        message = "Battery voltage trigger requires whole millivolts from 0 to 20000 and a valid comparison.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val threshold = block.batteryVoltageThresholdOrNull()
            ?: return "Start on an invalid battery voltage threshold."
        val comparison = when (threshold.comparison) {
            BatteryVoltageComparison.BELOW -> "below"
            BatteryVoltageComparison.ABOVE -> "above"
            BatteryVoltageComparison.EQUALS -> "equal to"
        }
        return "Start when battery voltage crosses $comparison ${threshold.millivolts} mV."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val threshold = requireNotNull(block.batteryVoltageThresholdOrNull())
        return RuntimeStep.ObserveBatteryVoltage(
            blockId = block.id,
            thresholdMillivolts = threshold.millivolts,
            comparison = threshold.comparison,
        )
    }
}
