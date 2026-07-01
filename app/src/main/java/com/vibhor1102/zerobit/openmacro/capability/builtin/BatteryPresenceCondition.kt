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

object BatteryPresenceCondition : CapabilityDefinition {
    override val type = "android.battery.presence"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery presence"
    override val description = "Checks whether Android reports a physical battery present."
    override val creation = CapabilityCreation(
        idBase = "battery-presence",
        defaultConfig = mapOf("state" to MacroValue.Text("present")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose present or not present.",
            allowedValues = listOf("present", "not_present"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedBatteryPresentOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_battery_presence",
                        message = "Battery presence must be 'present' or 'not_present'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedBatteryPresentOrNull() == false) {
            "Continue only while Android reports no physical battery."
        } else {
            "Continue only while Android reports a physical battery present."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckBatteryPresent(
            blockId = block.id,
            expectedPresent = requireNotNull(block.expectedBatteryPresentOrNull()),
        )
}

internal fun MacroBlock.expectedBatteryPresentOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "present" -> true
        "not_present" -> false
        else -> null
    }
