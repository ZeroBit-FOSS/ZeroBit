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

object DeviceIdleModeCondition : CapabilityDefinition {
    override val type = "android.device.idle-mode"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Device idle mode"
    override val description = "Checks whether Android is currently in device idle mode."
    override val creation = CapabilityCreation(
        idBase = "device-idle-mode",
        defaultConfig = mapOf("state" to MacroValue.Text("idle")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose idle or not idle.",
            allowedValues = listOf("idle", "not_idle"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("state"), path))
        if (block.expectedDeviceIdleOrNull() == null) {
            add(
                ValidationIssue(
                    path = "$path.config.state",
                    code = "invalid_device_idle_state",
                    message = "Device idle state must be 'idle' or 'not_idle'.",
                ),
            )
        }
    }

    override fun explain(block: MacroBlock): String =
        if (block.expectedDeviceIdleOrNull() == false) {
            "Continue only while Android is not in device idle mode."
        } else {
            "Continue only while Android is in device idle mode."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckDeviceIdleMode(
            blockId = block.id,
            expectedIdle = requireNotNull(block.expectedDeviceIdleOrNull()),
        )
}

internal fun MacroBlock.expectedDeviceIdleOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "idle" -> true
        "not_idle" -> false
        else -> null
    }
