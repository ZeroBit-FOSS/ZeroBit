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
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object AirplaneModeTrigger : CapabilityDefinition {
    override val type = "android.airplane-mode.changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Airplane mode changed"
    override val description = "Starts when airplane mode changes to the selected state."
    override val creation = CapabilityCreation(
        idBase = "airplane-mode-changed",
        defaultConfig = mapOf("state" to MacroValue.Text("enabled")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "New State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose enabled or disabled.",
            allowedValues = listOf("enabled", "disabled"),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "airplane_mode.state",
            type = MacroVariableType.TEXT,
            description = "The airplane mode state that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedAirplaneModeEnabledOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_airplane_mode_trigger_state",
                        message = "Airplane mode trigger state must be 'enabled' or 'disabled'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedAirplaneModeEnabledOrNull() == false) {
            "Start when airplane mode becomes disabled."
        } else {
            "Start when airplane mode becomes enabled."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveAirplaneMode(
            blockId = block.id,
            expectedEnabled = requireNotNull(block.expectedAirplaneModeEnabledOrNull()),
        )
}

private fun MacroBlock.expectedAirplaneModeEnabledOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "enabled" -> true
        "disabled" -> false
        else -> null
    }
