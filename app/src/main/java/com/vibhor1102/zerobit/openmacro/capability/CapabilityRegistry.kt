/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.capability.builtin.DeviceUnlockedCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.NotificationShowAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.PowerConnectedTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.ScreenOnTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.ScreenOffTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.BatteryLevelTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.WifiConnectedCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.WriteLogAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.SendSmsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.SetVariableAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.IncrementVariableAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.ToggleVariableAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.NotificationReceivedTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.ValueCompareCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.DelayAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.StopActionsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.StopIfAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.TimeScheduleTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.LaunchAppAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenWebUrlAction

class CapabilityRegistry private constructor(
    definitions: List<CapabilityDefinition>,
) {
    private val definitionsByType = definitions.associateBy { it.type }

    init {
        require(definitionsByType.size == definitions.size) {
            "Capability types must be unique."
        }
    }

    fun find(type: String): CapabilityDefinition? = definitionsByType[type]

    fun list(lane: CapabilityLane): List<CapabilityDefinition> =
        definitionsByType.values
            .filter { it.lane == lane }
            .sortedBy { it.displayName }

    companion object {
        fun builtIn(): CapabilityRegistry = CapabilityRegistry(
            definitions = listOf(
                PowerConnectedTrigger,
                ScreenOnTrigger,
                ScreenOffTrigger,
                BatteryLevelTrigger,
                NotificationReceivedTrigger,
                TimeScheduleTrigger,
                DeviceUnlockedCondition,
                WifiConnectedCondition,
                ValueCompareCondition,
                NotificationShowAction,
                WriteLogAction,
                SendSmsAction,
                LaunchAppAction,
                OpenWebUrlAction,
                SetVariableAction,
                IncrementVariableAction,
                ToggleVariableAction,
                DelayAction,
                StopActionsAction,
                StopIfAction,
            ),
        )

        fun of(vararg definitions: CapabilityDefinition): CapabilityRegistry =
            CapabilityRegistry(definitions.toList())
    }
}
