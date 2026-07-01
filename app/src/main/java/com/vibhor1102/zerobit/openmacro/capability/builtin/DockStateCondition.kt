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
import com.vibhor1102.zerobit.openmacro.runtime.DockState
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object DockStateCondition : CapabilityDefinition {
    override val type = "android.device.dock-state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Dock state"
    override val description = "Checks Android's current bounded device dock state."
    override val creation = CapabilityCreation(
        idBase = "dock-state",
        defaultConfig = mapOf("state" to MacroValue.Text("undocked")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "Dock state",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose undocked, desk, car, low-end desk, or high-end desk.",
            allowedValues = listOf(
                "undocked",
                "desk",
                "car",
                "low_end_desk",
                "high_end_desk",
            ),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.dockStateOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_dock_state",
                        message = "Dock state must be undocked, desk, car, low_end_desk, or high_end_desk.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val state = block.dockStateOrNull() ?: return "Check an invalid dock state."
        return "Continue only while device dock state is ${state.explanation}."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckDockState(
            blockId = block.id,
            expectedState = requireNotNull(block.dockStateOrNull()),
        )
}

internal fun MacroBlock.dockStateOrNull(): DockState? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "undocked" -> DockState.UNDOCKED
        "desk" -> DockState.DESK
        "car" -> DockState.CAR
        "low_end_desk" -> DockState.LOW_END_DESK
        "high_end_desk" -> DockState.HIGH_END_DESK
        else -> null
    }

internal val DockState.sourceName: String
    get() = when (this) {
        DockState.UNDOCKED -> "undocked"
        DockState.DESK -> "desk"
        DockState.CAR -> "car"
        DockState.LOW_END_DESK -> "low_end_desk"
        DockState.HIGH_END_DESK -> "high_end_desk"
    }

private val DockState.explanation: String
    get() = sourceName.replace('_', ' ')
