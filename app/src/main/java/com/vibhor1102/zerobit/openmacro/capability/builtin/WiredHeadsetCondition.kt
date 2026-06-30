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

object WiredHeadsetCondition : CapabilityDefinition {
    override val type = "android.audio.wired-headset-state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Wired headset"
    override val description = "Checks whether a wired or USB headset output is connected."
    override val creation = CapabilityCreation(
        idBase = "wired-headset",
        defaultConfig = mapOf("state" to MacroValue.Text("connected")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose connected or disconnected.",
            allowedValues = listOf("connected", "disconnected"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedWiredHeadsetConnectedOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_wired_headset_state",
                        message = "Wired headset state must be 'connected' or 'disconnected'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedWiredHeadsetConnectedOrNull() == false) {
            "Continue only while no wired headset is connected."
        } else {
            "Continue only while a wired headset is connected."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckWiredHeadsetConnected(
            blockId = block.id,
            expectedConnected = requireNotNull(block.expectedWiredHeadsetConnectedOrNull()),
        )
}

internal fun MacroBlock.expectedWiredHeadsetConnectedOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "connected" -> true
        "disconnected" -> false
        else -> null
    }
