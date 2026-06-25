/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Schedules one occurrence. Android adapters may use AlarmManager while tests
 * can use a deterministic fake. Repeating behavior remains in shared code.
 */
fun interface OneShotScheduleAlarmPort {
    fun schedule(
        request: ScheduleAlarmRequest,
        onDelivered: () -> Unit,
    ): RuntimeCancellation
}

data class ScheduleAlarmRequest(
    val subscriptionId: String,
    val macroId: String,
    val blockId: String,
    val occurrence: Instant,
    val deliveryWindow: Duration,
)

interface ScheduleEventSource {
    fun subscribe(
        macroId: String,
        trigger: RuntimeStep.ObserveSchedule,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult
}

/**
 * Owns deterministic recurrence and sends only planned schedule data into the
 * macro. A platform alarm adapter owns wakeup behavior and reboot restoration.
 */
class ScheduleSubscriptionCoordinator(
    private val clock: Clock,
    private val alarms: OneShotScheduleAlarmPort,
    private val recurringFailure: (subscriptionId: String, problem: RuntimeException) -> Unit =
        { _, _ -> },
) : ScheduleEventSource {
    override fun subscribe(
        macroId: String,
        trigger: RuntimeStep.ObserveSchedule,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult {
        val subscription = Subscription(
            id = stableSubscriptionId(macroId, trigger.blockId),
            macroId = macroId,
            trigger = trigger,
            onTriggered = onTriggered,
        )
        return try {
            subscription.arm(clock.instant())
            TriggerSubscriptionResult.Success(
                RuntimeCancellation(subscription::cancel),
            )
        } catch (problem: RuntimeException) {
            subscription.cancel()
            TriggerSubscriptionResult.Failure(
                problem.message ?: "Could not schedule the next occurrence.",
            )
        }
    }

    private fun stableSubscriptionId(macroId: String, blockId: String): String =
        "$macroId:$blockId"

    private inner class Subscription(
        val id: String,
        val macroId: String,
        val trigger: RuntimeStep.ObserveSchedule,
        val onTriggered: (RuntimeTriggerEvent) -> Unit,
    ) {
        private val lock = Any()
        private var active = true
        private var alarm: RuntimeCancellation? = null

        fun arm(after: Instant) {
            val occurrence = trigger.schedule.nextOccurrence(after)
            val request = ScheduleAlarmRequest(
                subscriptionId = id,
                macroId = macroId,
                blockId = trigger.blockId,
                occurrence = occurrence.toInstant(),
                deliveryWindow = trigger.schedule.deliveryWindow(),
            )
            val scheduled = alarms.schedule(request) {
                delivered(occurrence.toInstant())
            }
            synchronized(lock) {
                if (active) {
                    alarm = scheduled
                } else {
                    scheduled.cancel()
                }
            }
        }

        fun cancel() {
            val current = synchronized(lock) {
                if (!active) {
                    return
                }
                active = false
                alarm.also { alarm = null }
            }
            current?.cancel()
        }

        private fun delivered(occurrence: Instant) {
            synchronized(lock) {
                if (!active) {
                    return
                }
                alarm = null
            }
            val local = occurrence.atZone(trigger.schedule.zoneId)
            try {
                onTriggered(
                    RuntimeTriggerEvent(
                        mapOf(
                            "schedule.instant" to MacroValue.Text(occurrence.toString()),
                            "schedule.local_time" to MacroValue.Text(local.toString()),
                        ),
                    ),
                )
            } finally {
                synchronized(lock) {
                    if (!active) {
                        return
                    }
                }
                try {
                    arm(occurrence)
                } catch (problem: RuntimeException) {
                    recurringFailure(id, problem)
                }
            }
        }
    }
}

object UnsupportedScheduleEventSource : ScheduleEventSource {
    override fun subscribe(
        macroId: String,
        trigger: RuntimeStep.ObserveSchedule,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult = TriggerSubscriptionResult.Failure(
        "No durable schedule alarm adapter is configured.",
    )
}
