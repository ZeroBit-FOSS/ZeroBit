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

class BatteryTemperatureConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExactTenthsForEveryComparison() {
        listOf(
            "below" to BatteryTemperatureComparison.BELOW,
            "above" to BatteryTemperatureComparison.ABOVE,
            "equals" to BatteryTemperatureComparison.EQUALS,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(BigDecimal("40.5"), source), "sha256:temperature-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckBatteryTemperature("battery-temperature", 405, expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun acceptsBoundsAndNegativeTenths() {
        listOf(BigDecimal("-100"), BigDecimal("-0.5"), BigDecimal.ZERO, BigDecimal("100"))
            .forEachIndexed { index, celsius ->
                val result = compiler.compile(document(celsius, "equals"), "sha256:temperature-bound-$index")
                require(result is PlanCompilationResult.Success)
            }
    }

    @Test
    fun rejectsMissingExtraPrecisionOutOfRangeUnknownAndExtraConfiguration() {
        val invalid = listOf(
            emptyMap(),
            config(BigDecimal("40.55"), "above"),
            config(BigDecimal("-100.1"), "above"),
            config(BigDecimal("100.1"), "above"),
            config(BigDecimal("40"), "near"),
            config(BigDecimal("40"), "above") + ("unit" to MacroValue.Text("fahrenheit")),
        )
        invalid.forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-temperature-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 5) listOf("unknown_config_key")
                else listOf("invalid_battery_temperature_threshold"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(celsius: BigDecimal, comparison: String) =
        document(config(celsius, comparison))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("temperature-condition", "Temperature condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock("battery-temperature", "android.battery.temperature", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )

    private fun config(celsius: BigDecimal, comparison: String) = mapOf(
        "celsius" to MacroValue.Number(celsius),
        "comparison" to MacroValue.Text(comparison),
    )
}
