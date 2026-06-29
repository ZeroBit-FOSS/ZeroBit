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
import com.vibhor1102.zerobit.openmacro.runtime.PowerSource
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object PowerConnectionCondition : CapabilityDefinition {
    override val type = "android.power.connection-state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Power connection"
    override val description = "Checks whether the device is plugged into a power source."
    override val creation = CapabilityCreation(
        idBase = "power-connection",
        defaultConfig = mapOf(
            "state" to MacroValue.Text("plugged_in"),
            "source" to MacroValue.Text("any"),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose plugged in or unplugged.",
            allowedValues = listOf("plugged_in", "unplugged"),
        ),
        CapabilityField(
            key = "source",
            label = "Power Source",
            kind = CapabilityFieldKind.TEXT,
            required = false,
            help = "Optionally require AC, USB, wireless, or dock power.",
            allowedValues = listOf("any", "ac", "usb", "wireless", "dock"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state", "source"), path))
            if (block.expectedPluggedInOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_power_connection_state",
                        message = "Power connection state must be 'plugged_in' or 'unplugged'.",
                    ),
                )
            }
            val source = block.config["source"]
            if (
                source != null &&
                (source !is MacroValue.Text || source.value !in POWER_SOURCE_VALUES)
            ) {
                add(
                    ValidationIssue(
                        path = "$path.config.source",
                        code = "invalid_power_source",
                        message = "Power source must be 'any', 'ac', 'usb', 'wireless', or 'dock'.",
                    ),
                )
            } else if (
                block.expectedPluggedInOrNull() == false &&
                source is MacroValue.Text &&
                source.value != "any"
            ) {
                add(
                    ValidationIssue(
                        path = "$path.config.source",
                        code = "power_source_requires_plugged_in",
                        message = "A specific power source can only be used with 'plugged_in'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val source = block.expectedPowerSourceOrNull()
        return if (block.expectedPluggedInOrNull() == false) {
            "Continue only while the device is unplugged."
        } else if (source != null) {
            "Continue only while the device is connected to ${source.explanationName()}."
        } else {
            "Continue only while the device is plugged into power."
        }
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckPowerConnection(
            blockId = block.id,
            expectedPluggedIn = requireNotNull(block.expectedPluggedInOrNull()),
            expectedSource = block.expectedPowerSourceOrNull(),
        )

    private fun MacroBlock.expectedPluggedInOrNull(): Boolean? =
        when ((config["state"] as? MacroValue.Text)?.value) {
            "plugged_in" -> true
            "unplugged" -> false
            else -> null
        }

    private fun MacroBlock.expectedPowerSourceOrNull(): PowerSource? =
        when ((config["source"] as? MacroValue.Text)?.value) {
            "ac" -> PowerSource.AC
            "usb" -> PowerSource.USB
            "wireless" -> PowerSource.WIRELESS
            "dock" -> PowerSource.DOCK
            else -> null
        }

    private fun PowerSource.explanationName(): String = when (this) {
        PowerSource.AC -> "AC power"
        PowerSource.USB -> "USB power"
        PowerSource.WIRELESS -> "wireless power"
        PowerSource.DOCK -> "dock power"
    }

    private val POWER_SOURCE_VALUES = setOf("any", "ac", "usb", "wireless", "dock")
}
