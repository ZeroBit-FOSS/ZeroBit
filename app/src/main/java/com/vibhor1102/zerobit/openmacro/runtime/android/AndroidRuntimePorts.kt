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
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import com.vibhor1102.zerobit.R
import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.ActionResult
import com.vibhor1102.zerobit.openmacro.runtime.BatteryDirection
import com.vibhor1102.zerobit.openmacro.runtime.ConditionResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeActionExecutor
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeCancellation
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeConditionEvaluator
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeContext
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePermissionChecker
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTaskDispatcher
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerEvent
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerRegistrar
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSourceResult
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
        val filter = when (trigger) {
            is RuntimeStep.ObservePowerConnected -> IntentFilter(Intent.ACTION_POWER_CONNECTED)
            is RuntimeStep.ObserveScreenOn -> IntentFilter(Intent.ACTION_SCREEN_ON)
            is RuntimeStep.ObserveScreenOff -> IntentFilter(Intent.ACTION_SCREEN_OFF)
            is RuntimeStep.ObserveBatteryLevel -> IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            else -> return TriggerSubscriptionResult.Failure(
                "Android trigger registrar does not support ${trigger::class.simpleName}.",
            )
        }

        val receiver = if (trigger is RuntimeStep.ObserveBatteryLevel) {
            var lastLevel: Int? = null
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                        if (level >= 0 && scale > 0) {
                            val percentage = (level * 100 / scale)
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
                        onTriggered(RuntimeTriggerEvent())
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
}

class AndroidConditionEvaluator(
    context: Context,
) : RuntimeConditionEvaluator {
    private val keyguardManager =
        context.getSystemService(KeyguardManager::class.java)
    private val appContext = context.applicationContext

    override fun evaluate(condition: RuntimeStep, context: RuntimeContext): ConditionResult =
        evaluateValueCondition(condition, context) ?: when (condition) {
            is RuntimeStep.CheckDeviceUnlocked -> {
                if (keyguardManager == null) {
                    ConditionResult.Failed("Android keyguard service is unavailable.")
                } else if (keyguardManager.isDeviceLocked) {
                    ConditionResult.Blocked("The device is locked.")
                } else {
                    ConditionResult.Passed
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
            else -> ConditionResult.Failed(
                "Unsupported Android condition ${condition::class.simpleName}.",
            )
        }
}

class AndroidActionExecutor(
    context: Context,
) : RuntimeActionExecutor {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val nextNotificationId = AtomicInteger(1)

    override fun execute(action: RuntimeStep, context: RuntimeContext): ActionResult =
        executeVariableAction(action, context) ?: when (action) {
            is RuntimeStep.ShowNotification -> show(action, context)
            is RuntimeStep.WriteLog -> writeLog(action, context)
            is RuntimeStep.SendSms -> sendSms(action, context)
            is RuntimeStep.LaunchApp -> launchApp(action)
            is RuntimeStep.OpenWebUrl -> openWebUrl(action)
            else -> ActionResult.Failed(
                "Unsupported Android action ${action::class.simpleName}.",
            )
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

    private companion object {
        const val CHANNEL_ID = "zerobit_macro_notifications"
        const val CHANNEL_NAME = "Macro notifications"
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
