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

class BatteryVoltageTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesExactMillivoltCrossingsWithBoundedContext() {
        listOf(
            "below" to BatteryVoltageComparison.BELOW,
            "above" to BatteryVoltageComparison.ABOVE,
            "equals" to BatteryVoltageComparison.EQUALS,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(BigDecimal("4200"), source), "sha256:voltage-trigger-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveBatteryVoltage("voltage-crossing", 4200, expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("battery.voltage_millivolts"),
            registry.find("android.battery.voltage-crossing")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsFractionalOutOfRangeUnknownAndUnitConfiguration() {
        val invalid = listOf(
            config(BigDecimal("4000.5"), "below"),
            config(BigDecimal("20001"), "below"),
            config(BigDecimal("4000"), "near"),
            config(BigDecimal("4000"), "below") + ("unit" to MacroValue.Text("volts")),
        )
        invalid.forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-voltage-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key")
                else listOf("invalid_battery_voltage_trigger"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(millivolts: BigDecimal, comparison: String) =
        document(config(millivolts, comparison))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("voltage-trigger", "Voltage trigger"),
        triggers = listOf(
            MacroBlock("voltage-crossing", "android.battery.voltage-crossing", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )

    private fun config(millivolts: BigDecimal, comparison: String) = mapOf(
        "millivolts" to MacroValue.Number(millivolts),
        "comparison" to MacroValue.Text(comparison),
    )
}
