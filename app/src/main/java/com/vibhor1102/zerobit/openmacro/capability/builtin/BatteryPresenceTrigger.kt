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

object BatteryPresenceTrigger : CapabilityDefinition {
    override val type = "android.battery.presence-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery presence changed"
    override val description = "Starts when Android's physical battery presence changes."
    override val creation = CapabilityCreation(
        idBase = "battery-presence-changed",
        defaultConfig = mapOf("state" to MacroValue.Text("present")),
    )
    override val fields = BatteryPresenceCondition.fields
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery.presence",
            type = MacroVariableType.TEXT,
            description = "The canonical battery presence that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedBatteryPresentOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_battery_presence_trigger",
                        message = "Battery presence trigger must be 'present' or 'not_present'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedBatteryPresentOrNull() == false) {
            "Start when Android reports the physical battery is no longer present."
        } else {
            "Start when Android reports a physical battery present."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveBatteryPresence(
            blockId = block.id,
            expectedPresent = requireNotNull(block.expectedBatteryPresentOrNull()),
        )
}
