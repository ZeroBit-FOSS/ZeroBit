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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class SetAlarmActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesBoundedAlarmWithoutClockDataAccess() {
        val result = compiler.compile(document(7, 30, "Morning", true), "sha256:alarm")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.SetAlarm(
                blockId = "set-alarm",
                hour = 7,
                minute = 30,
                label = "Morning",
                skipUi = true,
            ),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun acceptsMissingOptionalLabel() {
        val result = compiler.compile(document(23, 59, null, false), "sha256:alarm-no-label")
        require(result is PlanCompilationResult.Success)
        assertEquals(null, (result.plan.actions.single() as RuntimeStep.SetAlarm).label)
    }

    @Test
    fun rejectsOutOfRangeFractionalAndUnknownFields() {
        listOf(
            document(24, 0, null, false) to "invalid_alarm_hour",
            document(12, 60, null, false) to "invalid_alarm_minute",
            document(BigDecimal("7.5"), BigDecimal.ZERO, null, false) to "invalid_alarm_hour",
            document(7, 0, null, false, mapOf("ringtone" to MacroValue.Text("content://tone"))) to
                "unknown_config",
        ).forEachIndexed { index, (document, code) ->
            val result = compiler.compile(document, "sha256:invalid-alarm-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf(code), result.issues.map { it.code })
        }
    }

    private fun document(
        hour: Number,
        minute: Number,
        label: String?,
        skipUi: Boolean,
        extra: Map<String, MacroValue> = emptyMap(),
    ): OpenMacroDocument {
        val config = mutableMapOf<String, MacroValue>(
            "hour" to MacroValue.Number(hour.toBigDecimal()),
            "minute" to MacroValue.Number(minute.toBigDecimal()),
            "skipUi" to MacroValue.Boolean(skipUi),
        )
        label?.let { config["label"] = MacroValue.Text(it) }
        config += extra
        return OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("alarm", "Alarm"),
            triggers = listOf(MacroBlock("power", "android.power.connected")),
            actions = listOf(MacroBlock("set-alarm", "android.alarm.set", config)),
        )
    }

    private fun Number.toBigDecimal(): BigDecimal = when (this) {
        is BigDecimal -> this
        else -> BigDecimal(toString())
    }
}
