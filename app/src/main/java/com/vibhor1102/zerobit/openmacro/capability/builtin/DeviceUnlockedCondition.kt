/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object DeviceUnlockedCondition : CapabilityDefinition {
    override val type = "android.device.unlocked"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Device lock state"
    override val description = "Checks whether the device is currently locked or unlocked."
    override val creation = CapabilityCreation(
        idBase = "device-unlocked",
        defaultConfig = mapOf("state" to MacroValue.Text("unlocked")),
    )
    override val fields: List<CapabilityField> = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = false,
            help = "Choose locked or unlocked. Existing macros without this field mean unlocked.",
            allowedValues = listOf("unlocked", "locked"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedUnlockedOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_device_lock_state",
                        message = "Device lock state must be 'unlocked' or 'locked'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedUnlockedOrNull() == false) {
            "Continue only if the phone is locked."
        } else {
            "Continue only if the phone is unlocked."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckDeviceUnlocked(
            blockId = block.id,
            expectedUnlocked = requireNotNull(block.expectedUnlockedOrNull()),
        )

    private fun MacroBlock.expectedUnlockedOrNull(): Boolean? =
        when (val state = config["state"]) {
            null -> true
            is MacroValue.Text -> when (state.value) {
                "unlocked" -> true
                "locked" -> false
                else -> null
            }
            else -> null
        }
}
