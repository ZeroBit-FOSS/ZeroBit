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

object TelephonyAvailabilityCondition : CapabilityDefinition {
    override val type = "android.telephony.availability"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Telephony availability"
    override val description = "Checks Android's general telephony hardware feature."
    override val creation = CapabilityCreation(
        idBase = "telephony-availability",
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
        if (block.expectedTelephonyAvailableOrNull() == null) {
            add(
                ValidationIssue(
                    path = "$path.config.state",
                    code = "invalid_telephony_availability",
                    message = "Telephony availability must be 'available' or 'unavailable'.",
                ),
            )
        }
    }

    override fun explain(block: MacroBlock): String =
        if (block.expectedTelephonyAvailableOrNull() == false) {
            "Continue only while general telephony hardware is unavailable."
        } else {
            "Continue only while general telephony hardware is available."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckTelephonyAvailability(
            blockId = block.id,
            expectedAvailable = requireNotNull(block.expectedTelephonyAvailableOrNull()),
        )
}

internal fun MacroBlock.expectedTelephonyAvailableOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "available" -> true
        "unavailable" -> false
        else -> null
    }
