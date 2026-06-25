/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.vibhor1102.zerobit.R
import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
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
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerRegistrar
import com.vibhor1102.zerobit.openmacro.runtime.TriggerSubscriptionResult
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AndroidTriggerRegistrar(
    context: Context,
) : RuntimeTriggerRegistrar {
    private val appContext = context.applicationContext

    override fun subscribe(
        macroId: String,
        trigger: RuntimeStep,
        onTriggered: () -> Unit,
    ): TriggerSubscriptionResult {
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
                                onTriggered()
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
                        onTriggered()
                    } else if (action == Intent.ACTION_SCREEN_ON && trigger is RuntimeStep.ObserveScreenOn) {
                        onTriggered()
                    } else if (action == Intent.ACTION_SCREEN_OFF && trigger is RuntimeStep.ObserveScreenOff) {
                        onTriggered()
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

    override fun evaluate(condition: RuntimeStep, context: RuntimeContext): ConditionResult = when (condition) {
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
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                ConditionResult.Failed("Android connectivity service is unavailable.")
            } else {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    ConditionResult.Blocked("Wi-Fi is not connected.")
                } else {
                    if (condition.ssid != null) {
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

    override fun execute(action: RuntimeStep, context: RuntimeContext): ActionResult = when (action) {
        is RuntimeStep.ShowNotification -> show(action)
        is RuntimeStep.WriteLog -> writeLog(action)
        is RuntimeStep.SendSms -> sendSms(action)
        else -> ActionResult.Failed(
            "Unsupported Android action ${action::class.simpleName}.",
        )
    }

    @SuppressLint("MissingPermission")
    private fun show(action: RuntimeStep.ShowNotification): ActionResult {
        val manager = notificationManager
            ?: return ActionResult.Failed("Android notification service is unavailable.")
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
                .setContentTitle(action.title)
                .setContentText(action.message)
                .setAutoCancel(true)
                .build()
            manager.notify(nextNotificationId.getAndIncrement(), notification)
            ActionResult.Succeeded
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not show the notification.")
        }
    }

    private fun writeLog(action: RuntimeStep.WriteLog): ActionResult {
        Log.i("ZeroBitMacro", action.message)
        // Also print to stdout for easy test assertions
        println("ZeroBitMacro: ${action.message}")
        return ActionResult.Succeeded
    }

    private fun sendSms(action: RuntimeStep.SendSms): ActionResult {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
                    ?: return ActionResult.Failed("SMS service is unavailable.")
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(action.phoneNumber, null, action.message, null, null)
            ActionResult.Succeeded
        } catch (problem: RuntimeException) {
            ActionResult.Failed(problem.message ?: "Could not send SMS.")
        }
    }

    private companion object {
        const val CHANNEL_ID = "zerobit_macro_notifications"
        const val CHANNEL_NAME = "Macro notifications"
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
        }
    }
}

class ExecutorRuntimeTaskDispatcher(
    private val executor: Executor,
) : RuntimeTaskDispatcher {
    override fun dispatch(task: () -> Unit) {
        executor.execute(task)
    }
}
