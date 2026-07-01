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

object DeviceIdleModeTrigger : CapabilityDefinition {
    override val type = "android.device.idle-mode-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Device idle mode changed"
    override val description = "Starts when Android changes to the selected device idle state."
    override val creation = CapabilityCreation(
        idBase = "device-idle-mode-changed",
        defaultConfig = mapOf("state" to MacroValue.Text("idle")),
    )
    override val fields = DeviceIdleModeCondition.fields
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "device_idle.state",
            type = MacroVariableType.TEXT,
            description = "The canonical device idle state that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("state"), path))
        if (block.expectedDeviceIdleOrNull() == null) {
            add(
                ValidationIssue(
                    path = "$path.config.state",
                    code = "invalid_device_idle_trigger_state",
                    message = "Device idle trigger state must be 'idle' or 'not_idle'.",
                ),
            )
        }
    }

    override fun explain(block: MacroBlock): String =
        if (block.expectedDeviceIdleOrNull() == false) {
            "Start when Android leaves device idle mode."
        } else {
            "Start when Android enters device idle mode."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveDeviceIdleMode(
            blockId = block.id,
            expectedIdle = requireNotNull(block.expectedDeviceIdleOrNull()),
        )
}
