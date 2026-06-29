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

class ShowAlarmsActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesConfigFreeClockActionWithoutDataAccess() {
        val result = compiler.compile(document(), "sha256:show-alarms")
        require(result is PlanCompilationResult.Success)

        assertEquals(RuntimeStep.ShowAlarms("show-alarms"), result.plan.actions.single())
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsArbitraryClockIntentFields() {
        val result = compiler.compile(
            document(mapOf("alarmId" to MacroValue.Number(java.math.BigDecimal.ONE))),
            "sha256:show-specific-alarm",
        )
        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("unknown_config"), result.issues.map { it.code })
    }

    private fun document(config: Map<String, MacroValue> = emptyMap()) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("alarms", "Alarms"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(MacroBlock("show-alarms", "android.alarm.show", config)),
    )
}
