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
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object BatteryHealthTrigger : CapabilityDefinition {
    override val type = "android.battery.health-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery health changed"
    override val description = "Starts when Android changes to the selected battery health state."
    override val creation = CapabilityCreation(
        idBase = "battery-health-changed",
        defaultConfig = mapOf("health" to MacroValue.Text("healthy")),
    )
    override val fields = BatteryHealthCondition.fields
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery.health",
            type = MacroVariableType.TEXT,
            description = "The canonical battery health state that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("health"), path))
            if (block.batteryHealthOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.health",
                        code = "invalid_battery_health_trigger",
                        message = "Battery health trigger must be healthy, overheating, cold, dead, over_voltage, or failure.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val health = block.batteryHealthOrNull()
            ?: return "Start on an invalid battery health state."
        return "Start when battery health changes to ${health.sourceName}."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveBatteryHealth(
            blockId = block.id,
            expectedHealth = requireNotNull(block.batteryHealthOrNull()),
        )
}
