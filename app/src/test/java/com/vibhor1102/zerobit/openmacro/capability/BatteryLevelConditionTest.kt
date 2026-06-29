/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.BatteryDirection
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryLevelConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesEachBatteryComparison() {
        listOf(
            "goes_below" to BatteryDirection.GOES_BELOW,
            "goes_above" to BatteryDirection.GOES_ABOVE,
            "equals" to BatteryDirection.EQUALS,
        ).forEach { (direction, expected) ->
            val result = compiler.compile(document(BigDecimal("50"), direction), "sha256:$direction")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckBatteryLevel("battery-level-condition", 50, expected),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsMissingFractionalOutOfRangeOrUnknownThresholds() {
        listOf(
            null to "goes_below",
            BigDecimal("50.5") to "goes_below",
            BigDecimal.ZERO to "goes_below",
            BigDecimal("101") to "goes_below",
            BigDecimal("50") to "near",
        ).forEachIndexed { index, (level, direction) ->
            val result = compiler.compile(document(level, direction), "sha256:invalid-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_battery_threshold"), result.issues.map { it.code })
        }
    }

    @Test
    fun batteryTriggerAlsoRejectsFractionalThresholds() {
        val result = compiler.compile(
            document(BigDecimal("50"), "equals").copy(
                triggers = listOf(
                    MacroBlock(
                        id = "battery-trigger",
                        type = "android.battery.level",
                        config = thresholdConfig(BigDecimal("50.5"), "equals"),
                    ),
                ),
            ),
            "sha256:fractional-trigger",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("invalid_battery_threshold"), result.issues.map { it.code })
    }

    private fun document(level: BigDecimal?, direction: String) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("battery-level", "Battery level"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock(
                id = "battery-level-condition",
                type = "android.battery.level-condition",
                config = thresholdConfig(level, direction),
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

    private fun thresholdConfig(level: BigDecimal?, direction: String) = buildMap {
        if (level != null) put("level", MacroValue.Number(level))
        put("direction", MacroValue.Text(direction))
    }
}
