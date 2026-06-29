/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RingerMode
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import org.junit.Assert.assertEquals
import org.junit.Test

class RingerModeConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesEveryRingerMode() {
        listOf(
            "normal" to RingerMode.NORMAL,
            "vibrate" to RingerMode.VIBRATE,
            "silent" to RingerMode.SILENT,
        ).forEach { (mode, expected) ->
            val result = compiler.compile(document(mode), "sha256:ringer-$mode")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckRingerMode("ringer-mode", expected),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsMissingOrUnknownMode() {
        listOf(null, "do_not_disturb").forEachIndexed { index, mode ->
            val result = compiler.compile(document(mode), "sha256:invalid-ringer-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_ringer_mode"), result.issues.map { it.code })
        }
    }

    private fun document(mode: String?) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("ringer", "Ringer"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock(
                id = "ringer-mode",
                type = "android.ringer-mode.state",
                config = if (mode == null) emptyMap() else {
                    mapOf("mode" to MacroValue.Text(mode))
                },
            ),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
