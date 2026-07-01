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

class BatteryStatusTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesEveryCanonicalStatusTransition() {
        listOf(
            "charging" to BatteryStatus.CHARGING,
            "full" to BatteryStatus.FULL,
            "discharging" to BatteryStatus.DISCHARGING,
            "not_charging" to BatteryStatus.NOT_CHARGING,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(mapOf("status" to MacroValue.Text(source))), "sha256:status-trigger-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveBatteryStatus("status-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("battery.status"),
            registry.find("android.battery.status-changed")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsUnknownNonTextAndRawStatusConfiguration() {
        listOf(
            mapOf("status" to MacroValue.Text("unknown")),
            mapOf("status" to MacroValue.Boolean(true)),
            mapOf(
                "status" to MacroValue.Text("charging"),
                "raw_status" to MacroValue.Number(java.math.BigDecimal("2")),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-status-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_battery_status_trigger"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("status-trigger", "Status trigger"),
        triggers = listOf(MacroBlock("status-changed", "android.battery.status-changed", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )
}
