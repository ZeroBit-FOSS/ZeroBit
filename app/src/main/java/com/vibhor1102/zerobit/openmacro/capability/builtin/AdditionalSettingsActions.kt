/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object OpenDefaultAppsSettingsAction : ConfigFreeSettingsAction(
    type = "android.settings.default-apps",
    displayName = "Open Default apps settings",
    description = "Opens Android's exact default-apps settings screen.",
    idBase = "open-default-apps-settings",
    explanation = "Open Android's default-apps settings without selecting a role or changing defaults.",
    compileStep = { RuntimeStep.OpenDefaultAppsSettings(it) },
)

object OpenDeveloperOptionsAction : ConfigFreeSettingsAction(
    type = "android.settings.developer-options",
    displayName = "Open Developer options",
    description = "Opens Android's exact Developer options screen.",
    idBase = "open-developer-options",
    explanation = "Open Android's Developer options without changing any developer setting.",
    compileStep = { RuntimeStep.OpenDeveloperOptions(it) },
)

object OpenWirelessSettingsAction : ConfigFreeSettingsAction(
    type = "android.settings.wireless",
    displayName = "Open Wireless settings",
    description = "Opens Android's top-level wireless settings screen.",
    idBase = "open-wireless-settings",
    explanation = "Open Android's wireless settings without changing a radio or connection.",
    compileStep = { RuntimeStep.OpenWirelessSettings(it) },
)

abstract class ConfigFreeSettingsAction(
    final override val type: String,
    final override val displayName: String,
    final override val description: String,
    idBase: String,
    private val explanation: String,
    private val compileStep: (String) -> RuntimeStep,
) : CapabilityDefinition {
    final override val lane = CapabilityLane.ACTION
    final override val creation = CapabilityCreation(idBase = idBase)
    final override val fields = emptyList<CapabilityField>()

    final override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    final override fun explain(block: MacroBlock): String = explanation

    final override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    final override fun compile(block: MacroBlock): RuntimeStep = compileStep(block.id)
}
