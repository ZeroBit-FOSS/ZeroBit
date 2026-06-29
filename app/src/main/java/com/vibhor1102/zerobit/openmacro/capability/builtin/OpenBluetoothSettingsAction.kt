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

object OpenBluetoothSettingsAction : CapabilityDefinition {
    override val type = "android.settings.bluetooth"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Open Bluetooth settings"
    override val description = "Opens Android's exact Bluetooth settings screen."
    override val creation = CapabilityCreation(idBase = "open-bluetooth-settings")
    override val fields = emptyList<CapabilityField>()

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun explain(block: MacroBlock): String =
        "Open Android's Bluetooth settings without scanning, pairing, or changing the radio."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep = RuntimeStep.OpenBluetoothSettings(block.id)
}
