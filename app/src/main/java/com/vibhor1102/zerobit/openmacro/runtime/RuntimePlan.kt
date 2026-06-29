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

