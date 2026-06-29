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
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventDraftActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesDeterministicDraftWithoutCalendarPermission() {
        val result = compiler.compile(validDocument(), "sha256:calendar")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.CreateCalendarEventDraft(
                blockId = "calendar-event-draft",
                startMillis = Instant.parse("2026-07-01T03:30:00Z").toEpochMilli(),
                endMillis = Instant.parse("2026-07-01T04:30:00Z").toEpochMilli(),
                title = RuntimeValueSource.Literal(MacroValue.Text("Planning")),
                location = RuntimeValueSource.Literal(MacroValue.Text("Office")),
                description = null,
            ),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsInvalidTimeRangeTimezoneAndArbitraryCalendarFields() {
        listOf(
            mapOf("end" to MacroValue.Text("2026-07-01T08:00")),
            mapOf("timezone" to MacroValue.Text("Not/AZone")),
            mapOf("calendarId" to MacroValue.Text("private")),
        ).forEachIndexed { index, override ->
            val result = compiler.compile(validDocument(override), "sha256:bad-calendar-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if ("calendarId" in override) "unknown_config" else "invalid_calendar_event_time",
                result.issues.single().code,
            )
        }
    }

    private fun validDocument(override: Map<String, MacroValue> = emptyMap()): OpenMacroDocument {
        val config = mapOf(
            "start" to MacroValue.Text("2026-07-01T09:00"),
            "end" to MacroValue.Text("2026-07-01T10:00"),
            "timezone" to MacroValue.Text("Asia/Kolkata"),
            "title" to MacroValue.Text("Planning"),
            "location" to MacroValue.Text("Office"),
        ) + override
        return OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("calendar", "Calendar"),
            triggers = listOf(MacroBlock("power", "android.power.connected")),
            actions = listOf(MacroBlock("calendar-event-draft", "android.calendar.event-draft", config)),
        )
    }
}
