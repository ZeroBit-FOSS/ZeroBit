/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.media.AudioManager
import android.os.Build
import android.os.BatteryManager
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import com.vibhor1102.zerobit.R
import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.ActionResult
import com.vibhor1102.zerobit.openmacro.runtime.BatteryDirection
import com.vibhor1102.zerobit.openmacro.runtime.ConditionResult
import com.vibhor1102.zerobit.openmacro.runtime.MAX_EMAIL_BODY_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_EMAIL_SUBJECT_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.isValidMapQuery
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeActionExecutor
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeCancellation
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeConditionEvaluator
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeContext
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePermissionChecker
import com.vibhor1102.zerobit.openmacro.runtime.RingerMode
import com.vibhor1102.zerobit.openmacro.runtime.PowerSource
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTaskDispatcher
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerEvent
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerRegistrar
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSourceResult
import com.vibhor1102.zerobit.openmacro.runtime.isValidEmailAddress
import com.vibhor1102.zerobit.openmacro.runtime.ScheduleEventSource
import com.vibhor1102.zerobit.openmacro.runtime.ScheduleSubscriptionCoordinator
import com.vibhor1102.zerobit.openmacro.runtime.TriggerSubscriptionResult
import com.vibhor1102.zerobit.openmacro.runtime.executeVariableAction
import com.vibhor1102.zerobit.openmacro.runtime.evaluateValueCondition
import com.vibhor1102.zerobit.openmacro.runtime.resolve
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.time.Clock

