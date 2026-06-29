/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.describeValueSource
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireTextSource
import com.vibhor1102.zerobit.openmacro.capability.validateTextSource
import com.vibhor1102.zerobit.openmacro.capability.valueSource
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.CalendarEventTimeSpec
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CALENDAR_DESCRIPTION_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CALENDAR_LOCATION_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CALENDAR_TITLE_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

object CalendarEventDraftAction : CapabilityDefinition {
    override val type = "android.calendar.event-draft"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Create calendar event draft"
    override val description = "Opens a validated event draft in a calendar app."
    override val creation = CapabilityCreation(
        idBase = "calendar-event-draft",
        setup = CapabilitySetup(
            fieldKeys = listOf("start", "end", "timezone", "title", "location", "description"),
        ),
    )
    override val fields = listOf(
        CapabilityField("start", "Start", CapabilityFieldKind.TEXT, true, "Local date and time, for example 2026-07-01T09:00."),
        CapabilityField("end", "End", CapabilityFieldKind.TEXT, true, "Later local date and time in the same timezone."),
        CapabilityField("timezone", "Timezone", CapabilityFieldKind.TEXT, true, "An IANA timezone such as Asia/Kolkata."),
        CapabilityField("title", "Title", CapabilityFieldKind.TEXT, true, "Event title, up to 200 characters.", acceptsValueSources = true),
        CapabilityField("location", "Location (optional)", CapabilityFieldKind.TEXT, false, "Optional event location.", acceptsValueSources = true),
        CapabilityField("description", "Description (optional)", CapabilityFieldKind.MULTILINE_TEXT, false, "Optional event description.", acceptsValueSources = true),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(ALLOWED_KEYS, path))
        addAll(block.requireTextSource("title", path, MAX_CALENDAR_TITLE_LENGTH))
        addAll(block.optionalTextSourceIssues("location", path, MAX_CALENDAR_LOCATION_LENGTH))
        addAll(block.optionalTextSourceIssues("description", path, MAX_CALENDAR_DESCRIPTION_LENGTH))
        if (block.eventTimeSpecOrNull() == null) {
            add(
                ValidationIssue(
                    "$path.config.start",
                    "invalid_calendar_event_time",
                    "Use exact local start and end times, a recognized timezone, and a duration up to seven days.",
                ),
            )
        }
    }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> = listOf("title", "location", "description").flatMap { key ->
        block.validateTextSource(key, path, document, registry)
    }

    override fun explain(block: MacroBlock): String {
        val time = block.eventTimeSpecOrNull()
        return "Open a calendar draft for ${block.describeValueSource("title")} from ${
            time?.start ?: "an invalid start"
        } to ${time?.end ?: "an invalid end"} in ${time?.zoneId?.id ?: "an invalid timezone"}."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val time = requireNotNull(block.eventTimeSpecOrNull())
        return RuntimeStep.CreateCalendarEventDraft(
            blockId = block.id,
            startMillis = time.startInstant.toEpochMilli(),
            endMillis = time.endInstant.toEpochMilli(),
            title = block.valueSource("title"),
            location = block.config["location"]?.let { block.valueSource("location") },
            description = block.config["description"]?.let { block.valueSource("description") },
        )
    }

    private fun MacroBlock.optionalTextSourceIssues(
        key: String,
        path: String,
        maxLength: Int,
    ): List<ValidationIssue> = if (config[key] == null) emptyList() else requireTextSource(key, path, maxLength)

    private fun MacroBlock.eventTimeSpecOrNull(): CalendarEventTimeSpec? {
        val start = parseLocalDateTime(config["start"]) ?: return null
        val end = parseLocalDateTime(config["end"]) ?: return null
        val timezone = parseTimezone(config["timezone"]) ?: return null
        return runCatching { CalendarEventTimeSpec(start, end, timezone) }.getOrNull()
    }

    private fun parseLocalDateTime(value: MacroValue?): LocalDateTime? {
        val text = (value as? MacroValue.Text)?.value ?: return null
        if (!LOCAL_DATE_TIME_PATTERN.matches(text)) return null
        return try {
            LocalDateTime.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private val ALLOWED_KEYS = setOf("start", "end", "timezone", "title", "location", "description")
    private val LOCAL_DATE_TIME_PATTERN = Regex("""\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d""")
}
