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

object MicrophoneAvailabilityCondition : CapabilityDefinition {
    override val type = "android.microphone.availability"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Microphone availability"
    override val description = "Checks Android's microphone hardware feature."
    override val creation = CapabilityCreation(
        idBase = "microphone-availability",
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
        if (block.expectedMicrophoneAvailableOrNull() == null) {
            add(
                ValidationIssue(
                    path = "$path.config.state",
                    code = "invalid_microphone_availability",
                    message = "Microphone availability must be 'available' or 'unavailable'.",
                ),
            )
        }
    }

    override fun explain(block: MacroBlock): String =
        if (block.expectedMicrophoneAvailableOrNull() == false) {
            "Continue only while microphone hardware is unavailable."
        } else {
            "Continue only while microphone hardware is available."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckMicrophoneAvailability(
            blockId = block.id,
            expectedAvailable = requireNotNull(block.expectedMicrophoneAvailableOrNull()),
        )
}

internal fun MacroBlock.expectedMicrophoneAvailableOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "available" -> true
        "unavailable" -> false
        else -> null
    }
