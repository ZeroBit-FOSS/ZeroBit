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
import com.vibhor1102.zerobit.openmacro.runtime.ScreenOrientation
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object ScreenOrientationTrigger : CapabilityDefinition {
    override val type = "android.ui.screen-orientation-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Screen orientation changed"
    override val description = "Starts when the current screen becomes portrait or landscape."
    override val creation = CapabilityCreation(
        idBase = "screen-orientation-changed",
        defaultConfig = mapOf("orientation" to MacroValue.Text("portrait")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "orientation",
            label = "New orientation",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose portrait or landscape.",
            allowedValues = listOf("portrait", "landscape"),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "screen.orientation",
            type = MacroVariableType.TEXT,
            description = "The screen orientation that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("orientation"), path))
            if (block.screenOrientationOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.orientation",
                        code = "invalid_screen_orientation_trigger",
                        message = "Screen orientation trigger must be 'portrait' or 'landscape'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String = when (block.screenOrientationOrNull()) {
        ScreenOrientation.PORTRAIT -> "Start when the screen becomes portrait."
        ScreenOrientation.LANDSCAPE -> "Start when the screen becomes landscape."
        null -> "Start on an invalid screen orientation."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveScreenOrientation(
            blockId = block.id,
            expectedOrientation = requireNotNull(block.screenOrientationOrNull()),
        )
}
