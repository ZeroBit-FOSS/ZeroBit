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

object VibrationAvailabilityCondition : CapabilityDefinition {
    override val type = "android.vibration.availability"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Vibration availability"
    override val description = "Checks whether Android reports vibration hardware."
    override val creation = CapabilityCreation(
        idBase = "vibration-availability",
        defaultConfig = mapOf("state" to MacroValue.Text("available")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose available or unavailable.",
            allowedValues = listOf("available", "unavailable"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("state"), path))
        if (block.expectedVibrationAvailableOrNull() == null) {
            add(
                ValidationIssue(
                    path = "$path.config.state",
                    code = "invalid_vibration_availability",
                    message = "Vibration availability must be 'available' or 'unavailable'.",
                ),
            )
        }
    }

    override fun explain(block: MacroBlock): String =
        if (block.expectedVibrationAvailableOrNull() == false) {
            "Continue only while vibration hardware is unavailable."
        } else {
            "Continue only while vibration hardware is available."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckVibrationAvailability(
            blockId = block.id,
            expectedAvailable = requireNotNull(block.expectedVibrationAvailableOrNull()),
        )
}

internal fun MacroBlock.expectedVibrationAvailableOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "available" -> true
        "unavailable" -> false
        else -> null
    }
