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

object LowRamDeviceCondition : CapabilityDefinition {
    override val type = "android.device.low-ram"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Low-RAM device"
    override val description = "Checks Android's bounded low-RAM device classification."
    override val creation = CapabilityCreation(
        idBase = "low-ram-device",
        defaultConfig = mapOf("state" to MacroValue.Text("low_ram")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "Classification",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose low-RAM or regular device.",
            allowedValues = listOf("low_ram", "regular"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("state"), path))
        if (block.expectedLowRamDeviceOrNull() == null) {
            add(
                ValidationIssue(
                    path = "$path.config.state",
                    code = "invalid_low_ram_device_state",
                    message = "Device RAM classification must be 'low_ram' or 'regular'.",
                ),
            )
        }
    }

    override fun explain(block: MacroBlock): String =
        if (block.expectedLowRamDeviceOrNull() == false) {
            "Continue only on a device Android does not classify as low-RAM."
        } else {
            "Continue only on a device Android classifies as low-RAM."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckLowRamDevice(
            blockId = block.id,
            expectedLowRam = requireNotNull(block.expectedLowRamDeviceOrNull()),
        )
}

internal fun MacroBlock.expectedLowRamDeviceOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "low_ram" -> true
        "regular" -> false
        else -> null
    }
