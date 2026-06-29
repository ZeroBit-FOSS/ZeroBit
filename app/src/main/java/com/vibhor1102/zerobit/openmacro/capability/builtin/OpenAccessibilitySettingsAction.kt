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

object OpenAccessibilitySettingsAction : CapabilityDefinition {
    override val type = "android.settings.accessibility"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Open accessibility settings"
    override val description = "Opens Android's exact accessibility settings screen."
    override val creation = CapabilityCreation(idBase = "open-accessibility-settings")
    override val fields = emptyList<CapabilityField>()

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun explain(block: MacroBlock): String =
        "Open Android's accessibility settings without targeting or enabling a service."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep = RuntimeStep.OpenAccessibilitySettings(block.id)
}
