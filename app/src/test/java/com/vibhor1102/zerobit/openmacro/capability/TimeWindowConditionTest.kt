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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWindowConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExplicitOvernightWindow() {
        val result = compiler.compile(validDocument(), "sha256:time-window")
        require(result is PlanCompilationResult.Success)
        val condition = result.plan.conditions.single() as RuntimeStep.CheckTimeWindow

        assertEquals("time-window", condition.blockId)
        assertEquals(LocalTime.of(22, 0), condition.window.startTime)
        assertEquals(LocalTime.of(6, 30), condition.window.endTime)
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), condition.window.daysOfWeek)
        assertEquals(ZoneId.of("Asia/Kolkata"), condition.window.zoneId)
    }

    @Test
    fun rejectsEqualTimesDuplicateDaysAndUnknownTimezone() {
        val config = validConfig().toMutableMap().apply {
            this["end_time"] = MacroValue.Text("22:00")
            this["days"] = MacroValue.ListValue(
                listOf(MacroValue.Text("fri"), MacroValue.Text("fri")),
            )
            this["timezone"] = MacroValue.Text("Mars/Olympus")
        }
        val result = compiler.compile(document(config), "sha256:invalid-time-window")
        require(result is PlanCompilationResult.Invalid)

        assertEquals(
            listOf(
                "empty_time_window",
                "invalid_time_window_days",
                "invalid_time_window_timezone",
            ),
            result.issues.map { it.code },
        )
    }

    @Test
    fun rejectsMalformedStartAndEndTimes() {
        val config = validConfig().toMutableMap().apply {
            this["start_time"] = MacroValue.Text("9:00")
            this["end_time"] = MacroValue.Text("24:00")
        }
        val result = compiler.compile(document(config), "sha256:invalid-window-times")
        require(result is PlanCompilationResult.Invalid)

        assertEquals(
            listOf("invalid_time_window_start", "invalid_time_window_end"),
            result.issues.map { it.code },
        )
    }

    private fun validDocument() = document(validConfig())

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("time-window", "Time window"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(MacroBlock("time-window", "openmacro.time.window", config)),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("inside")),
            ),
        ),
    )

    private fun validConfig(): Map<String, MacroValue> = mapOf(
        "start_time" to MacroValue.Text("22:00"),
        "end_time" to MacroValue.Text("06:30"),
        "days" to MacroValue.ListValue(
            listOf(MacroValue.Text("fri"), MacroValue.Text("sat")),
        ),
        "timezone" to MacroValue.Text("Asia/Kolkata"),
    )
}
