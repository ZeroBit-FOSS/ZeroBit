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

object ScreenOnTrigger : CapabilityDefinition {
    override val type = "android.screen.on"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Screen turned on"
    override val description = "Starts when Android reports that the screen was turned on."
    override val creation = CapabilityCreation("screen-on")
    override val triggerOutputs = listOf(screenStateOutput())
    override val fields: List<CapabilityField> = emptyList()

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun explain(block: MacroBlock): String =
        "Start when the screen is turned on."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveScreenOn(blockId = block.id)
}

object ScreenOffTrigger : CapabilityDefinition {
    override val type = "android.screen.off"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Screen turned off"
    override val description = "Starts when Android reports that the screen was turned off."
    override val creation = CapabilityCreation("screen-off")
    override val triggerOutputs = listOf(screenStateOutput())
    override val fields: List<CapabilityField> = emptyList()

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun explain(block: MacroBlock): String =
        "Start when the screen is turned off."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveScreenOff(blockId = block.id)
}

private fun screenStateOutput() = TriggerOutput(
    key = "screen.state",
    type = MacroVariableType.TEXT,
    description = "The screen state that caused this run: on or off.",
)
