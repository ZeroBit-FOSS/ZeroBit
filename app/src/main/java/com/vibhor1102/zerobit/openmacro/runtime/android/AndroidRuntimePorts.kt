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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ComponentCallbacks
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.nfc.NfcManager
import android.nfc.NfcAdapter
import android.media.AudioManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.BatteryManager
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
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
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CALENDAR_DESCRIPTION_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CALENDAR_LOCATION_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CALENDAR_TITLE_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CONTACT_NAME_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MediaVolumeComparison
import com.vibhor1102.zerobit.openmacro.runtime.isDialablePhoneNumber
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
        if (trigger is RuntimeStep.ObserveDarkTheme) {
            return subscribeToDarkTheme(trigger, onTriggered)
        }
        val filter = when (trigger) {
            is RuntimeStep.ObservePowerConnected -> IntentFilter(Intent.ACTION_POWER_CONNECTED)
            is RuntimeStep.ObservePowerDisconnected -> IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
            is RuntimeStep.ObserveScreenOn -> IntentFilter(Intent.ACTION_SCREEN_ON)
            is RuntimeStep.ObserveScreenOff -> IntentFilter(Intent.ACTION_SCREEN_OFF)
            is RuntimeStep.ObserveBatteryLevel -> IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            is RuntimeStep.ObserveAirplaneMode -> IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            is RuntimeStep.ObserveRingerMode -> IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
            is RuntimeStep.ObserveBluetoothState -> IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            is RuntimeStep.ObserveNfcState -> IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            is RuntimeStep.ObserveLocationServices -> IntentFilter(LocationManager.MODE_CHANGED_ACTION)
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
                    } else if (
                        action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                        trigger is RuntimeStep.ObserveBluetoothState &&
                        intent.hasExtra(BluetoothAdapter.EXTRA_STATE)
                    ) {
                        val state = matchingBluetoothTriggerState(
                            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR),
                            trigger.expectedEnabled,
                        )
                        if (state != null) {
                            onTriggered(
                                RuntimeTriggerEvent(
                                    values = mapOf(
                                        "bluetooth.state" to MacroValue.Text(state),
                                    ),
                                ),
                            )
                        }
                    } else if (
                        action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED &&
                        trigger is RuntimeStep.ObserveNfcState &&
                        intent.hasExtra(NfcAdapter.EXTRA_ADAPTER_STATE)
                    ) {
                        val state = matchingNfcTriggerState(
                            intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1),
                            trigger.expectedEnabled,
                        )
                        if (state != null) {
                            onTriggered(
                                RuntimeTriggerEvent(
                                    values = mapOf(
                                        "nfc.state" to MacroValue.Text(state),
                                    ),
                                ),
                            )
                        }
                    } else if (
                        action == LocationManager.MODE_CHANGED_ACTION &&
                        trigger is RuntimeStep.ObserveLocationServices
                    ) {
                        val state = matchingLocationServicesTriggerState(
                            locationServicesEnabledOrNull(appContext),
                            trigger.expectedEnabled,
                        )
                        if (state != null) {
                            onTriggered(
                                RuntimeTriggerEvent(
                                    values = mapOf(
                                        "location_services.state" to MacroValue.Text(state),
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

    private fun subscribeToDarkTheme(
        trigger: RuntimeStep.ObserveDarkTheme,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult {
        val tracker = DarkThemeTransitionTracker(
            darkThemeEnabledOrNull(appContext.resources.configuration.uiMode),
        )
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val state = tracker.matchingState(
                    darkThemeEnabledOrNull(newConfig.uiMode),
                    trigger.expectedDark,
                )
                if (state != null) {
                    onTriggered(
                        RuntimeTriggerEvent(
                            values = mapOf("theme.state" to MacroValue.Text(state)),
                        ),
                    )
                }
            }

            override fun onLowMemory() = Unit
        }
        return try {
            appContext.registerComponentCallbacks(callbacks)
            val cancelled = AtomicBoolean(false)
            TriggerSubscriptionResult.Success(
                RuntimeCancellation {
                    if (cancelled.compareAndSet(false, true)) {
                        appContext.unregisterComponentCallbacks(callbacks)
                    }
                },
            )
        } catch (_: RuntimeException) {
            TriggerSubscriptionResult.Failure("Could not observe Android theme changes.")
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
            is RuntimeStep.CheckMediaVolume -> evaluateMediaVolume(condition)
            is RuntimeStep.CheckBluetoothEnabled -> evaluateBluetoothState(condition)
            is RuntimeStep.CheckNfcEnabled -> evaluateNfcState(condition)
            is RuntimeStep.CheckLocationServicesEnabled -> evaluateLocationServices(condition)
            is RuntimeStep.CheckDarkTheme -> evaluateDarkTheme(condition)
            is RuntimeStep.CheckScreenOrientation -> evaluateScreenOrientation(condition)
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

    private fun evaluateMediaVolume(
        condition: RuntimeStep.CheckMediaVolume,
    ): ConditionResult {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
            ?: return ConditionResult.Failed("Android audio service is unavailable.")
        return try {
            val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val matched = mediaVolumeMatches(
                current,
                maximum,
                condition.percentage,
                condition.comparison,
            ) ?: return ConditionResult.Failed("Android media volume range is unavailable.")
            if (matched) {
                ConditionResult.Passed
            } else {
                val currentPercentage = mediaVolumeApproximatePercentage(current, maximum)
                    ?: return ConditionResult.Failed("Android media volume range is unavailable.")
                val comparison = when (condition.comparison) {
                    MediaVolumeComparison.BELOW -> "below"
                    MediaVolumeComparison.ABOVE -> "above"
                    MediaVolumeComparison.EQUALS -> "equal to"
                }
                ConditionResult.Blocked(
                    "Media volume is about $currentPercentage%; expected $comparison ${condition.percentage}% on this device.",
                )
            }
        } catch (_: SecurityException) {
            ConditionResult.Failed("Android did not allow the media volume check.")
        } catch (_: RuntimeException) {
            ConditionResult.Failed("Android could not read media volume.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun evaluateBluetoothState(
        condition: RuntimeStep.CheckBluetoothEnabled,
    ): ConditionResult {
        val manager = appContext.getSystemService(BluetoothManager::class.java)
            ?: return ConditionResult.Failed("Android Bluetooth service is unavailable.")
        val adapter = manager.adapter
            ?: return ConditionResult.Failed("This device does not have Bluetooth.")
        val state = try {
            androidBluetoothState(adapter.state)
        } catch (_: SecurityException) {
            return ConditionResult.Failed("Nearby devices permission is required to check Bluetooth.")
        } catch (_: RuntimeException) {
            return ConditionResult.Failed("Android could not read Bluetooth state.")
        }
        val enabled = when (state) {
            AndroidBluetoothState.ENABLED -> true
            AndroidBluetoothState.DISABLED -> false
            AndroidBluetoothState.CHANGING ->
                return ConditionResult.Failed("Bluetooth is currently changing state.")
            AndroidBluetoothState.UNKNOWN ->
                return ConditionResult.Failed("Android reported an unknown Bluetooth state.")
        }
        return if (enabled == condition.expectedEnabled) {
            ConditionResult.Passed
        } else if (condition.expectedEnabled) {
            ConditionResult.Blocked("Bluetooth is disabled.")
        } else {
            ConditionResult.Blocked("Bluetooth is enabled.")
        }
    }

    private fun evaluateNfcState(
        condition: RuntimeStep.CheckNfcEnabled,
    ): ConditionResult {
        val manager = appContext.getSystemService(NfcManager::class.java)
            ?: return ConditionResult.Failed("Android NFC service is unavailable.")
        val adapter = manager.defaultAdapter
            ?: return ConditionResult.Failed("This device does not have NFC.")
        val state = try {
            androidNfcState(adapter.adapterState)
        } catch (_: SecurityException) {
            return ConditionResult.Failed("Android did not allow the NFC state check.")
        } catch (_: RuntimeException) {
            return ConditionResult.Failed("Android could not read NFC state.")
        }
        val enabled = when (state) {
            AndroidNfcState.ENABLED -> true
            AndroidNfcState.DISABLED -> false
            AndroidNfcState.CHANGING ->
                return ConditionResult.Failed("NFC is currently changing state.")
            AndroidNfcState.UNKNOWN ->
                return ConditionResult.Failed("Android reported an unknown NFC state.")
        }
        return if (enabled == condition.expectedEnabled) {
            ConditionResult.Passed
        } else if (condition.expectedEnabled) {
            ConditionResult.Blocked("NFC is disabled.")
        } else {
            ConditionResult.Blocked("NFC is enabled.")
        }
    }

    private fun evaluateLocationServices(
        condition: RuntimeStep.CheckLocationServicesEnabled,
    ): ConditionResult {
        val enabled = locationServicesEnabledOrNull(appContext)
            ?: return ConditionResult.Failed("Android location services state is unavailable.")
        return if (enabled == condition.expectedEnabled) {
            ConditionResult.Passed
        } else if (condition.expectedEnabled) {
            ConditionResult.Blocked("Location services are disabled.")
        } else {
            ConditionResult.Blocked("Location services are enabled.")
        }
    }

    private fun evaluateDarkTheme(condition: RuntimeStep.CheckDarkTheme): ConditionResult {
        val dark = darkThemeEnabledOrNull(appContext.resources.configuration.uiMode)
            ?: return ConditionResult.Failed("Android theme state is undefined.")
        return if (dark == condition.expectedDark) {
            ConditionResult.Passed
        } else if (condition.expectedDark) {
            ConditionResult.Blocked("Android is using light theme.")
        } else {
            ConditionResult.Blocked("Android is using dark theme.")
        }
    }

    private fun evaluateScreenOrientation(
        condition: RuntimeStep.CheckScreenOrientation,
    ): ConditionResult {
        val orientation = screenOrientationOrNull(appContext.resources.configuration.orientation)
            ?: return ConditionResult.Failed("Android screen orientation is undefined.")
        return if (orientation == condition.expectedOrientation) {
            ConditionResult.Passed
        } else {
            ConditionResult.Blocked(
                "Screen orientation is ${orientation.name.lowercase()}; expected ${condition.expectedOrientation.name.lowercase()}.",
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
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
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
            is RuntimeStep.SetTorch -> setTorch(action)
            is RuntimeStep.SetMediaVolume -> setMediaVolume(action)
            is RuntimeStep.CopyTextToClipboard -> copyTextToClipboard(action, context)
            is RuntimeStep.DialNumber -> dialNumber(action, context)
            is RuntimeStep.ComposeEmail -> composeEmail(action, context)
            is RuntimeStep.OpenMapLocation -> openMapLocation(action, context)
            is RuntimeStep.SetAlarm -> setAlarm(action)
            is RuntimeStep.SetTimer -> setTimer(action)
            is RuntimeStep.ShowAlarms -> showAlarms()
            is RuntimeStep.CreateCalendarEventDraft -> createCalendarEventDraft(action, context)
            is RuntimeStep.CreateContactDraft -> createContactDraft(action, context)
            is RuntimeStep.OpenWifiSettings -> openWifiSettings()
            is RuntimeStep.OpenBluetoothSettings -> openBluetoothSettings()
            is RuntimeStep.OpenNfcSettings -> openNfcSettings()
            is RuntimeStep.OpenLocationSettings -> openLocationSettings()
            is RuntimeStep.OpenAccessibilitySettings -> openAccessibilitySettings()
            is RuntimeStep.OpenBatteryOptimizationSettings -> openBatteryOptimizationSettings()
            is RuntimeStep.OpenDataUsageSettings -> openDataUsageSettings()
            is RuntimeStep.OpenDisplaySettings -> openDisplaySettings()
            is RuntimeStep.OpenSoundSettings -> openSoundSettings()
            is RuntimeStep.OpenSecuritySettings -> openSecuritySettings()
            is RuntimeStep.OpenPrivacySettings -> openPrivacySettings()
            is RuntimeStep.OpenDateTimeSettings -> openDateTimeSettings()
            is RuntimeStep.OpenLanguagesSettings -> openLanguagesSettings()
            is RuntimeStep.OpenKeyboardSettings -> openKeyboardSettings()
            is RuntimeStep.OpenAppsSettings -> openAppsSettings()
            is RuntimeStep.OpenStorageSettings -> openStorageSettings()
            is RuntimeStep.OpenAirplaneModeSettings -> openAirplaneModeSettings()
            is RuntimeStep.OpenSystemNotificationSettings -> openSystemNotificationSettings()
            is RuntimeStep.OpenDndAccessSettings -> openDndAccessSettings()
            is RuntimeStep.OpenVpnSettings -> openVpnSettings()
            is RuntimeStep.OpenDefaultAppsSettings -> openDefaultAppsSettings()
            is RuntimeStep.OpenDeveloperOptions -> openDeveloperOptions()
            is RuntimeStep.OpenWirelessSettings -> openWirelessSettings()
            is RuntimeStep.OpenUsageAccessSettings -> openUsageAccessSettings()
            is RuntimeStep.OpenAllFilesAccessSettings -> openAllFilesAccessSettings()
            is RuntimeStep.OpenNotificationListenerSettings -> openNotificationListenerSettings()
            is RuntimeStep.OpenAppLanguageSettings -> openPackageSettingsRoute(
                Settings.ACTION_APP_LOCALE_SETTINGS,
                action.packageName,
                "app language",
            )
            is RuntimeStep.OpenAppPictureInPictureSettings -> openPackageSettingsRoute(
                Settings.ACTION_PICTURE_IN_PICTURE_SETTINGS,
                action.packageName,
                "app picture-in-picture",
            )
            is RuntimeStep.OpenAppOverlaySettings -> openPackageSettingsRoute(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                action.packageName,
                "app overlay",
            )
            is RuntimeStep.OpenAppAllFilesAccessSettings -> openPackageSettingsRoute(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                action.packageName,
                "app all-files access",
            )
            is RuntimeStep.OpenAppUnknownSourcesSettings -> openPackageSettingsRoute(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                action.packageName,
                "app unknown sources",
            )
            is RuntimeStep.OpenAppNotificationBubbleSettings ->
                openAppNotificationBubbleSettings(action.packageName)
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

    private fun setAlarm(action: RuntimeStep.SetAlarm): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, action.skipUi)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        action.label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No clock app can set an alarm.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not set the alarm.")
        }
    }

    private fun setTimer(action: RuntimeStep.SetTimer): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, action.durationSeconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, action.skipUi)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        action.label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No clock app can set a timer.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not set the timer.")
        }
    }

    private fun showAlarms(): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No clock app can show alarms.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not open the alarm list.")
        }
    }

    private fun createCalendarEventDraft(
        action: RuntimeStep.CreateCalendarEventDraft,
        context: RuntimeContext,
    ): ActionResult {
        val title = when (val result = action.title.resolveText(context, "calendar event title")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        val location = when (val source = action.location) {
            null -> null
            else -> when (val result = source.resolveText(context, "calendar event location")) {
                is TextResolution.Failure -> return result.result
                is TextResolution.Value -> result.value
            }
        }
        val description = when (val source = action.description) {
            null -> null
            else -> when (val result = source.resolveText(context, "calendar event description")) {
                is TextResolution.Failure -> return result.result
                is TextResolution.Value -> result.value
            }
        }
        if (title.isBlank() || title.length > MAX_CALENDAR_TITLE_LENGTH) {
            return ActionResult.Failed("The resolved calendar event title must be 1 to 200 characters.")
        }
        if (location != null && (location.isBlank() || location.length > MAX_CALENDAR_LOCATION_LENGTH)) {
            return ActionResult.Failed("The resolved calendar event location must be 1 to 500 characters.")
        }
        if (description != null && (description.isBlank() || description.length > MAX_CALENDAR_DESCRIPTION_LENGTH)) {
            return ActionResult.Failed("The resolved calendar event description must be 1 to 5000 characters.")
        }
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.endMillis)
            .putExtra(CalendarContract.Events.TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        location?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        description?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No calendar app can create an event draft.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not open the calendar event draft.")
        }
    }

    private fun createContactDraft(
        action: RuntimeStep.CreateContactDraft,
        context: RuntimeContext,
    ): ActionResult {
        val name = when (val result = action.name.resolveText(context, "contact name")) {
            is TextResolution.Failure -> return result.result
            is TextResolution.Value -> result.value
        }
        val phoneNumber = when (val source = action.phoneNumber) {
            null -> null
            else -> when (val result = source.resolveText(context, "contact phone number")) {
                is TextResolution.Failure -> return result.result
                is TextResolution.Value -> result.value
            }
        }
        val email = when (val source = action.email) {
            null -> null
            else -> when (val result = source.resolveText(context, "contact email")) {
                is TextResolution.Failure -> return result.result
                is TextResolution.Value -> result.value
            }
        }
        if (name.isBlank() || name.length > MAX_CONTACT_NAME_LENGTH) {
            return ActionResult.Failed("The resolved contact name must be 1 to 200 characters.")
        }
        if (phoneNumber != null && !isDialablePhoneNumber(phoneNumber)) {
            return ActionResult.Failed("The resolved contact phone number is not valid.")
        }
        if (email != null && !isValidEmailAddress(email)) {
            return ActionResult.Failed("The resolved contact email is not valid.")
        }
        val intent = Intent(Intent.ACTION_INSERT)
            .setType(ContactsContract.Contacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        phoneNumber?.let { intent.putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        email?.let { intent.putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("No contacts app can create a contact draft.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Android could not open the contact draft.")
        }
    }

    private fun openWifiSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android Wi-Fi settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open Wi-Fi settings.")
        }
    }

    private fun openBluetoothSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android Bluetooth settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open Bluetooth settings.")
        }
    }

    private fun openNfcSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_NFC_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android NFC settings are not available on this device.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open NFC settings.")
        }
    }

    private fun openLocationSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android location settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open location settings.")
        }
    }

    private fun openAccessibilitySettings(): ActionResult {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android accessibility settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open accessibility settings.")
        }
    }

    private fun openBatteryOptimizationSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android battery optimization settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open battery optimization settings.")
        }
    }

    private fun openDataUsageSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android data usage settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open data usage settings.")
        }
    }

    private fun openDisplaySettings(): ActionResult {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android display settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open display settings.")
        }
    }

    private fun openSoundSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android sound settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open sound settings.")
        }
    }

    private fun openSecuritySettings(): ActionResult {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android security settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open security settings.")
        }
    }

    private fun openPrivacySettings(): ActionResult {
        val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android privacy settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open privacy settings.")
        }
    }

    private fun openDateTimeSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_DATE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android date and time settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open date and time settings.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun setTorch(action: RuntimeStep.SetTorch): ActionResult {
        val manager = cameraManager
            ?: return ActionResult.Failed("Android camera service is unavailable.")
        return try {
            val candidates = manager.cameraIdList.map { cameraId ->
                val characteristics = manager.getCameraCharacteristics(cameraId)
                TorchCameraCandidate(
                    id = cameraId,
                    hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING),
                )
            }
            val cameraId = selectTorchCameraId(candidates)
                ?: return ActionResult.Failed("This device does not have an available torch.")
            manager.setTorchMode(cameraId, action.enabled)
            ActionResult.Succeeded
        } catch (problem: CameraAccessException) {
            ActionResult.Failed(torchCameraFailureMessage(problem.reason))
        } catch (_: SecurityException) {
            ActionResult.Failed("Camera permission is required to control the torch.")
        } catch (_: IllegalArgumentException) {
            ActionResult.Failed("The selected torch camera is no longer available.")
        } catch (_: RuntimeException) {
            ActionResult.Failed("Android could not change the torch.")
        }
    }

    private fun setMediaVolume(action: RuntimeStep.SetMediaVolume): ActionResult {
        val manager = audioManager
            ?: return ActionResult.Failed("Android audio service is unavailable.")
        return try {
            val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val index = mediaVolumeIndex(action.percentage, maximum)
                ?: return ActionResult.Failed("Android media volume range is unavailable.")
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            ActionResult.Succeeded
        } catch (_: SecurityException) {
            ActionResult.Failed("Android did not allow the media volume change.")
        } catch (_: RuntimeException) {
            ActionResult.Failed("Android could not change media volume.")
        }
    }

    private fun openLanguagesSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_LOCALE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android language settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open language settings.")
        }
    }

    private fun openKeyboardSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android keyboard settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open keyboard settings.")
        }
    }

    private fun openAppsSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android apps settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open apps settings.")
        }
    }

    private fun openStorageSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android storage settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open storage settings.")
        }
    }

    private fun openAirplaneModeSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android Airplane mode settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open Airplane mode settings.")
        }
    }

    private fun openSystemNotificationSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_NOTIFICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android notification settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open notification settings.")
        }
    }

    private fun openDndAccessSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android Do Not Disturb access settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open Do Not Disturb access settings.")
        }
    }

    private fun openVpnSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_VPN_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android VPN settings are not available.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open VPN settings.")
        }
    }

    private fun openDefaultAppsSettings(): ActionResult = openSettingsRoute(
        action = Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        unavailableMessage = "Android default-apps settings are not available.",
        failureMessage = "Could not open default-apps settings.",
    )

    private fun openDeveloperOptions(): ActionResult = openSettingsRoute(
        action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        unavailableMessage = "Android Developer options are not available.",
        failureMessage = "Could not open Developer options.",
    )

    private fun openWirelessSettings(): ActionResult = openSettingsRoute(
        action = Settings.ACTION_WIRELESS_SETTINGS,
        unavailableMessage = "Android wireless settings are not available.",
        failureMessage = "Could not open wireless settings.",
    )

    private fun openUsageAccessSettings(): ActionResult = openSettingsRoute(
        action = Settings.ACTION_USAGE_ACCESS_SETTINGS,
        unavailableMessage = "Android usage-access settings are not available.",
        failureMessage = "Could not open usage-access settings.",
    )

    private fun openAllFilesAccessSettings(): ActionResult = openSettingsRoute(
        action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
        unavailableMessage = "Android all-files access settings are not available.",
        failureMessage = "Could not open all-files access settings.",
    )

    private fun openNotificationListenerSettings(): ActionResult = openSettingsRoute(
        action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        unavailableMessage = "Android notification-listener settings are not available.",
        failureMessage = "Could not open notification-listener settings.",
    )

    private fun openSettingsRoute(
        action: String,
        unavailableMessage: String,
        failureMessage: String,
    ): ActionResult {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed(unavailableMessage)
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: failureMessage)
        }
    }

    private fun openPackageSettingsRoute(
        action: String,
        packageName: String,
        routeName: String,
    ): ActionResult {
        val intent = Intent(
            action,
            android.net.Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed("Android $routeName settings are not available for '$packageName'.")
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open $routeName settings.")
        }
    }

    private fun openAppNotificationBubbleSettings(packageName: String): ActionResult {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Succeeded
        } catch (_: ActivityNotFoundException) {
            ActionResult.Failed(
                "Android app notification-bubble settings are not available for '$packageName'.",
            )
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not open app notification-bubble settings.")
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
            AndroidPermission.BLUETOOTH_CONNECT ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                    PackageManager.PERMISSION_GRANTED
            AndroidPermission.CAMERA ->
                appContext.checkSelfPermission(Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED
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
