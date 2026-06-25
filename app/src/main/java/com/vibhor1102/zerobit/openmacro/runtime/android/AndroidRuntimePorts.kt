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
import android.os.Build
import com.vibhor1102.zerobit.R
import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.runtime.ActionResult
import com.vibhor1102.zerobit.openmacro.runtime.ConditionResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeActionExecutor
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeCancellation
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeConditionEvaluator
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePermissionChecker
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTaskDispatcher
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerRegistrar
import com.vibhor1102.zerobit.openmacro.runtime.TriggerSubscriptionResult
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AndroidPowerTriggerRegistrar(
    context: Context,
) : RuntimeTriggerRegistrar {
    private val appContext = context.applicationContext

    override fun subscribe(
        macroId: String,
        trigger: RuntimeStep,
        onTriggered: () -> Unit,
    ): TriggerSubscriptionResult {
        if (trigger !is RuntimeStep.ObservePowerConnected) {
            return TriggerSubscriptionResult.Failure(
                "Android power registrar does not support ${trigger::class.simpleName}.",
            )
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                    onTriggered()
                }
            }
        }
        return try {
            registerReceiver(receiver)
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
                problem.message ?: "Could not register Android power receiver.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun registerReceiver(receiver: BroadcastReceiver) {
        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
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

    override fun evaluate(condition: RuntimeStep): ConditionResult = when (condition) {
        is RuntimeStep.CheckDeviceUnlocked -> {
            if (keyguardManager == null) {
                ConditionResult.Failed("Android keyguard service is unavailable.")
            } else if (keyguardManager.isDeviceLocked) {
                ConditionResult.Blocked("The device is locked.")
            } else {
                ConditionResult.Passed
            }
        }
        else -> ConditionResult.Failed(
            "Unsupported Android condition ${condition::class.simpleName}.",
        )
    }
}

class AndroidNotificationActionExecutor(
    context: Context,
) : RuntimeActionExecutor {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val nextNotificationId = AtomicInteger(1)

    override fun execute(action: RuntimeStep): ActionResult = when (action) {
        is RuntimeStep.ShowNotification -> show(action)
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
