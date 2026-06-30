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

class BluetoothStateTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesExplicitTransitionsWithBoundedContextAndConnectAccess() {
        listOf("enabled" to true, "disabled" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:bluetooth-trigger-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveBluetoothState("bluetooth-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(
                setOf(AndroidPermission.BLUETOOTH_CONNECT),
                result.plan.requiredPermissions,
            )
        }
        assertEquals(
            listOf("bluetooth.state"),
            registry.find("android.bluetooth.state-changed")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsMissingUnknownAndExtraConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("turning_off")),
            mapOf(
                "state" to MacroValue.Text("enabled"),
                "device" to MacroValue.Text("headphones"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-bluetooth-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_bluetooth_trigger_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(state: String) = document(mapOf("state" to MacroValue.Text(state)))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("bluetooth-trigger", "Bluetooth trigger"),
        triggers = listOf(
            MacroBlock("bluetooth-changed", "android.bluetooth.state-changed", config),
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
