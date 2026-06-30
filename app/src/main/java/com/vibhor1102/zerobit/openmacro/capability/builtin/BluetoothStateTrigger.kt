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

object BluetoothStateTrigger : CapabilityDefinition {
    override val type = "android.bluetooth.state-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Bluetooth state changed"
    override val description = "Starts when Bluetooth becomes enabled or disabled."
    override val creation = CapabilityCreation(
        idBase = "bluetooth-state-changed",
        defaultConfig = mapOf("state" to MacroValue.Text("enabled")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "New state",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose enabled or disabled.",
            allowedValues = listOf("enabled", "disabled"),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "bluetooth.state",
            type = MacroVariableType.TEXT,
            description = "The Bluetooth state that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedBluetoothEnabledOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_bluetooth_trigger_state",
                        message = "Bluetooth trigger state must be 'enabled' or 'disabled'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedBluetoothEnabledOrNull() == false) {
            "Start when Bluetooth becomes disabled."
        } else {
            "Start when Bluetooth becomes enabled."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.BLUETOOTH_CONNECT)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveBluetoothState(
            blockId = block.id,
            expectedEnabled = requireNotNull(block.expectedBluetoothEnabledOrNull()),
        )
}
