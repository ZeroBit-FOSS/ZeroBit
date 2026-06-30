/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.MacroVariable

/**
 * Immutable, already-approved instructions consumed by the future runtime.
 * The runtime does not parse source files or interpret capability config.
 */
data class RuntimePlan(
    val macroId: String,
    val sourceFingerprint: String,
    val variables: List<MacroVariable> = emptyList(),
    val triggers: List<RuntimeStep>,
    val conditions: List<RuntimeStep>,
    val actions: List<RuntimeStep>,
    val requiredPermissions: Set<AndroidPermission>,
    val conditionTree: RuntimeConditionNode? = null,
)

sealed interface RuntimeConditionNode {
    data class Condition(
        val step: RuntimeStep,
    ) : RuntimeConditionNode

    data class All(
        val children: List<RuntimeConditionNode>,
    ) : RuntimeConditionNode

    data class Any(
        val children: List<RuntimeConditionNode>,
    ) : RuntimeConditionNode

    data class Not(
        val child: RuntimeConditionNode,
    ) : RuntimeConditionNode
}

sealed interface RuntimeStep {
    val blockId: String

    data class ObservePowerConnected(
        override val blockId: String,
    ) : RuntimeStep

    data class ObservePowerDisconnected(
        override val blockId: String,
    ) : RuntimeStep

    data class ObserveScreenOn(
        override val blockId: String,
    ) : RuntimeStep

    data class ObserveScreenOff(
        override val blockId: String,
    ) : RuntimeStep

    data class ObserveBatteryLevel(
        override val blockId: String,
        val level: Int,
        val direction: BatteryDirection,
    ) : RuntimeStep

    data class CheckDeviceUnlocked(
        override val blockId: String,
        val expectedUnlocked: Boolean = true,
    ) : RuntimeStep

    data class ObserveWifiConnectivity(
        override val blockId: String,
        val connected: Boolean,
    ) : RuntimeStep

