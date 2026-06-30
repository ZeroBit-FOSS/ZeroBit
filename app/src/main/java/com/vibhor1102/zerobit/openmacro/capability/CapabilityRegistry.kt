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
import com.vibhor1102.zerobit.openmacro.capability.builtin.SetTorchAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.SetMediaVolumeAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.MediaVolumeCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.BluetoothStateCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.BluetoothStateTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.NfcStateCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.NfcStateTrigger
import com.vibhor1102.zerobit.openmacro.capability.builtin.LocationServicesCondition
import com.vibhor1102.zerobit.openmacro.capability.builtin.LocationServicesTrigger
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
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenWifiSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenBluetoothSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenNfcSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenLocationSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAccessibilitySettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenBatteryOptimizationSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenDataUsageSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenDisplaySettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenSoundSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenSecuritySettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenPrivacySettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenDateTimeSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenLanguagesSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenKeyboardSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppsSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenStorageSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAirplaneModeSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenSystemNotificationSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenDndAccessSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenVpnSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenDefaultAppsSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenDeveloperOptionsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenWirelessSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenUsageAccessSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAllFilesAccessSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenNotificationListenerSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppLanguageSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppOverlaySettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppPictureInPictureSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppAllFilesAccessSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppUnknownSourcesSettingsAction
import com.vibhor1102.zerobit.openmacro.capability.builtin.OpenAppNotificationBubbleSettingsAction

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
                BluetoothStateTrigger,
                NfcStateTrigger,
                LocationServicesTrigger,
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
                MediaVolumeCondition,
                BluetoothStateCondition,
                NfcStateCondition,
                LocationServicesCondition,
                NotificationShowAction,
                WriteLogAction,
                SendSmsAction,
                LaunchAppAction,
                OpenAppDetailsAction,
                OpenAppNotificationSettingsAction,
                OpenWebUrlAction,
                ShareTextIntentAction,
                VibrateAction,
                SetTorchAction,
                SetMediaVolumeAction,
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
                OpenWifiSettingsAction,
                OpenBluetoothSettingsAction,
                OpenNfcSettingsAction,
                OpenLocationSettingsAction,
                OpenAccessibilitySettingsAction,
                OpenBatteryOptimizationSettingsAction,
                OpenDataUsageSettingsAction,
                OpenDisplaySettingsAction,
                OpenSoundSettingsAction,
                OpenSecuritySettingsAction,
                OpenPrivacySettingsAction,
                OpenDateTimeSettingsAction,
                OpenLanguagesSettingsAction,
                OpenKeyboardSettingsAction,
                OpenAppsSettingsAction,
                OpenStorageSettingsAction,
                OpenAirplaneModeSettingsAction,
                OpenSystemNotificationSettingsAction,
                OpenDndAccessSettingsAction,
                OpenVpnSettingsAction,
                OpenDefaultAppsSettingsAction,
                OpenDeveloperOptionsAction,
                OpenWirelessSettingsAction,
                OpenUsageAccessSettingsAction,
                OpenAllFilesAccessSettingsAction,
                OpenNotificationListenerSettingsAction,
                OpenAppLanguageSettingsAction,
                OpenAppOverlaySettingsAction,
                OpenAppPictureInPictureSettingsAction,
                OpenAppAllFilesAccessSettingsAction,
                OpenAppUnknownSourcesSettingsAction,
                OpenAppNotificationBubbleSettingsAction,
            ),
        )

        fun of(vararg definitions: CapabilityDefinition): CapabilityRegistry =
            CapabilityRegistry(definitions.toList())
    }
}
