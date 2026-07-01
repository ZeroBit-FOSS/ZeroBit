/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.BatteryVoltageComparison
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryVoltageConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExactMillivoltsForEveryComparison() {
        listOf(
            "below" to BatteryVoltageComparison.BELOW,
            "above" to BatteryVoltageComparison.ABOVE,
            "equals" to BatteryVoltageComparison.EQUALS,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(BigDecimal("4200"), source), "sha256:voltage-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckBatteryVoltage("battery-voltage", 4200, expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun acceptsInclusiveMillivoltBounds() {
        listOf(BigDecimal.ZERO, BigDecimal("20000")).forEachIndexed { index, value ->
            val result = compiler.compile(document(value, "equals"), "sha256:voltage-bound-$index")
            require(result is PlanCompilationResult.Success)
        }
    }

    @Test
    fun rejectsMissingFractionalOutOfRangeUnknownAndUnitConfiguration() {
        val invalid = listOf(
            emptyMap(),
            config(BigDecimal("4000.5"), "below"),
            config(BigDecimal("-1"), "below"),
            config(BigDecimal("20001"), "below"),
            config(BigDecimal("4000"), "near"),
            config(BigDecimal("4000"), "below") + ("unit" to MacroValue.Text("volts")),
        )
        invalid.forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-voltage-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 5) listOf("unknown_config_key")
                else listOf("invalid_battery_voltage_threshold"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(millivolts: BigDecimal, comparison: String) =
        document(config(millivolts, comparison))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("voltage-condition", "Voltage condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(MacroBlock("battery-voltage", "android.battery.voltage", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )

    private fun config(millivolts: BigDecimal, comparison: String) = mapOf(
        "millivolts" to MacroValue.Number(millivolts),
        "comparison" to MacroValue.Text(comparison),
    )
}
