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
import com.vibhor1102.zerobit.openmacro.runtime.ScreenOrientation
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object ScreenOrientationCondition : CapabilityDefinition {
    override val type = "android.ui.screen-orientation"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Screen orientation"
    override val description = "Checks whether the current screen is portrait or landscape."
    override val creation = CapabilityCreation(
        idBase = "screen-orientation",
        defaultConfig = mapOf("orientation" to MacroValue.Text("portrait")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "orientation",
            label = "Orientation",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose portrait or landscape.",
            allowedValues = listOf("portrait", "landscape"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("orientation"), path))
            if (block.screenOrientationOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.orientation",
                        code = "invalid_screen_orientation",
                        message = "Screen orientation must be 'portrait' or 'landscape'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String = when (block.screenOrientationOrNull()) {
        ScreenOrientation.PORTRAIT -> "Continue only while the screen is portrait."
        ScreenOrientation.LANDSCAPE -> "Continue only while the screen is landscape."
        null -> "Check an invalid screen orientation."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckScreenOrientation(
            blockId = block.id,
            expectedOrientation = requireNotNull(block.screenOrientationOrNull()),
        )
}

internal fun MacroBlock.screenOrientationOrNull(): ScreenOrientation? =
    when ((config["orientation"] as? MacroValue.Text)?.value) {
        "portrait" -> ScreenOrientation.PORTRAIT
        "landscape" -> ScreenOrientation.LANDSCAPE
        else -> null
    }
