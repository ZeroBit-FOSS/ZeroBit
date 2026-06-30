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

object DarkThemeCondition : CapabilityDefinition {
    override val type = "android.ui.theme-state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Dark theme"
    override val description = "Checks whether Android currently resolves dark or light theme."
    override val creation = CapabilityCreation(
        idBase = "dark-theme",
        defaultConfig = mapOf("theme" to MacroValue.Text("dark")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "theme",
            label = "Theme",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose dark or light.",
            allowedValues = listOf("dark", "light"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("theme"), path))
            if (block.expectedDarkThemeOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.theme",
                        code = "invalid_theme_state",
                        message = "Theme state must be 'dark' or 'light'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedDarkThemeOrNull() == false) {
            "Continue only while Android uses light theme."
        } else {
            "Continue only while Android uses dark theme."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckDarkTheme(
            blockId = block.id,
            expectedDark = requireNotNull(block.expectedDarkThemeOrNull()),
        )
}

internal fun MacroBlock.expectedDarkThemeOrNull(): Boolean? =
    when ((config["theme"] as? MacroValue.Text)?.value) {
        "dark" -> true
        "light" -> false
        else -> null
    }
