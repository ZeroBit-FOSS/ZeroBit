/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.ScheduleDelivery
import com.vibhor1102.zerobit.openmacro.runtime.ScheduleSpec
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object TimeScheduleTrigger : CapabilityDefinition {
    override val type = "android.time.schedule"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Time schedule"
    override val description =
        "Starts on selected days at a local wall-clock time in an explicit timezone."
    override val creation = CapabilityCreation(
        idBase = "time-schedule",
        setup = CapabilitySetup(
            fieldKeys = listOf(
                "time",
                "days",
                "timezone",
                "delivery",
                "window_minutes",
            ),
            initialConfig = mapOf(
                "time" to MacroValue.Text("08:00"),
                "days" to MacroValue.ListValue(
                    listOf("mon", "tue", "wed", "thu", "fri")
                        .map(MacroValue::Text),
                ),
                "timezone" to MacroValue.Text("UTC"),
                "delivery" to MacroValue.Text("windowed"),
                "window_minutes" to MacroValue.Number(java.math.BigDecimal("15")),
            ),
        ),
    )
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "schedule.instant",
            type = MacroVariableType.TEXT,
            description = "The planned occurrence as an ISO-8601 instant.",
        ),
        TriggerOutput(
            key = "schedule.local_time",
            type = MacroVariableType.TEXT,
            description = "The planned local date and time with timezone.",
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "time",
            label = "Time",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "24-hour local time, for example 07:30 or 18:05.",
        ),
        CapabilityField(
            key = "days",
            label = "Days",
            kind = CapabilityFieldKind.TEXT_LIST,
            required = true,
            help = "One or more of mon, tue, wed, thu, fri, sat, sun.",
            allowedValues = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
        ),
        CapabilityField(
            key = "timezone",
            label = "Timezone",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "An IANA timezone such as Asia/Kolkata or America/New_York.",
        ),
        CapabilityField(
            key = "delivery",
            label = "Delivery",
            kind = CapabilityFieldKind.TEXT,
            required = false,
            help = "windowed is battery-friendly; exact requires Android special access.",
            advanced = true,
            allowedValues = listOf("windowed", "exact"),
        ),
        CapabilityField(
            key = "window_minutes",
            label = "Window (minutes)",
            kind = CapabilityFieldKind.NUMBER,
            required = false,
            help = "Allowed delivery window from 1 to 60 minutes.",
            advanced = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(
                block.rejectUnknownConfig(
                    setOf("time", "days", "timezone", "delivery", "window_minutes"),
                    path,
                ),
            )
            if (parseLocalTime(block.config["time"]) == null) {
                add(issue(path, "time", "invalid_schedule_time", "Use a 24-hour time such as 07:30."))
            }
            if (parseWeekdays(block.config["days"]) == null) {
                add(
                    issue(
                        path,
                        "days",
                        "invalid_schedule_days",
                        "Choose one or more unique days: mon, tue, wed, thu, fri, sat, sun.",
                    ),
                )
            }
            if (parseTimezone(block.config["timezone"]) == null) {
                add(
                    issue(
                        path,
                        "timezone",
                        "invalid_schedule_timezone",
                        "Use a recognized IANA timezone such as Asia/Kolkata.",
                    ),
                )
            }
            if (parseDelivery(block.config["delivery"]) == null) {
                add(
                    issue(
                        path,
                        "delivery",
                        "invalid_schedule_delivery",
                        "Delivery must be 'windowed' or 'exact'.",
                    ),
                )
            }
            if (parseWindow(block.config["window_minutes"]) == null) {
                add(
                    issue(
                        path,
                        "window_minutes",
                        "invalid_schedule_window",
                        "Window must be a whole number from 1 to 60.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val schedule = block.toScheduleSpec()
        val days = schedule.daysOfWeek.shortNames()
        val delivery = when (schedule.delivery) {
            ScheduleDelivery.WINDOWED ->
                "within ${schedule.windowMinutes} minutes to save battery"
            ScheduleDelivery.EXACT -> "at the exact requested time"
        }
        return "Start at ${schedule.localTime} on $days in ${schedule.zoneId.id}, $delivery."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        if (parseDelivery(block.config["delivery"]) == ScheduleDelivery.EXACT) {
            setOf(AndroidPermission.SCHEDULE_EXACT_ALARM_ACCESS)
        } else {
            emptySet()
        }

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveSchedule(block.id, block.toScheduleSpec())

    private fun MacroBlock.toScheduleSpec() = ScheduleSpec(
        localTime = requireNotNull(parseLocalTime(config["time"])),
        daysOfWeek = requireNotNull(parseWeekdays(config["days"])),
        zoneId = requireNotNull(parseTimezone(config["timezone"])),
        delivery = requireNotNull(parseDelivery(config["delivery"])),
        windowMinutes = requireNotNull(parseWindow(config["window_minutes"])),
    )

    private fun parseDelivery(value: MacroValue?): ScheduleDelivery? {
        if (value == null) {
            return ScheduleDelivery.WINDOWED
        }
        val text = (value as? MacroValue.Text)?.value ?: return null
        return when (text) {
        "windowed" -> ScheduleDelivery.WINDOWED
        "exact" -> ScheduleDelivery.EXACT
        else -> null
        }
    }

    private fun parseWindow(value: MacroValue?): Int? {
        if (value == null) {
            return ScheduleSpec.DEFAULT_WINDOW_MINUTES
        }
        val number = (value as? MacroValue.Number)?.value ?: return null
        return number.toBigIntegerExactOrNull()
            ?.toIntExactOrNull()
            ?.takeIf { it in 1..ScheduleSpec.MAX_WINDOW_MINUTES }
    }

    private fun issue(
        path: String,
        key: String,
        code: String,
        message: String,
    ) = ValidationIssue("$path.config.$key", code, message)

    private fun java.math.BigDecimal.toBigIntegerExactOrNull() =
        runCatching { toBigIntegerExact() }.getOrNull()

    private fun java.math.BigInteger.toIntExactOrNull() =
        runCatching { intValueExact() }.getOrNull()

}
