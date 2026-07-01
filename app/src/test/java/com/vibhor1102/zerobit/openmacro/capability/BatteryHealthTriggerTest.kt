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

class BatteryHealthTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesEveryBoundedTransitionWithCanonicalContext() {
        listOf(
            "healthy" to BatteryHealth.HEALTHY,
            "overheating" to BatteryHealth.OVERHEATING,
            "cold" to BatteryHealth.COLD,
            "dead" to BatteryHealth.DEAD,
            "over_voltage" to BatteryHealth.OVER_VOLTAGE,
            "failure" to BatteryHealth.FAILURE,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(mapOf("health" to MacroValue.Text(source))), "sha256:health-trigger-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveBatteryHealth("health-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("battery.health"),
            registry.find("android.battery.health-changed")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsUnknownNonTextAndDiagnosticConfiguration() {
        listOf(
            mapOf("health" to MacroValue.Text("unknown")),
            mapOf("health" to MacroValue.Boolean(true)),
            mapOf(
                "health" to MacroValue.Text("healthy"),
                "vendor_code" to MacroValue.Number(java.math.BigDecimal("7")),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-health-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_battery_health_trigger"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("health-trigger", "Health trigger"),
        triggers = listOf(
            MacroBlock("health-changed", "android.battery.health-changed", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )
}