class AndroidTriggerRegistrar(
    context: Context,
    private val notificationEvents: NotificationEventSource = AndroidNotificationEventBroker,
    scheduleEvents: ScheduleEventSource? = null,
    private val onScheduleFailure: (String, RuntimeException) -> Unit =
        { subscriptionId, problem ->
            Log.e(
                "ZeroBitSchedule",
                "Could not schedule the next occurrence for $subscriptionId.",
                problem,
            )
        },
) : RuntimeTriggerRegistrar {
    private val appContext = context.applicationContext
    private val scheduleEvents = scheduleEvents ?: ScheduleSubscriptionCoordinator(
        clock = Clock.systemUTC(),
        alarms = AndroidOneShotScheduleAlarmPort(appContext),
        recurringFailure = onScheduleFailure,
    )

    override fun subscribe(
        macroId: String,
        trigger: RuntimeStep,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult {
        if (trigger is RuntimeStep.ObserveNotification) {
            return notificationEvents.subscribe(trigger, onTriggered)
        }
        if (trigger is RuntimeStep.ObserveSchedule) {
            return scheduleEvents.subscribe(macroId, trigger, onTriggered)
        }
        if (trigger is RuntimeStep.ObserveWifiConnectivity) {
            return subscribeToWifi(trigger, onTriggered)
        }
        if (trigger is RuntimeStep.ObserveBatterySaver) {
            return subscribeToBatterySaver(trigger, onTriggered)
        }
        val filter = when (trigger) {
            is RuntimeStep.ObservePowerConnected -> IntentFilter(Intent.ACTION_POWER_CONNECTED)
            is RuntimeStep.ObservePowerDisconnected -> IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
            is RuntimeStep.ObserveScreenOn -> IntentFilter(Intent.ACTION_SCREEN_ON)
            is RuntimeStep.ObserveScreenOff -> IntentFilter(Intent.ACTION_SCREEN_OFF)
            is RuntimeStep.ObserveBatteryLevel -> IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            is RuntimeStep.ObserveAirplaneMode -> IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            is RuntimeStep.ObserveRingerMode -> IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
            else -> return TriggerSubscriptionResult.Failure(
                "Android trigger registrar does not support ${trigger::class.simpleName}.",
            )
        }

        val receiver = if (trigger is RuntimeStep.ObserveBatteryLevel) {
            var lastLevel: Int? = null
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                        val percentage = intent.batteryPercentageOrNull()
                        if (percentage != null) {
                            val matched = when (trigger.direction) {
                                BatteryDirection.GOES_BELOW -> {
                                    lastLevel?.let { last -> last >= trigger.level && percentage < trigger.level } ?: false
                                }
                                BatteryDirection.GOES_ABOVE -> {
                                    lastLevel?.let { last -> last <= trigger.level && percentage > trigger.level } ?: false
                                }
                                BatteryDirection.EQUALS -> {
                                    lastLevel?.let { last -> last != trigger.level && percentage == trigger.level } ?: false
                                }
                            }
                            lastLevel = percentage
                            if (matched) {
                                onTriggered(
                                    RuntimeTriggerEvent(
                                        values = mapOf(
                                            "battery.percentage" to MacroValue.Number(
                                                percentage.toBigDecimal(),
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    if (action == Intent.ACTION_POWER_CONNECTED && trigger is RuntimeStep.ObservePowerConnected) {
                        val values = mutableMapOf<String, MacroValue>(
                            "power.state" to MacroValue.Text("connected"),
                        )
                        currentPowerSourceOrNull()?.let { source ->
                            values["power.source"] = MacroValue.Text(source.contextName())
                        }
                        onTriggered(RuntimeTriggerEvent(values))
                    } else if (
                        action == Intent.ACTION_POWER_DISCONNECTED &&
                        trigger is RuntimeStep.ObservePowerDisconnected
                    ) {
                        onTriggered(
                            RuntimeTriggerEvent(
                                values = mapOf(
                                    "power.state" to MacroValue.Text("disconnected"),
                                ),
                            ),
                        )
                    } else if (action == Intent.ACTION_SCREEN_ON && trigger is RuntimeStep.ObserveScreenOn) {
                        onTriggered(
                            RuntimeTriggerEvent(
                                values = mapOf(
                                    "screen.state" to MacroValue.Text("on"),
                                ),
                            ),
                        )
                    } else if (action == Intent.ACTION_SCREEN_OFF && trigger is RuntimeStep.ObserveScreenOff) {
                        onTriggered(
                            RuntimeTriggerEvent(
                                values = mapOf(
                                    "screen.state" to MacroValue.Text("off"),
                                ),
                            ),
                        )
                    } else if (
                        action == Intent.ACTION_AIRPLANE_MODE_CHANGED &&
                        trigger is RuntimeStep.ObserveAirplaneMode &&
                        intent.hasExtra("state")
                    ) {
                        val enabled = intent.getBooleanExtra("state", false)
                        if (enabled == trigger.expectedEnabled) {
                            onTriggered(
                                RuntimeTriggerEvent(
                                    values = mapOf(
                                        "airplane_mode.state" to MacroValue.Text(
                                            if (enabled) "enabled" else "disabled",
                                        ),
                                    ),
                                ),
                            )
                        }
                    } else if (
                        action == AudioManager.RINGER_MODE_CHANGED_ACTION &&
                        trigger is RuntimeStep.ObserveRingerMode &&
                        intent.hasExtra(AudioManager.EXTRA_RINGER_MODE)
                    ) {
                        val mode = androidRingerModeOrNull(
                            intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1),
                        )
                        if (mode != null && mode == trigger.expectedMode) {
                            onTriggered(
                                RuntimeTriggerEvent(
                                    values = mapOf(
                                        "ringer.state" to MacroValue.Text(mode.diagnosticName()),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }

        return try {
            registerReceiver(receiver, filter)
            val cancelled = AtomicBoolean(false)
            TriggerSubscriptionResult.Success(
                RuntimeCancellation {
                    if (cancelled.compareAndSet(false, true)) {
                        appContext.unregisterReceiver(receiver)
                    }
                },
            )
        } catch (problem: RuntimeException) {
            TriggerSubscriptionResult.Failure(
                problem.message ?: "Could not register Android receiver for ${trigger::class.simpleName}.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun subscribeToWifi(
        trigger: RuntimeStep.ObserveWifiConnectivity,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return TriggerSubscriptionResult.Failure(
                "Android connectivity service is unavailable.",
            )
        val initialConnected = connectivityManager.getNetworkCapabilities(
            connectivityManager.activeNetwork,
        )?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val transitions = WifiTransitionTracker(initialConnected, trigger.connected) {
            onTriggered(
                RuntimeTriggerEvent(
                    values = mapOf(
                        "wifi.state" to MacroValue.Text(
                            if (trigger.connected) "connected" else "disconnected",
                        ),
                    ),
                ),
            )
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                transitions.update(
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                )
            }

            override fun onLost(network: android.net.Network) {
                transitions.update(false)
            }
        }

        return try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            val cancelled = AtomicBoolean(false)
            TriggerSubscriptionResult.Success(
                RuntimeCancellation {
                    if (cancelled.compareAndSet(false, true)) {
                        connectivityManager.unregisterNetworkCallback(callback)
                    }
                },
            )
        } catch (problem: RuntimeException) {
            TriggerSubscriptionResult.Failure(
                problem.message ?: "Could not observe Android Wi-Fi connectivity.",
            )
        }
    }

    private fun subscribeToBatterySaver(
        trigger: RuntimeStep.ObserveBatterySaver,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult {
        val powerManager = appContext.getSystemService(PowerManager::class.java)
            ?: return TriggerSubscriptionResult.Failure("Android power service is unavailable.")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return
                val enabled = powerManager.isPowerSaveMode
                if (enabled == trigger.expectedEnabled) {
                    onTriggered(
                        RuntimeTriggerEvent(
                            values = mapOf(
                                "battery_saver.state" to MacroValue.Text(
                                    if (enabled) "enabled" else "disabled",
                                ),
                            ),
                        ),
                    )
                }
            }
        }

        return try {
            registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            val cancelled = AtomicBoolean(false)
            TriggerSubscriptionResult.Success(
                RuntimeCancellation {
                    if (cancelled.compareAndSet(false, true)) {
                        appContext.unregisterReceiver(receiver)
                    }
                },
            )
        } catch (problem: RuntimeException) {
            TriggerSubscriptionResult.Failure(
                problem.message ?: "Could not observe Android Battery Saver state.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun currentPowerSourceOrNull(): PowerSource? {
        val battery = try {
            appContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
        } catch (_: RuntimeException) {
            null
        } ?: return null
        return androidPowerSourceOrNull(
            battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
        )
    }
}

internal class WifiTransitionTracker(
    initialConnected: Boolean,
    private val expectedConnected: Boolean,
    private val onMatched: () -> Unit,
) {
    private var connected = initialConnected

    @Synchronized
    fun update(nextConnected: Boolean) {
        if (nextConnected == connected) return
        connected = nextConnected
        if (nextConnected == expectedConnected) onMatched()
    }
}

class AndroidConditionEvaluator(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
) : RuntimeConditionEvaluator {
    private val keyguardManager =
        context.getSystemService(KeyguardManager::class.java)
    private val appContext = context.applicationContext

    override fun evaluate(condition: RuntimeStep, context: RuntimeContext): ConditionResult =
        evaluateValueCondition(condition, context) ?: when (condition) {
            is RuntimeStep.CheckDeviceUnlocked -> {
                if (keyguardManager == null) {
                    ConditionResult.Failed("Android keyguard service is unavailable.")
                } else {
                    val unlocked = !keyguardManager.isDeviceLocked
                    if (unlocked == condition.expectedUnlocked) {
                        ConditionResult.Passed
                    } else if (condition.expectedUnlocked) {
                        ConditionResult.Blocked("The device is locked.")
                    } else {
                        ConditionResult.Blocked("The device is unlocked.")
                    }
                }
            }
            is RuntimeStep.CheckWifiConnected -> {
                val connectivityManager =
                    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (connectivityManager == null) {
                    ConditionResult.Failed("Android connectivity service is unavailable.")
                } else {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (
                        capabilities == null ||
                        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    ) {
                        ConditionResult.Blocked("Wi-Fi is not connected.")
                    } else if (condition.ssid != null) {
                        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        @Suppress("DEPRECATION")
                        val info = wifiManager?.connectionInfo
                        @Suppress("DEPRECATION")
                        val currentSsid = info?.ssid?.removeSurrounding("\"")
                        if (currentSsid == null || currentSsid != condition.ssid) {
                            ConditionResult.Blocked("Connected to Wi-Fi SSID '$currentSsid', expected '${condition.ssid}'.")
                        } else {
                            ConditionResult.Passed
                        }
                    } else {
                        ConditionResult.Passed
                    }
                }
            }
            is RuntimeStep.CheckBatteryCharging -> evaluateBatteryCharging(condition)
            is RuntimeStep.CheckBatteryLevel -> evaluateBatteryLevel(condition)
            is RuntimeStep.CheckPowerConnection -> evaluatePowerConnection(condition)
            is RuntimeStep.CheckScreenInteractive -> evaluateScreenInteractive(condition)
            is RuntimeStep.CheckAirplaneMode -> evaluateAirplaneMode(condition)
            is RuntimeStep.CheckRingerMode -> evaluateRingerMode(condition)
            is RuntimeStep.CheckBatterySaver -> evaluateBatterySaver(condition)
            is RuntimeStep.CheckTimeWindow -> evaluateTimeWindow(condition)
            else -> ConditionResult.Failed(
                "Unsupported Android condition ${condition::class.simpleName}.",
            )
        }

    @Suppress("DEPRECATION")
    private fun evaluateBatteryCharging(
        condition: RuntimeStep.CheckBatteryCharging,
    ): ConditionResult {
        val battery = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return ConditionResult.Failed("Android battery status is unavailable.")
        val status = battery.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        )
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> return ConditionResult.Failed("Android battery charging state is unknown.")
        }
        return if (charging == condition.expectedCharging) {
            ConditionResult.Passed
        } else if (condition.expectedCharging) {
            ConditionResult.Blocked("The battery is not charging.")
        } else {
            ConditionResult.Blocked("The battery is charging.")
        }
    }

    @Suppress("DEPRECATION")
    private fun evaluateBatteryLevel(condition: RuntimeStep.CheckBatteryLevel): ConditionResult {
        val battery = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return ConditionResult.Failed("Android battery level is unavailable.")
        val percentage = battery.batteryPercentageOrNull()
            ?: return ConditionResult.Failed("Android battery percentage is invalid.")
        val matched = when (condition.direction) {
            BatteryDirection.GOES_BELOW -> percentage < condition.level
            BatteryDirection.GOES_ABOVE -> percentage > condition.level
            BatteryDirection.EQUALS -> percentage == condition.level
        }
        return if (matched) {
            ConditionResult.Passed
        } else {
            val comparison = when (condition.direction) {
                BatteryDirection.GOES_BELOW -> "below"
                BatteryDirection.GOES_ABOVE -> "above"
                BatteryDirection.EQUALS -> "equal to"
            }
            ConditionResult.Blocked(
                "Battery level is $percentage%; expected $comparison ${condition.level}%.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun evaluatePowerConnection(
        condition: RuntimeStep.CheckPowerConnection,
    ): ConditionResult {
        val battery = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return ConditionResult.Failed("Android power connection state is unavailable.")
        val rawSource = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val source = androidPowerSourceOrNull(rawSource)
        if (rawSource != 0 && source == null) {
            return ConditionResult.Failed("Android reported an unknown power source.")
        }
        val pluggedIn = source != null
        return if (
            pluggedIn == condition.expectedPluggedIn &&
            (condition.expectedSource == null || source == condition.expectedSource)
        ) {
            ConditionResult.Passed
        } else if (condition.expectedPluggedIn) {
            if (source == null) {
                ConditionResult.Blocked("The device is unplugged.")
            } else {
                ConditionResult.Blocked(
                    "The device is connected to ${source.diagnosticName()}; expected ${condition.expectedSource?.diagnosticName()}.",
                )
            }
        } else {
            ConditionResult.Blocked("The device is connected to ${source?.diagnosticName()}.")
        }
    }

    private fun evaluateScreenInteractive(
        condition: RuntimeStep.CheckScreenInteractive,
    ): ConditionResult {
        val powerManager = appContext.getSystemService(PowerManager::class.java)
            ?: return ConditionResult.Failed("Android power service is unavailable.")
        return if (powerManager.isInteractive == condition.expectedInteractive) {
            ConditionResult.Passed
        } else if (condition.expectedInteractive) {
            ConditionResult.Blocked("The screen is off.")
        } else {
            ConditionResult.Blocked("The screen is on.")
        }
    }

    private fun evaluateAirplaneMode(
        condition: RuntimeStep.CheckAirplaneMode,
    ): ConditionResult {
        val rawState = try {
            Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
            )
        } catch (_: Settings.SettingNotFoundException) {
            return ConditionResult.Failed("Android airplane mode state is unavailable.")
        } catch (_: SecurityException) {
            return ConditionResult.Failed("Android denied access to airplane mode state.")
        }
        val enabled = airplaneModeEnabledOrNull(rawState)
            ?: return ConditionResult.Failed("Android reported an unknown airplane mode state.")
        return if (enabled == condition.expectedEnabled) {
            ConditionResult.Passed
        } else if (condition.expectedEnabled) {
            ConditionResult.Blocked("Airplane mode is disabled.")
        } else {
            ConditionResult.Blocked("Airplane mode is enabled.")
        }
    }

    private fun evaluateRingerMode(
        condition: RuntimeStep.CheckRingerMode,
    ): ConditionResult {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
            ?: return ConditionResult.Failed("Android audio service is unavailable.")
        val currentMode = androidRingerModeOrNull(audioManager.ringerMode)
            ?: return ConditionResult.Failed("Android reported an unknown ringer mode.")
        return if (currentMode == condition.expectedMode) {
            ConditionResult.Passed
        } else {
            ConditionResult.Blocked(
                "Ringer mode is ${currentMode.diagnosticName()}; expected ${condition.expectedMode.diagnosticName()}.",
            )
        }
    }

    private fun evaluateBatterySaver(
        condition: RuntimeStep.CheckBatterySaver,
    ): ConditionResult {
        val powerManager = appContext.getSystemService(PowerManager::class.java)
            ?: return ConditionResult.Failed("Android power service is unavailable.")
        val enabled = powerManager.isPowerSaveMode
        return if (enabled == condition.expectedEnabled) {
            ConditionResult.Passed
        } else if (condition.expectedEnabled) {
            ConditionResult.Blocked("Battery Saver is disabled.")
        } else {
            ConditionResult.Blocked("Battery Saver is enabled.")
        }
    }

    private fun evaluateTimeWindow(
        condition: RuntimeStep.CheckTimeWindow,
    ): ConditionResult {
        val now = clock.instant()
        return if (condition.window.contains(now)) {
            ConditionResult.Passed
        } else {
            val local = now.atZone(condition.window.zoneId)
            ConditionResult.Blocked(
                "Local time is ${local.toLocalDateTime()} in ${condition.window.zoneId.id}, outside the approved window.",
            )
        }
    }
}

internal fun airplaneModeEnabledOrNull(rawState: Int): Boolean? = when (rawState) {
    0 -> false
    1 -> true
    else -> null
}

internal fun androidRingerModeOrNull(rawMode: Int): RingerMode? = when (rawMode) {
    AudioManager.RINGER_MODE_NORMAL -> RingerMode.NORMAL
    AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
    AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
    else -> null
}

internal fun androidPowerSourceOrNull(rawSource: Int): PowerSource? = when (rawSource) {
    BatteryManager.BATTERY_PLUGGED_AC -> PowerSource.AC
    BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.USB
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
    BatteryManager.BATTERY_PLUGGED_DOCK -> PowerSource.DOCK
    else -> null
}

private fun PowerSource.diagnosticName(): String = when (this) {
    PowerSource.AC -> "AC power"
    PowerSource.USB -> "USB power"
    PowerSource.WIRELESS -> "wireless power"
    PowerSource.DOCK -> "dock power"
}

private fun PowerSource.contextName(): String = when (this) {
    PowerSource.AC -> "ac"
    PowerSource.USB -> "usb"
    PowerSource.WIRELESS -> "wireless"
    PowerSource.DOCK -> "dock"
}

internal fun RingerMode.diagnosticName(): String = when (this) {
    RingerMode.NORMAL -> "normal"
    RingerMode.VIBRATE -> "vibrate"
    RingerMode.SILENT -> "silent"
}

private fun Intent.batteryPercentageOrNull(): Int? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null

    return ((level.toLong() * 100L) / scale.toLong())
        .toInt()
        .takeIf { it in 0..100 }
}

class AndroidActionExecutor(
    context: Context,
) : RuntimeActionExecutor {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }
    private val clipboardManager = appContext.getSystemService(ClipboardManager::class.java)
    private val nextNotificationId = AtomicInteger(1)

    override fun execute(action: RuntimeStep, context: RuntimeContext): ActionResult =
        executeVariableAction(action, context) ?: when (action) {
            is RuntimeStep.ShowNotification -> show(action, context)
            is RuntimeStep.WriteLog -> writeLog(action, context)
            is RuntimeStep.SendSms -> sendSms(action, context)
            is RuntimeStep.LaunchApp -> launchApp(action)
            is RuntimeStep.OpenWebUrl -> openWebUrl(action)
            is RuntimeStep.OpenAppDetails -> openAppDetails(action)
            is RuntimeStep.OpenAppNotificationSettings -> openAppNotificationSettings(action)
            is RuntimeStep.ShareTextIntent -> shareText(action, context)
            is RuntimeStep.Vibrate -> vibrate(action)
            is RuntimeStep.CopyTextToClipboard -> copyTextToClipboard(action, context)
            is RuntimeStep.DialNumber -> dialNumber(action, context)
            is RuntimeStep.ComposeEmail -> composeEmail(action, context)
            is RuntimeStep.OpenMapLocation -> openMapLocation(action, context)
            else -> ActionResult.Failed(
                "Unsupported Android action ${action::class.simpleName}.",
            )
        }

    @SuppressLint("MissingPermission")
    private fun vibrate(action: RuntimeStep.Vibrate): ActionResult {
        val deviceVibrator = vibrator
            ?: return ActionResult.Failed("Android vibration service is unavailable.")
        if (!deviceVibrator.hasVibrator()) {
            return ActionResult.Failed("This device does not have a vibrator.")
        }
        return try {
            deviceVibrator.vibrate(
                VibrationEffect.createOneShot(
                    action.durationMillis,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
            ActionResult.Succeeded
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not vibrate the device.")
        }
    }

    private fun copyTextToClipboard(
        action: RuntimeStep.CopyTextToClipboard,
        context: RuntimeContext,
    ): ActionResult {
        val manager = clipboardManager
            ?: return ActionResult.Failed("Android clipboard service is unavailable.")
        val text = when (val result = action.text.resolveText(context, "clipboard text")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        if (text.length > MAX_CLIPBOARD_TEXT_LENGTH) {
            return ActionResult.Failed("Resolved clipboard text exceeds 10000 characters.")
        }
        return try {
            manager.setPrimaryClip(ClipData.newPlainText("ZeroBit", text))
            ActionResult.Succeeded
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not update the clipboard.")
        }
    }

    private fun dialNumber(
        action: RuntimeStep.DialNumber,
        context: RuntimeContext,
    ): ActionResult {
        val phoneNumber = when (
            val result = action.phoneNumber.resolveText(context, "dialer phone number")
        ) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        if (!com.vibhor1102.zerobit.openmacro.runtime.isDialablePhoneNumber(phoneNumber)) {
            return ActionResult.Failed("The resolved phone number is not dialable.")
        }
        val intent = Intent(
            Intent.ACTION_DIAL,
            android.net.Uri.fromParts("tel", phoneNumber, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No dialer app is available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not open the dialer.")
        }
    }

    private fun composeEmail(
        action: RuntimeStep.ComposeEmail,
        context: RuntimeContext,
    ): ActionResult {
        val recipient = when (val result = action.recipient.resolveText(context, "email recipient")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        val subject = when (val result = action.subject.resolveText(context, "email subject")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        val body = when (val result = action.body.resolveText(context, "email body")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        if (!isValidEmailAddress(recipient)) {
            return ActionResult.Failed("The resolved email recipient is not valid.")
        }
        if (subject.isBlank() || subject.length > MAX_EMAIL_SUBJECT_LENGTH) {
            return ActionResult.Failed("The resolved email subject must be 1 to 998 characters.")
        }
        if (body.isBlank() || body.length > MAX_EMAIL_BODY_LENGTH) {
            return ActionResult.Failed("The resolved email body must be 1 to 20000 characters.")
        }
        val intent = Intent(
            Intent.ACTION_SENDTO,
            android.net.Uri.fromParts("mailto", recipient, null),
        )
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No email app is available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not open the email draft.")
        }
    }

    private fun openMapLocation(
        action: RuntimeStep.OpenMapLocation,
        context: RuntimeContext,
    ): ActionResult {
        val query = when (val result = action.query.resolveText(context, "map location")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        if (!isValidMapQuery(query)) {
            return ActionResult.Failed("The resolved map location is not valid.")
        }
        val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No map app is available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not open the map location.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun show(
        action: RuntimeStep.ShowNotification,
        context: RuntimeContext,
    ): ActionResult {
        val manager = notificationManager
            ?: return ActionResult.Failed("Android notification service is unavailable.")
        val title = when (val result = action.title.resolveText(context, "notification title")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        val message = when (val result = action.message.resolveText(context, "notification message")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        return try {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val notification = Notification.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.zerobit_mark)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build()
            manager.notify(nextNotificationId.getAndIncrement(), notification)
            ActionResult.Succeeded
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not show the notification.")
        }
    }

    private fun writeLog(
        action: RuntimeStep.WriteLog,
        context: RuntimeContext,
    ): ActionResult {
        val message = when (val result = action.message.resolveText(context, "log message")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        Log.i("ZeroBitMacro", message)
        return ActionResult.Succeeded
    }

    private fun sendSms(
        action: RuntimeStep.SendSms,
        context: RuntimeContext,
    ): ActionResult {
        val phoneNumber = when (val result = action.phoneNumber.resolveText(context, "SMS phone number")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        val message = when (val result = action.message.resolveText(context, "SMS message")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
                    ?: return ActionResult.Failed("SMS service is unavailable.")
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            ActionResult.Succeeded
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not send SMS.")
        }
    }

    private fun launchApp(action: RuntimeStep.LaunchApp): ActionResult {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(action.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed(
                "No launchable app is installed for package '${action.packageName}'.",
            )
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not launch the app.")
        }
    }

    private fun openWebUrl(action: RuntimeStep.OpenWebUrl): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(action.url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No browser is available to open this web address.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open the web address.")
        }
    }

    private fun openAppDetails(action: RuntimeStep.OpenAppDetails): ActionResult {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", action.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed(
                "Android app details settings are not available for package '${action.packageName}'.",
            )
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open app details.")
        }
    }

    private fun openAppNotificationSettings(
        action: RuntimeStep.OpenAppNotificationSettings,
    ): ActionResult {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, action.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed(
                "Android notification settings are not available for package '${action.packageName}'.",
            )
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open notification settings.")
        }
    }

    private fun shareText(
        action: RuntimeStep.ShareTextIntent,
        context: RuntimeContext,
    ): ActionResult {
        val text = when (val result = action.text.resolveText(context, "shared text")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setPackage(action.packageName)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed(
                "No installed app for package '${action.packageName}' can receive shared text.",
            )
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not share text with the app.")
        }
    }

    private companion object {
        const val CHANNEL_ID = "zerobit_macro_notifications"
        const val CHANNEL_NAME = "Macro notifications"
        const val MAX_CLIPBOARD_TEXT_LENGTH = 10_000
    }

    private fun RuntimeValueSource.resolveText(
        context: RuntimeContext,
        label: String,
    ): TextResolution = when (val result = resolve(context)) {
        is RuntimeValueSourceResult.Missing ->
            TextResolution.Failure(ActionResult.Failed(result.message))
        is RuntimeValueSourceResult.Available -> {
            val text = result.value as? MacroValue.Text
            if (text == null) {
                TextResolution.Failure(
                    ActionResult.Failed("Resolved $label is not text."),
                )
            } else {
                TextResolution.Value(text.value)
            }
        }
    }

    private sealed interface TextResolution {
        data class Value(val value: String) : TextResolution

        data class Failure(val result: ActionResult.Failed) : TextResolution
    }
}

class AndroidRuntimePermissionChecker(
    context: Context,
) : RuntimePermissionChecker {
    private val appContext = context.applicationContext

    override fun missingPermissions(
        required: Set<AndroidPermission>,
    ): Set<AndroidPermission> = required.filterTo(mutableSetOf()) { permission ->
        when (permission) {
            AndroidPermission.POST_NOTIFICATIONS ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            AndroidPermission.SEND_SMS ->
                appContext.checkSelfPermission(Manifest.permission.SEND_SMS) !=
                    PackageManager.PERMISSION_GRANTED
            AndroidPermission.ACCESS_NETWORK_STATE ->
                appContext.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) !=
                    PackageManager.PERMISSION_GRANTED
            AndroidPermission.NOTIFICATION_LISTENER_ACCESS ->
                !hasNotificationListenerAccess()
            AndroidPermission.SCHEDULE_EXACT_ALARM_ACCESS ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    appContext.getSystemService(AlarmManager::class.java)
                        ?.canScheduleExactAlarms() != true
        }
    }

    private fun hasNotificationListenerAccess(): Boolean {
        val expected = ComponentName(
            appContext,
            ZeroBitNotificationListenerService::class.java,
        )
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }
}

class ExecutorRuntimeTaskDispatcher(
    private val executor: Executor,
) : RuntimeTaskDispatcher {
    override fun dispatch(task: () -> Unit) {
        executor.execute(task)
    }
}
