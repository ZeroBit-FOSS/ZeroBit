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

object BatteryStatusTrigger : CapabilityDefinition {
    override val type = "android.battery.status-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery status changed"
    override val description = "Starts when Android changes to the selected battery status."
    override val creation = CapabilityCreation(
        idBase = "battery-status-changed",
        defaultConfig = mapOf("status" to MacroValue.Text("charging")),
    )
    override val fields = BatteryStatusCondition.fields
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery.status",
            type = MacroVariableType.TEXT,
            description = "The canonical battery status that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("status"), path))
            if (block.batteryStatusOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.status",
                        code = "invalid_battery_status_trigger",
                        message = "Battery status trigger must be charging, full, discharging, or not_charging.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val status = block.batteryStatusOrNull()
            ?: return "Start on an invalid battery status."
        return "Start when battery status changes to ${status.sourceName}."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveBatteryStatus(
            blockId = block.id,
            expectedStatus = requireNotNull(block.batteryStatusOrNull()),
        )
}
