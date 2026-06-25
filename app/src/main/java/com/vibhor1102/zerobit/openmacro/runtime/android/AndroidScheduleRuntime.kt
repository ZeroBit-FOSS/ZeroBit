/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vibhor1102.zerobit.ZeroBitApplication
import com.vibhor1102.zerobit.openmacro.runtime.OneShotScheduleAlarmPort
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeCancellation
import com.vibhor1102.zerobit.openmacro.runtime.ScheduleAlarmRequest
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class AndroidOneShotScheduleAlarmPort(
    context: Context,
) : OneShotScheduleAlarmPort {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(
        request: ScheduleAlarmRequest,
        onDelivered: () -> Unit,
    ): RuntimeCancellation {
        val manager = alarmManager
            ?: throw IllegalStateException("Android alarm service is unavailable.")
        val delivery = AndroidScheduleAlarmDeliveryBroker.register(
            request.deliveryKey(),
            onDelivered,
        )
        val pendingIntent = pendingIntent(request)
        try {
            val triggerAt = request.occurrence.toEpochMilli()
            if (request.deliveryWindow.isZero) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
            } else {
                manager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    request.deliveryWindow.toMillis(),
                    pendingIntent,
                )
            }
        } catch (problem: RuntimeException) {
            delivery.cancel()
            throw problem
        }
        return RuntimeCancellation {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
            delivery.cancel()
        }
    }

    private fun pendingIntent(request: ScheduleAlarmRequest): PendingIntent {
        val intent = Intent(appContext, ZeroBitScheduleAlarmReceiver::class.java)
            .setAction(ACTION_DELIVER_SCHEDULE)
            .setData(
                Uri.Builder()
                    .scheme("zerobit")
                    .authority("schedule")
                    .appendPath(request.subscriptionId)
                    .appendPath(request.occurrence.toEpochMilli().toString())
                    .build(),
            )
            .putExtra(EXTRA_SUBSCRIPTION_ID, request.subscriptionId)
            .putExtra(EXTRA_MACRO_ID, request.macroId)
            .putExtra(EXTRA_BLOCK_ID, request.blockId)
            .putExtra(EXTRA_OCCURRENCE_EPOCH_MILLIS, request.occurrence.toEpochMilli())
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_DELIVER_SCHEDULE =
            "com.vibhor1102.zerobit.action.DELIVER_SCHEDULE"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_BLOCK_ID = "block_id"
        const val EXTRA_OCCURRENCE_EPOCH_MILLIS = "occurrence_epoch_millis"
    }
}

private fun ScheduleAlarmRequest.deliveryKey(): String =
    "$subscriptionId@${occurrence.toEpochMilli()}"

/**
 * Keeps callback replacement safe when process restoration rearms the same
 * stable macro/block subscription before an older cancellation is released.
 */
object AndroidScheduleAlarmDeliveryBroker {
    private val lock = Any()
    private val nextToken = AtomicLong(1)
    private val deliveries = mutableMapOf<String, Delivery>()

    fun register(
        subscriptionId: String,
        callback: () -> Unit,
    ): RuntimeCancellation {
        val token = nextToken.getAndIncrement()
        synchronized(lock) {
            deliveries[subscriptionId] = Delivery(token, callback)
        }
        return RuntimeCancellation {
            synchronized(lock) {
                if (deliveries[subscriptionId]?.token == token) {
                    deliveries.remove(subscriptionId)
                }
            }
        }
    }

    fun deliver(subscriptionId: String): Boolean {
        val callback = synchronized(lock) {
            deliveries[subscriptionId]?.callback
        } ?: return false
        callback()
        return true
    }

    private data class Delivery(
        val token: Long,
        val callback: () -> Unit,
    )
}

class ZeroBitScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != AndroidOneShotScheduleAlarmPort.ACTION_DELIVER_SCHEDULE) {
            return
        }
        val subscriptionId = intent.getStringExtra(
            AndroidOneShotScheduleAlarmPort.EXTRA_SUBSCRIPTION_ID,
        ) ?: return
        val macroId = intent.getStringExtra(
            AndroidOneShotScheduleAlarmPort.EXTRA_MACRO_ID,
        ) ?: return
        val blockId = intent.getStringExtra(
            AndroidOneShotScheduleAlarmPort.EXTRA_BLOCK_ID,
        ) ?: return
        val occurrenceMillis = intent.getLongExtra(
            AndroidOneShotScheduleAlarmPort.EXTRA_OCCURRENCE_EPOCH_MILLIS,
            Long.MIN_VALUE,
        )
        if (occurrenceMillis == Long.MIN_VALUE) {
            return
        }
        val occurrence = Instant.ofEpochMilli(occurrenceMillis)
        val deliveryKey = "$subscriptionId@$occurrenceMillis"
        if (AndroidScheduleAlarmDeliveryBroker.deliver(deliveryKey)) {
            return
        }
        (context?.applicationContext as? ZeroBitApplication)
            ?.runtimeController
            ?.deliverScheduleAlarm(macroId, blockId, occurrence)
    }
}
