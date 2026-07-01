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
import org.junit.Test

class LowRamDeviceConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesBothClassificationsWithoutAccess() {
        listOf("low_ram" to true, "regular" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(mapOf("state" to MacroValue.Text(state))), "sha256:ram-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckLowRamDevice("ram-classification", expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingUnknownNonTextAndMemoryThresholdConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("medium_ram")),
            mapOf("state" to MacroValue.Boolean(true)),
            mapOf(
                "state" to MacroValue.Text("low_ram"),
                "minimum_free_megabytes" to MacroValue.Number(512.0),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-ram-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key") else listOf("invalid_low_ram_device_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("ram-classification", "RAM classification"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(MacroBlock("ram-classification", "android.device.low-ram", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
