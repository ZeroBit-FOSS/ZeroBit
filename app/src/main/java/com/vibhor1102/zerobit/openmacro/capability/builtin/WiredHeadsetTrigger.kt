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

object WiredHeadsetTrigger : CapabilityDefinition {
    override val type = "android.audio.wired-headset-state-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Wired headset changed"
    override val description = "Starts when a wired or USB headset connects or disconnects."
    override val creation = CapabilityCreation(
        idBase = "wired-headset-changed",
        defaultConfig = mapOf("state" to MacroValue.Text("connected")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "New state",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose connected or disconnected.",
            allowedValues = listOf("connected", "disconnected"),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "wired_headset.state",
            type = MacroVariableType.TEXT,
            description = "The wired headset state that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedWiredHeadsetConnectedOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_wired_headset_trigger_state",
                        message = "Wired headset trigger state must be 'connected' or 'disconnected'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedWiredHeadsetConnectedOrNull() == false) {
            "Start when the last wired headset disconnects."
        } else {
            "Start when a wired headset connects."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveWiredHeadset(
            blockId = block.id,
            expectedConnected = requireNotNull(block.expectedWiredHeadsetConnectedOrNull()),
        )
}
