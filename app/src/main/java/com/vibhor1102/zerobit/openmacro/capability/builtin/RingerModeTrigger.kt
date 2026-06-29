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
import com.vibhor1102.zerobit.openmacro.runtime.RingerMode
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object RingerModeTrigger : CapabilityDefinition {
    override val type = "android.ringer-mode.changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Ringer mode changed"
    override val description = "Starts when Android changes to the selected ringer mode."
    override val creation = CapabilityCreation(
        idBase = "ringer-mode-changed",
        defaultConfig = mapOf("mode" to MacroValue.Text("normal")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "mode",
            label = "New Mode",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose normal, vibrate, or silent.",
            allowedValues = listOf("normal", "vibrate", "silent"),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "ringer.state",
            type = MacroVariableType.TEXT,
            description = "The ringer mode that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("mode"), path))
            if (block.ringerModeOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.mode",
                        code = "invalid_ringer_mode_trigger",
                        message = "Ringer trigger mode must be 'normal', 'vibrate', or 'silent'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String = when (block.ringerModeOrNull()) {
        RingerMode.NORMAL -> "Start when the phone enters normal ringer mode."
        RingerMode.VIBRATE -> "Start when the phone enters vibrate mode."
        RingerMode.SILENT -> "Start when the phone enters silent mode."
        null -> "Start on an invalid ringer mode."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveRingerMode(
            blockId = block.id,
            expectedMode = requireNotNull(block.ringerModeOrNull()),
        )
}
