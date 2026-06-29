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
import com.vibhor1102.zerobit.openmacro.runtime.RingerMode
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object RingerModeCondition : CapabilityDefinition {
    override val type = "android.ringer-mode.state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Ringer mode"
    override val description = "Checks Android's current normal, vibrate, or silent mode."
    override val creation = CapabilityCreation(
        idBase = "ringer-mode",
        defaultConfig = mapOf("mode" to MacroValue.Text("normal")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "mode",
            label = "Mode",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose normal, vibrate, or silent.",
            allowedValues = listOf("normal", "vibrate", "silent"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("mode"), path))
            if (block.ringerModeOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.mode",
                        code = "invalid_ringer_mode",
                        message = "Ringer mode must be 'normal', 'vibrate', or 'silent'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String = when (block.ringerModeOrNull()) {
        RingerMode.NORMAL -> "Continue only while the phone is in normal ringer mode."
        RingerMode.VIBRATE -> "Continue only while the phone is in vibrate mode."
        RingerMode.SILENT -> "Continue only while the phone is in silent mode."
        null -> "Check an invalid ringer mode."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckRingerMode(
            blockId = block.id,
            expectedMode = requireNotNull(block.ringerModeOrNull()),
        )

}

internal fun MacroBlock.ringerModeOrNull(): RingerMode? =
    when ((config["mode"] as? MacroValue.Text)?.value) {
        "normal" -> RingerMode.NORMAL
        "vibrate" -> RingerMode.VIBRATE
        "silent" -> RingerMode.SILENT
        else -> null
    }
