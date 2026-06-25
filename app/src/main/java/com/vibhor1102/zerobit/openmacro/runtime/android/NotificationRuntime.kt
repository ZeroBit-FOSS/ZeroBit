/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.NotificationField
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeCancellation
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerEvent
import com.vibhor1102.zerobit.openmacro.runtime.TriggerSubscriptionResult
import java.util.concurrent.atomic.AtomicBoolean

interface NotificationEventSource {
    fun subscribe(
        trigger: RuntimeStep.ObserveNotification,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult
}

object AndroidNotificationEventBroker : NotificationEventSource {
    private val lock = Any()
    private var nextSubscriptionId = 1L
    private val subscriptions = linkedMapOf<Long, Subscription>()

    override fun subscribe(
        trigger: RuntimeStep.ObserveNotification,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult {
        val id = synchronized(lock) {
            val subscriptionId = nextSubscriptionId++
            subscriptions[subscriptionId] = Subscription(trigger, onTriggered)
            subscriptionId
        }
        val cancelled = AtomicBoolean(false)
        return TriggerSubscriptionResult.Success(
            RuntimeCancellation {
                if (cancelled.compareAndSet(false, true)) {
                    synchronized(lock) {
                        subscriptions.remove(id)
                    }
                }
            },
        )
    }

    fun publish(notification: NotificationSnapshot) {
        val snapshot = synchronized(lock) { subscriptions.values.toList() }
        snapshot.forEach { subscription ->
            if (
                subscription.trigger.packageName != null &&
                subscription.trigger.packageName != notification.packageName
            ) {
                return@forEach
            }
            val values = buildMap {
                subscription.trigger.capturedFields.forEach { field ->
                    when (field) {
                        NotificationField.PACKAGE -> put(
                            field.contextKey,
                            MacroValue.Text(notification.packageName),
                        )
                        NotificationField.TITLE -> notification.title
                            ?.let { put(field.contextKey, MacroValue.Text(it)) }
                        NotificationField.TEXT -> notification.text
                            ?.let { put(field.contextKey, MacroValue.Text(it)) }
                    }
                }
            }
            subscription.onTriggered(RuntimeTriggerEvent(values))
        }
    }

    private data class Subscription(
        val trigger: RuntimeStep.ObserveNotification,
        val onTriggered: (RuntimeTriggerEvent) -> Unit,
    )
}

data class NotificationSnapshot(
    val packageName: String,
    val title: String?,
    val text: String?,
)

class ZeroBitNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            AndroidNotificationEventBroker.publish(
                NotificationSnapshot(
                    packageName = sbn.packageName,
                    title = sbn.notification.extras
                        .getCharSequence(android.app.Notification.EXTRA_TITLE)
                        ?.toString(),
                    text = sbn.notification.extras
                        .getCharSequence(android.app.Notification.EXTRA_TEXT)
                        ?.toString(),
                ),
            )
        }
    }
}
