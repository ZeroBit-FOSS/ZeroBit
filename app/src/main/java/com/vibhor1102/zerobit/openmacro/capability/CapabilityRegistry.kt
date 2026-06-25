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
                DeviceUnlockedCondition,
                WifiConnectedCondition,
                NotificationShowAction,
                WriteLogAction,
                SendSmsAction,
            ),
        )

        fun of(vararg definitions: CapabilityDefinition): CapabilityRegistry =
            CapabilityRegistry(definitions.toList())
    }
}
