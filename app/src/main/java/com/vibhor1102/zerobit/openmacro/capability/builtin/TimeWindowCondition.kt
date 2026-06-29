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
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.TimeWindowSpec
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object TimeWindowCondition : CapabilityDefinition {
    override val type = "openmacro.time.window"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Time window"
    override val description =
        "Checks a local daytime or overnight window in an explicit timezone."
    override val creation = CapabilityCreation(
        idBase = "time-window",
        defaultConfig = mapOf(
            "start_time" to MacroValue.Text("09:00"),
            "end_time" to MacroValue.Text("17:00"),
            "days" to MacroValue.ListValue(
                listOf("mon", "tue", "wed", "thu", "fri").map(MacroValue::Text),
            ),
            "timezone" to MacroValue.Text("UTC"),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "start_time",
            label = "Start Time",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Included 24-hour local time, for example 09:00.",
        ),
        CapabilityField(
            key = "end_time",
            label = "End Time",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Excluded 24-hour local time; earlier values create an overnight window.",
        ),
        CapabilityField(
            key = "days",
            label = "Starting Days",
            kind = CapabilityFieldKind.TEXT_LIST,
            required = true,
            help = "Days on which the window starts.",
            allowedValues = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
        ),
        CapabilityField(
            key = "timezone",
            label = "Timezone",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "An IANA timezone such as Asia/Kolkata or America/New_York.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(
                block.rejectUnknownConfig(
                    setOf("start_time", "end_time", "days", "timezone"),
                    path,
                ),
            )
            val start = parseLocalTime(block.config["start_time"])
            val end = parseLocalTime(block.config["end_time"])
            if (start == null) {
                add(issue(path, "start_time", "invalid_time_window_start", "Use a 24-hour time such as 09:00."))
            }
            if (end == null) {
                add(issue(path, "end_time", "invalid_time_window_end", "Use a 24-hour time such as 17:00."))
            }
            if (start != null && start == end) {
                add(
                    issue(
                        path,
                        "end_time",
                        "empty_time_window",
                        "Start and end must differ; use an explicit range instead of an ambiguous full day.",
                    ),
                )
            }
            if (parseWeekdays(block.config["days"]) == null) {
                add(
                    issue(
                        path,
                        "days",
                        "invalid_time_window_days",
                        "Choose one or more unique starting days.",
                    ),
                )
            }
            if (parseTimezone(block.config["timezone"]) == null) {
                add(
                    issue(
                        path,
                        "timezone",
                        "invalid_time_window_timezone",
                        "Use a recognized IANA timezone such as Asia/Kolkata.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val window = block.toTimeWindowSpec()
        val overnight = if (window.startTime > window.endTime) " overnight" else ""
        return "Continue from ${window.startTime} until ${window.endTime}$overnight on " +
            "${window.daysOfWeek.shortNames()} in ${window.zoneId.id}."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckTimeWindow(block.id, block.toTimeWindowSpec())

    private fun MacroBlock.toTimeWindowSpec() = TimeWindowSpec(
        startTime = requireNotNull(parseLocalTime(config["start_time"])),
        endTime = requireNotNull(parseLocalTime(config["end_time"])),
        daysOfWeek = requireNotNull(parseWeekdays(config["days"])),
        zoneId = requireNotNull(parseTimezone(config["timezone"])),
    )

    private fun issue(path: String, key: String, code: String, message: String) =
        ValidationIssue("$path.config.$key", code, message)
}
