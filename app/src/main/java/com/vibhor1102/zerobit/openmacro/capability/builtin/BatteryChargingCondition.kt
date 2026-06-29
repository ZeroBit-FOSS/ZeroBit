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

object BatteryChargingCondition : CapabilityDefinition {
    override val type = "android.battery.charging"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery charging"
    override val description = "Checks whether Android reports the battery as charging."
    override val creation = CapabilityCreation(
        idBase = "battery-charging",
        defaultConfig = mapOf("state" to MacroValue.Text("charging")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose charging or not charging.",
            allowedValues = listOf("charging", "not_charging"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedChargingOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_charging_state",
                        message = "Charging state must be 'charging' or 'not_charging'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedChargingOrNull() == false) {
            "Continue only while the battery is not charging."
        } else {
            "Continue only while the battery is charging."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckBatteryCharging(
            blockId = block.id,
            expectedCharging = requireNotNull(block.expectedChargingOrNull()),
        )

    private fun MacroBlock.expectedChargingOrNull(): Boolean? =
        when ((config["state"] as? MacroValue.Text)?.value) {
            "charging" -> true
            "not_charging" -> false
            else -> null
        }
}
