/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRuntimeTest {
    @Test
    fun schedulesNextOccurrenceThenRearmsAfterDelivery() {
        val alarms = FakeAlarmPort()
        val events = mutableListOf<RuntimeTriggerEvent>()
        val source = ScheduleSubscriptionCoordinator(
            Clock.fixed(
                Instant.parse("2026-06-22T03:00:00Z"),
                ZoneId.of("UTC"),
            ),
            alarms,
        )

        val result = source.subscribe(
            macroId = "weekday",
            trigger = RuntimeStep.ObserveSchedule(
                blockId = "morning",
                schedule = ScheduleSpec(
                    localTime = LocalTime.of(9, 0),
                    daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                    zoneId = ZoneId.of("Asia/Kolkata"),
                ),
            ),
            onTriggered = events::add,
        )

        require(result is TriggerSubscriptionResult.Success)
        assertEquals(
            Instant.parse("2026-06-22T03:30:00Z"),
            alarms.requests.single().occurrence,
        )
        assertEquals("weekday:morning", alarms.requests.single().subscriptionId)

        alarms.deliverLatest()

        assertEquals(
            listOf(
                RuntimeTriggerEvent(
                    mapOf(
                        "schedule.instant" to
                            MacroValue.Text("2026-06-22T03:30:00Z"),
                        "schedule.local_time" to
                            MacroValue.Text("2026-06-22T09:00+05:30[Asia/Kolkata]"),
                    ),
                ),
            ),
            events,
        )
        assertEquals(
            Instant.parse("2026-06-23T03:30:00Z"),
            alarms.requests.last().occurrence,
        )
    }

    @Test
    fun cancellationCancelsAlarmAndSuppressesDelivery() {
        val alarms = FakeAlarmPort()
        val events = mutableListOf<RuntimeTriggerEvent>()
        val source = ScheduleSubscriptionCoordinator(
            Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("UTC")),
            alarms,
        )
        val result = source.subscribe(
            macroId = "daily-macro",
            trigger = RuntimeStep.ObserveSchedule(
                "daily",
                ScheduleSpec(
                    LocalTime.NOON,
                    zoneId = ZoneId.of("UTC"),
                ),
            ),
            onTriggered = events::add,
        )
        require(result is TriggerSubscriptionResult.Success)

        result.cancellation.cancel()
        alarms.deliverLatest()

        assertTrue(alarms.latestCancelled)
        assertTrue(events.isEmpty())
        assertEquals(1, alarms.requests.size)
    }

    @Test
    fun recurrenceFailureIsContainedAfterCurrentEventWasDelivered() {
        val alarms = FakeAlarmPort(failAfterSchedules = 1)
        val failures = mutableListOf<String>()
        val events = mutableListOf<RuntimeTriggerEvent>()
        val source = ScheduleSubscriptionCoordinator(
            clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")),
            alarms = alarms,
            recurringFailure = { id, _ -> failures += id },
        )
        val result = source.subscribe(
            macroId = "macro",
            trigger = RuntimeStep.ObserveSchedule(
                "daily",
                ScheduleSpec(LocalTime.NOON, zoneId = ZoneId.of("UTC")),
            ),
            onTriggered = events::add,
        )
        require(result is TriggerSubscriptionResult.Success)

        alarms.deliverLatest()

        assertEquals(1, events.size)
        assertEquals(listOf("macro:daily"), failures)
    }

    private class FakeAlarmPort(
        private val failAfterSchedules: Int? = null,
    ) : OneShotScheduleAlarmPort {
        val requests = mutableListOf<ScheduleAlarmRequest>()
        private val callbacks = mutableListOf<() -> Unit>()
        var latestCancelled = false

        override fun schedule(
            request: ScheduleAlarmRequest,
            onDelivered: () -> Unit,
        ): RuntimeCancellation {
            if (
                failAfterSchedules != null &&
                requests.size >= failAfterSchedules
            ) {
                throw IllegalStateException("Alarm service failed.")
            }
            requests += request
            callbacks += onDelivered
            latestCancelled = false
            return RuntimeCancellation { latestCancelled = true }
        }

        fun deliverLatest() {
            if (!latestCancelled) {
                callbacks.last().invoke()
            }
        }
    }
}