    data class ObserveAirplaneMode(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class ObserveRingerMode(
        override val blockId: String,
        val expectedMode: RingerMode,
    ) : RuntimeStep

    data class ObserveBatterySaver(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class ObserveBluetoothState(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class ObserveNfcState(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class ObserveLocationServices(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class ObserveDarkTheme(
        override val blockId: String,
        val expectedDark: Boolean,
    ) : RuntimeStep

    data class ObserveScreenOrientation(
        override val blockId: String,
        val expectedOrientation: ScreenOrientation,
    ) : RuntimeStep

    data class CheckWifiConnected(
        override val blockId: String,
        val ssid: String?,
    ) : RuntimeStep

    data class ShowNotification(
        override val blockId: String,
        val title: RuntimeValueSource,
        val message: RuntimeValueSource,
    ) : RuntimeStep {
        constructor(
            blockId: String,
            title: String,
            message: String,
        ) : this(
            blockId,
            RuntimeValueSource.Literal(com.vibhor1102.zerobit.openmacro.model.MacroValue.Text(title)),
            RuntimeValueSource.Literal(com.vibhor1102.zerobit.openmacro.model.MacroValue.Text(message)),
        )
    }

    data class WriteLog(
        override val blockId: String,
        val message: RuntimeValueSource,
    ) : RuntimeStep {
        constructor(blockId: String, message: String) : this(
            blockId,
            RuntimeValueSource.Literal(com.vibhor1102.zerobit.openmacro.model.MacroValue.Text(message)),
        )
    }

    data class SendSms(
        override val blockId: String,
        val phoneNumber: RuntimeValueSource,
        val message: RuntimeValueSource,
    ) : RuntimeStep {
        constructor(
            blockId: String,
            phoneNumber: String,
            message: String,
        ) : this(
            blockId,
            RuntimeValueSource.Literal(com.vibhor1102.zerobit.openmacro.model.MacroValue.Text(phoneNumber)),
            RuntimeValueSource.Literal(com.vibhor1102.zerobit.openmacro.model.MacroValue.Text(message)),
        )
    }

    data class LaunchApp(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class CheckBatteryCharging(
        override val blockId: String,
        val expectedCharging: Boolean,
    ) : RuntimeStep

    data class CheckBatteryLevel(
        override val blockId: String,
        val level: Int,
        val direction: BatteryDirection,
    ) : RuntimeStep

    data class CheckMediaVolume(
        override val blockId: String,
        val percentage: Int,
        val comparison: MediaVolumeComparison,
    ) : RuntimeStep

    data class CheckBluetoothEnabled(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class CheckNfcEnabled(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class CheckLocationServicesEnabled(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class CheckDarkTheme(
        override val blockId: String,
        val expectedDark: Boolean,
    ) : RuntimeStep

    data class CheckScreenOrientation(
        override val blockId: String,
        val expectedOrientation: ScreenOrientation,
    ) : RuntimeStep

    data class CheckWiredHeadsetConnected(
        override val blockId: String,
        val expectedConnected: Boolean,
    ) : RuntimeStep

    data class CheckPowerConnection(
        override val blockId: String,
        val expectedPluggedIn: Boolean,
        val expectedSource: PowerSource? = null,
    ) : RuntimeStep

    data class CheckScreenInteractive(
        override val blockId: String,
        val expectedInteractive: Boolean,
    ) : RuntimeStep

    data class CheckAirplaneMode(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class CheckRingerMode(
        override val blockId: String,
        val expectedMode: RingerMode,
    ) : RuntimeStep

    data class CheckBatterySaver(
        override val blockId: String,
        val expectedEnabled: Boolean,
    ) : RuntimeStep

    data class CheckTimeWindow(
        override val blockId: String,
        val window: TimeWindowSpec,
    ) : RuntimeStep

    data class OpenWebUrl(
        override val blockId: String,
        val url: String,
    ) : RuntimeStep

    data class OpenAppDetails(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class OpenAppNotificationSettings(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class ShareTextIntent(
        override val blockId: String,
        val packageName: String,
        val text: RuntimeValueSource,
    ) : RuntimeStep

    data class Vibrate(
        override val blockId: String,
        val durationMillis: Long,
    ) : RuntimeStep

    data class SetTorch(
        override val blockId: String,
        val enabled: Boolean,
    ) : RuntimeStep

    data class SetMediaVolume(
        override val blockId: String,
        val percentage: Int,
    ) : RuntimeStep

    data class CopyTextToClipboard(
        override val blockId: String,
        val text: RuntimeValueSource,
    ) : RuntimeStep

    data class DialNumber(
        override val blockId: String,
        val phoneNumber: RuntimeValueSource,
    ) : RuntimeStep

    data class ComposeEmail(
        override val blockId: String,
        val recipient: RuntimeValueSource,
        val subject: RuntimeValueSource,
        val body: RuntimeValueSource,
    ) : RuntimeStep

    data class OpenMapLocation(
        override val blockId: String,
        val query: RuntimeValueSource,
    ) : RuntimeStep

    data class SetAlarm(
        override val blockId: String,
        val hour: Int,
        val minute: Int,
        val label: String?,
        val skipUi: Boolean,
    ) : RuntimeStep

    data class SetTimer(
        override val blockId: String,
        val durationSeconds: Int,
        val label: String?,
        val skipUi: Boolean,
    ) : RuntimeStep

    data class ShowAlarms(
        override val blockId: String,
    ) : RuntimeStep

    data class CreateCalendarEventDraft(
        override val blockId: String,
        val startMillis: Long,
        val endMillis: Long,
        val title: RuntimeValueSource,
        val location: RuntimeValueSource?,
        val description: RuntimeValueSource?,
    ) : RuntimeStep

    data class CreateContactDraft(
        override val blockId: String,
        val name: RuntimeValueSource,
        val phoneNumber: RuntimeValueSource?,
        val email: RuntimeValueSource?,
    ) : RuntimeStep

    data class OpenWifiSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenBluetoothSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenNfcSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenLocationSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenAccessibilitySettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenBatteryOptimizationSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenDataUsageSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenDisplaySettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenSoundSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenSecuritySettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenPrivacySettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenDateTimeSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenLanguagesSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenKeyboardSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenAppsSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenStorageSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenAirplaneModeSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenSystemNotificationSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenDndAccessSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenVpnSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenDefaultAppsSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenDeveloperOptions(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenWirelessSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenUsageAccessSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenAllFilesAccessSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenNotificationListenerSettings(
        override val blockId: String,
    ) : RuntimeStep

    data class OpenAppLanguageSettings(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class OpenAppPictureInPictureSettings(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class OpenAppOverlaySettings(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class OpenAppAllFilesAccessSettings(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class OpenAppUnknownSourcesSettings(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class OpenAppNotificationBubbleSettings(
        override val blockId: String,
        val packageName: String,
    ) : RuntimeStep

    data class SetVariable(
        override val blockId: String,
        val name: String,
        val value: RuntimeValueSource,
    ) : RuntimeStep

    data class CompareValues(
        override val blockId: String,
        val left: RuntimeValueSource,
        val operator: ValueComparisonOperator,
        val right: RuntimeValueSource?,
    ) : RuntimeStep

    data class ObserveNotification(
        override val blockId: String,
        val packageName: String?,
        val capturedFields: Set<NotificationField>,
    ) : RuntimeStep

    data class ObserveSchedule(
        override val blockId: String,
        val schedule: ScheduleSpec,
    ) : RuntimeStep

    data class IncrementVariable(
        override val blockId: String,
        val name: String,
        val amount: java.math.BigDecimal,
    ) : RuntimeStep

    data class ToggleVariable(
        override val blockId: String,
        val name: String,
    ) : RuntimeStep

    data class Delay(
        override val blockId: String,
        val durationMillis: Long,
    ) : RuntimeStep

    data class StopActions(
        override val blockId: String,
    ) : RuntimeStep

    data class StopIf(
        override val blockId: String,
        val left: RuntimeValueSource,
        val operator: ValueComparisonOperator,
        val right: RuntimeValueSource?,
    ) : RuntimeStep

    data class ActionGroup(
        override val blockId: String,
        val failurePolicy: ActionGroupFailurePolicy,
        val actions: List<RuntimeStep>,
    ) : RuntimeStep
}

enum class ActionGroupFailurePolicy {
    STOP,
    CONTINUE,
}

sealed interface RuntimeValueSource {
    data class Literal(
        val value: com.vibhor1102.zerobit.openmacro.model.MacroValue,
    ) : RuntimeValueSource

    data class Variable(
        val name: String,
    ) : RuntimeValueSource

    data class Trigger(
        val key: String,
    ) : RuntimeValueSource
}

enum class BatteryDirection {
    GOES_BELOW,
    GOES_ABOVE,
    EQUALS
}

enum class MediaVolumeComparison {
    BELOW,
    ABOVE,
    EQUALS,
}

enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE,
}

enum class RingerMode {
    NORMAL,
    VIBRATE,
    SILENT,
}

enum class PowerSource {
    AC,
    USB,
    WIRELESS,
    DOCK,
}

enum class NotificationField(
    val contextKey: String,
) {
    PACKAGE("notification.package"),
    TITLE("notification.title"),
    TEXT("notification.text"),
}

enum class ValueComparisonOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    IS_PRESENT,
    IS_MISSING,
}

