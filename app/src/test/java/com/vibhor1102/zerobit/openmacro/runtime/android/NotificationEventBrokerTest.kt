/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.NotificationField
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeTriggerEvent
import com.vibhor1102.zerobit.openmacro.runtime.TriggerSubscriptionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationEventBrokerTest {
    @Test
    fun filtersPackageAndExposesOnlyRequestedFields() {
        val events = mutableListOf<RuntimeTriggerEvent>()
        val result = AndroidNotificationEventBroker.subscribe(
            RuntimeStep.ObserveNotification(
                blockId = "notification",
                packageName = "com.example.chat",
                capturedFields = setOf(NotificationField.TITLE),
            ),
            { event -> events += event },
        )
        require(result is TriggerSubscriptionResult.Success)

        AndroidNotificationEventBroker.publish(
            NotificationSnapshot(
                packageName = "com.other",
                title = "Ignored",
                text = "Ignored body",
            ),
        )
        AndroidNotificationEventBroker.publish(
            NotificationSnapshot(
                packageName = "com.example.chat",
                title = "Hello",
                text = "Private body",
            ),
        )

        assertEquals(
            listOf(
                RuntimeTriggerEvent(
                    mapOf("notification.title" to MacroValue.Text("Hello")),
                ),
            ),
            events,
        )

        result.cancellation.cancel()
    }

    @Test
    fun cancellationStopsFutureDelivery() {
        val events = mutableListOf<RuntimeTriggerEvent>()
        val result = AndroidNotificationEventBroker.subscribe(
            RuntimeStep.ObserveNotification(
                blockId = "notification",
                packageName = null,
                capturedFields = setOf(NotificationField.PACKAGE),
            ),
            { event -> events += event },
        )
        require(result is TriggerSubscriptionResult.Success)
        result.cancellation.cancel()

        AndroidNotificationEventBroker.publish(
            NotificationSnapshot("com.example", "Title", "Body"),
        )

        assertEquals(emptyList<RuntimeTriggerEvent>(), events)
    }
}
