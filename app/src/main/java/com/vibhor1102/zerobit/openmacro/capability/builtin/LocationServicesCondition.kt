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

object LocationServicesCondition : CapabilityDefinition {
    override val type = "android.location.services-state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Location services"
    override val description = "Checks whether Android location services are enabled or disabled."
    override val creation = CapabilityCreation(
        idBase = "location-services",
        defaultConfig = mapOf("state" to MacroValue.Text("enabled")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose enabled or disabled.",
            allowedValues = listOf("enabled", "disabled"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedLocationServicesEnabledOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_location_services_state",
                        message = "Location services state must be 'enabled' or 'disabled'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedLocationServicesEnabledOrNull() == false) {
            "Continue only while location services are disabled."
        } else {
            "Continue only while location services are enabled."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckLocationServicesEnabled(
            blockId = block.id,
            expectedEnabled = requireNotNull(block.expectedLocationServicesEnabledOrNull()),
        )
}

internal fun MacroBlock.expectedLocationServicesEnabledOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "enabled" -> true
        "disabled" -> false
        else -> null
    }
