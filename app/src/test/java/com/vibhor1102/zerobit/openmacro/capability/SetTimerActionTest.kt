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

class SetTimerActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesBoundedTimerWithoutClockDataAccess() {
        val result = compiler.compile(document(BigDecimal("300"), "Tea", true), "sha256:timer")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.SetTimer(
                blockId = "set-timer",
                durationSeconds = 300,
                label = "Tea",
                skipUi = true,
            ),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun acceptsMaximumDurationWithoutLabel() {
        val result = compiler.compile(document(BigDecimal("86400"), null, false), "sha256:timer-max")
        require(result is PlanCompilationResult.Success)
        assertEquals(null, (result.plan.actions.single() as RuntimeStep.SetTimer).label)
    }

    @Test
    fun rejectsZeroFractionalOversizedAndUnknownFields() {
        listOf(
            document(BigDecimal.ZERO, null, false) to "invalid_timer_duration",
            document(BigDecimal("1.5"), null, false) to "invalid_timer_duration",
            document(BigDecimal("86401"), null, false) to "invalid_timer_duration",
            document(
                BigDecimal.ONE,
                null,
                false,
                mapOf("ringtone" to MacroValue.Text("content://tone")),
            ) to "unknown_config",
        ).forEachIndexed { index, (document, code) ->
            val result = compiler.compile(document, "sha256:invalid-timer-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf(code), result.issues.map { it.code })
        }
    }

    private fun document(
        seconds: BigDecimal,
        label: String?,
        skipUi: Boolean,
        extra: Map<String, MacroValue> = emptyMap(),
    ): OpenMacroDocument {
        val config = mutableMapOf<String, MacroValue>(
            "seconds" to MacroValue.Number(seconds),
            "skipUi" to MacroValue.Boolean(skipUi),
        )
        label?.let { config["label"] = MacroValue.Text(it) }
        config += extra
        return OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("timer", "Timer"),
            triggers = listOf(MacroBlock("power", "android.power.connected")),
            actions = listOf(MacroBlock("set-timer", "android.timer.set", config)),
        )
    }
}
