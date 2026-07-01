/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.BatteryHealth
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryHealthConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesEveryBoundedHealthStateWithoutAccess() {
        listOf(
            "healthy" to BatteryHealth.HEALTHY,
            "overheating" to BatteryHealth.OVERHEATING,
            "cold" to BatteryHealth.COLD,
            "dead" to BatteryHealth.DEAD,
            "over_voltage" to BatteryHealth.OVER_VOLTAGE,
            "failure" to BatteryHealth.FAILURE,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(mapOf("health" to MacroValue.Text(source))), "sha256:health-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckBatteryHealth("battery-health", expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingUnknownNonTextAndDiagnosticConfiguration() {
        listOf(
            emptyMap(),
            mapOf("health" to MacroValue.Text("unknown")),
            mapOf("health" to MacroValue.Number(java.math.BigDecimal("2"))),
            mapOf(
                "health" to MacroValue.Text("healthy"),
                "vendor_code" to MacroValue.Number(java.math.BigDecimal("7")),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-health-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key") else listOf("invalid_battery_health"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("health-condition", "Health condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(MacroBlock("battery-health", "android.battery.health", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
