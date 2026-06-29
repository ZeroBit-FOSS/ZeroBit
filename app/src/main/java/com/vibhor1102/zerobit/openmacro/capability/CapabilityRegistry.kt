/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.capability.builtin.DeviceUnlockedCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.NotificationShowAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.PowerConnectedTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.PowerDisconnectedTrigger
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
import com.vibhor1102.zerobit.openmacro.capability.builtin.ActionGroupAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.TimeScheduleTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.LaunchAppAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppDetailsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppNotificationSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenWebUrlAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.ShareTextIntentAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.VibrateAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.ClipboardTextAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.BatteryChargingCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.BatteryLevelCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.PowerConnectionCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.ScreenInteractiveCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.WifiConnectedTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.WifiDisconnectedTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.AirplaneModeCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.AirplaneModeTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.RingerModeCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.RingerModeTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.BatterySaverCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.BatterySaverTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.TimeWindowCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.DialNumberAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.ComposeEmailAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenMapLocationAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.SetAlarmAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.SetTimerAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.ShowAlarmsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.CalendarEventDraftAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.ContactDraftAction

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
                AirplaneModeTrigger,
                RingerModeTrigger,
                BatterySaverTrigger,
                PowerDisconnectedTrigger,
                ScreenOnTrigger,
                ScreenOffTrigger,
                BatteryLevelTrigger,
                NotificationReceivedTrigger,
                TimeScheduleTrigger,
                WifiConnectedTrigger,
                WifiDisconnectedTrigger,
                DeviceUnlockedCondition,
                AirplaneModeCondition,
                RingerModeCondition,
                BatterySaverCondition,
                TimeWindowCondition,
                WifiConnectedCondition,
                ValueCompareCondition,
                BatteryChargingCondition,
                BatteryLevelCondition,
                PowerConnectionCondition,
                ScreenInteractiveCondition,
                NotificationShowAction,
                WriteLogAction,
                SendSmsAction,
                LaunchAppAction,
                OpenAppDetailsAction,
                OpenAppNotificationSettingsAction,
                OpenWebUrlAction,
                ShareTextIntentAction,
                VibrateAction,
                ClipboardTextAction,
                SetVariableAction,
                IncrementVariableAction,
                ToggleVariableAction,
                DelayAction,
                StopActionsAction,
                StopIfAction,
                ActionGroupAction,
                DialNumberAction,
                ComposeEmailAction,
                OpenMapLocationAction,
                SetAlarmAction,
                SetTimerAction,
                ShowAlarmsAction,
                CalendarEventDraftAction,
                ContactDraftAction,
            ),
        )

        fun of(vararg definitions: CapabilityDefinition): CapabilityRegistry =
            CapabilityRegistry(definitions.toList())
    }
}
