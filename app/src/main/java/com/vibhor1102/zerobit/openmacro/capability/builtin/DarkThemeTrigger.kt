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

object DarkThemeTrigger : CapabilityDefinition {
    override val type = "android.ui.theme-state-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Dark theme changed"
    override val description = "Starts when Android changes to dark or light theme."
    override val creation = CapabilityCreation(
        idBase = "dark-theme-changed",
        defaultConfig = mapOf("theme" to MacroValue.Text("dark")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "theme",
            label = "New theme",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose dark or light.",
            allowedValues = listOf("dark", "light"),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "theme.state",
            type = MacroVariableType.TEXT,
            description = "The resolved theme that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("theme"), path))
            if (block.expectedDarkThemeOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.theme",
                        code = "invalid_theme_trigger_state",
                        message = "Theme trigger state must be 'dark' or 'light'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedDarkThemeOrNull() == false) {
            "Start when Android changes to light theme."
        } else {
            "Start when Android changes to dark theme."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveDarkTheme(
            blockId = block.id,
            expectedDark = requireNotNull(block.expectedDarkThemeOrNull()),
        )
}
