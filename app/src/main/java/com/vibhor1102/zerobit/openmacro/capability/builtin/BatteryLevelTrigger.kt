/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object BatteryLevelTrigger : CapabilityDefinition {
    override val type = "android.battery.level"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery level"
    override val description = "Starts when the battery level reaches, drops below, or rises above a percentage."
    override val creation = CapabilityCreation(
        idBase = "battery-level",
        defaultConfig = mapOf(
            "level" to MacroValue.Number(java.math.BigDecimal("50")),
            "direction" to MacroValue.Text("equals"),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery.percentage",
            type = MacroVariableType.NUMBER,
            description = "The battery percentage that caused this run.",
        ),
    )
    override val fields = batteryLevelFields()

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("level", "direction"), path))

            if (block.batteryThresholdOrNull() == null) {
                add(
                    ValidationIssue(
                        "$path.config",
                        "invalid_battery_threshold",
                        "Battery threshold requires a whole level from 1 to 100 and a valid direction.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val threshold = block.batteryThresholdOrNull()
            ?: return "Start when the battery reaches an invalid threshold."
        return "Start when the battery crosses ${threshold.direction.explanationWord()} ${threshold.level}%."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val threshold = requireNotNull(block.batteryThresholdOrNull())
        return RuntimeStep.ObserveBatteryLevel(
            blockId = block.id,
            level = threshold.level,
            direction = threshold.direction,
        )
    }
}
