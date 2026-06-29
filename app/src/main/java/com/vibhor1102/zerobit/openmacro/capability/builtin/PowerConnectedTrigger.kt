/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object PowerConnectedTrigger : CapabilityDefinition {
    override val type = "android.power.connected"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Power connected"
    override val description = "Starts when Android reports that external power was connected."
    override val creation = CapabilityCreation("power-connected")
    override val fields: List<CapabilityField> = emptyList()
    override val triggerOutputs = listOf(
        TriggerOutput("power.state", MacroVariableType.TEXT, "The connected power state."),
        TriggerOutput("power.source", MacroVariableType.TEXT, "The known connected power source."),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun explain(block: MacroBlock): String =
        "Start when the phone is connected to external power."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObservePowerConnected(blockId = block.id)
}

object PowerDisconnectedTrigger : CapabilityDefinition {
    override val type = "android.power.disconnected"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Power disconnected"
    override val description = "Starts when Android reports that external power was disconnected."
    override val creation = CapabilityCreation("power-disconnected")
    override val fields: List<CapabilityField> = emptyList()
    override val triggerOutputs = listOf(
        TriggerOutput("power.state", MacroVariableType.TEXT, "The disconnected power state."),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun explain(block: MacroBlock): String =
        "Start when the phone is disconnected from external power."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObservePowerDisconnected(blockId = block.id)
}
