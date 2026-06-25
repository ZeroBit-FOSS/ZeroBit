/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.BatteryDirection
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object BatteryLevelTrigger : CapabilityDefinition {
    override val type = "android.battery.level"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery level"
    override val description = "Starts when the battery level reaches, drops below, or rises above a percentage."
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery.percentage",
            type = MacroVariableType.NUMBER,
            description = "The battery percentage that caused this run.",
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "level",
            label = "Battery Level (%)",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "The target battery percentage (1 to 100).",
        ),
        CapabilityField(
            key = "direction",
            label = "Direction",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Trigger condition: 'goes_below', 'goes_above', or 'equals'.",
            allowedValues = listOf("goes_below", "goes_above", "equals"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("level", "direction"), path))

            val levelVal = block.config["level"]
            if (levelVal == null) {
                add(ValidationIssue("$path.config.level", "missing_config", "Configuration 'level' is required."))
            } else if (levelVal !is MacroValue.Number) {
                add(ValidationIssue("$path.config.level", "wrong_config_type", "Configuration 'level' must be a number."))
            } else {
                val intVal = levelVal.value.toInt()
                if (intVal < 1 || intVal > 100) {
                    add(ValidationIssue("$path.config.level", "invalid_battery_level", "Battery level must be between 1 and 100."))
                }
            }

            val directionVal = block.config["direction"]
            if (directionVal == null) {
                add(ValidationIssue("$path.config.direction", "missing_config", "Configuration 'direction' is required."))
            } else if (directionVal !is MacroValue.Text) {
                add(ValidationIssue("$path.config.direction", "wrong_config_type", "Configuration 'direction' must be text."))
            } else {
                val dirStr = directionVal.value
                if (dirStr != "goes_below" && dirStr != "goes_above" && dirStr != "equals") {
                    add(ValidationIssue("$path.config.direction", "invalid_battery_direction", "Direction must be 'goes_below', 'goes_above', or 'equals'."))
                }
            }
        }

    override fun explain(block: MacroBlock): String {
        val level = (block.config["level"] as? MacroValue.Number)?.value?.toInt() ?: 50
        val direction = (block.config["direction"] as? MacroValue.Text)?.value ?: "equals"
        val directionWord = when (direction) {
            "goes_below" -> "drops below"
            "goes_above" -> "rises above"
            else -> "reaches"
        }
        return "Start when the battery level $directionWord $level%."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val level = (block.config.getValue("level") as MacroValue.Number).value.toInt()
        val directionStr = (block.config.getValue("direction") as MacroValue.Text).value
        val direction = when (directionStr) {
            "goes_below" -> BatteryDirection.GOES_BELOW
            "goes_above" -> BatteryDirection.GOES_ABOVE
            else -> BatteryDirection.EQUALS
        }
        return RuntimeStep.ObserveBatteryLevel(
            blockId = block.id,
            level = level,
            direction = direction,
        )
    }
}
