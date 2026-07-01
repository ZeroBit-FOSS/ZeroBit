/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object DockStateTrigger : CapabilityDefinition {
    override val type = "android.device.dock-state-changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Dock state changed"
    override val description = "Starts when Android enters a selected bounded dock state."
    override val creation = CapabilityCreation(
        idBase = "dock-state-changed",
        defaultConfig = mapOf("state" to MacroValue.Text("undocked")),
    )
    override val fields = DockStateCondition.fields
    override val triggerOutputs = listOf(
        TriggerOutput("dock.state", MacroVariableType.TEXT, "The canonical dock state that caused this run."),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("state"), path))
        if (block.dockStateOrNull() == null) {
            add(ValidationIssue("$path.config.state", "invalid_dock_state_trigger", "Dock state trigger must use undocked, desk, car, low_end_desk, or high_end_desk."))
        }
    }

    override fun explain(block: MacroBlock): String =
        "Start when device dock state changes to ${block.dockStateOrNull()?.sourceName?.replace('_', ' ') ?: "an invalid state"}."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveDockState(block.id, requireNotNull(block.dockStateOrNull()))
}
