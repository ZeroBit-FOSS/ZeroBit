/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.ScheduleDelivery
import com.vibhor1102.zerobit.openmacro.runtime.ScheduleSpec
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeScheduleTriggerTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExplicitWallClockScheduleAndExactAccess() {
        val result = compiler.compile(
            document(
                MacroBlock(
                    id = "weekday-morning",
                    type = "android.time.schedule",
                    config = mapOf(
                        "time" to MacroValue.Text("07:30"),
                        "days" to MacroValue.ListValue(
                            listOf(MacroValue.Text("mon"), MacroValue.Text("fri")),
                        ),
                        "timezone" to MacroValue.Text("Asia/Kolkata"),
                        "delivery" to MacroValue.Text("exact"),
                        "window_minutes" to MacroValue.Number(BigDecimal("10")),
                    ),
                ),
            ),
            "sha256:schedule",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.ObserveSchedule(
                blockId = "weekday-morning",
                schedule = ScheduleSpec(
                    localTime = LocalTime.of(7, 30),
                    daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    zoneId = ZoneId.of("Asia/Kolkata"),
                    delivery = ScheduleDelivery.EXACT,
                    windowMinutes = 10,
                ),
            ),
            result.plan.triggers.single(),
        )
        assertEquals(
            setOf(AndroidPermission.SCHEDULE_EXACT_ALARM_ACCESS),
            result.plan.requiredPermissions,
        )
    }

    @Test
    fun rejectsInvalidTimeDaysTimezoneAndWindowTogether() {
        val result = compiler.compile(
            document(
                MacroBlock(
                    id = "broken",
                    type = "android.time.schedule",
                    config = mapOf(
                        "time" to MacroValue.Text("25:90"),
                        "days" to MacroValue.ListValue(
                            listOf(MacroValue.Text("mon"), MacroValue.Text("mon")),
                        ),
                        "timezone" to MacroValue.Text("Moon/Base"),
                        "delivery" to MacroValue.Text("eventually"),
                        "window_minutes" to MacroValue.Number(BigDecimal("1.5")),
                    ),
                ),
            ),
            "sha256:broken",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            setOf(
                "invalid_schedule_time",
                "invalid_schedule_days",
                "invalid_schedule_timezone",
                "invalid_schedule_delivery",
                "invalid_schedule_window",
            ),
            result.issues.map { it.code }.toSet(),
        )
    }

    private fun document(trigger: MacroBlock) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("schedule", "Schedule"),
        triggers = listOf(trigger),
        conditions = emptyList(),
        actions = listOf(MacroBlock("stop", "openmacro.flow.stop")),
    )
}
