/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.BatteryTemperatureComparison
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryTemperatureTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesExactTenthsCrossingsWithBoundedContext() {
        listOf(
            "below" to BatteryTemperatureComparison.BELOW,
            "above" to BatteryTemperatureComparison.ABOVE,
            "equals" to BatteryTemperatureComparison.EQUALS,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(BigDecimal("40.5"), source), "sha256:temp-trigger-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveBatteryTemperature("temperature-crossing", 405, expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("battery.temperature_celsius"),
            registry.find("android.battery.temperature-crossing")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsExtraPrecisionOutOfRangeUnknownAndUnitConfiguration() {
        val invalid = listOf(
            config(BigDecimal("40.55"), "above"),
            config(BigDecimal("100.1"), "above"),
            config(BigDecimal("40"), "near"),
            config(BigDecimal("40"), "above") + ("unit" to MacroValue.Text("fahrenheit")),
        )
        invalid.forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-temp-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key")
                else listOf("invalid_battery_temperature_trigger"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(celsius: BigDecimal, comparison: String) =
        document(config(celsius, comparison))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("temperature-trigger", "Temperature trigger"),
        triggers = listOf(
            MacroBlock("temperature-crossing", "android.battery.temperature-crossing", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )

    private fun config(celsius: BigDecimal, comparison: String) = mapOf(
        "celsius" to MacroValue.Number(celsius),
        "comparison" to MacroValue.Text(comparison),
    )
}
