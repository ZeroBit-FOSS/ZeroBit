/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.BatteryStatus
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryStatusConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesEveryExactBatteryStatusWithoutAccess() {
        listOf(
            "charging" to BatteryStatus.CHARGING,
            "full" to BatteryStatus.FULL,
            "discharging" to BatteryStatus.DISCHARGING,
            "not_charging" to BatteryStatus.NOT_CHARGING,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(mapOf("status" to MacroValue.Text(source))), "sha256:status-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckBatteryStatus("battery-status", expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingUnknownNonTextAndRawStatusConfiguration() {
        listOf(
            emptyMap(),
            mapOf("status" to MacroValue.Text("unknown")),
            mapOf("status" to MacroValue.Boolean(true)),
            mapOf(
                "status" to MacroValue.Text("charging"),
                "raw_status" to MacroValue.Number(java.math.BigDecimal("2")),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-status-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key") else listOf("invalid_battery_status"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("status-condition", "Status condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(MacroBlock("battery-status", "android.battery.status", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
