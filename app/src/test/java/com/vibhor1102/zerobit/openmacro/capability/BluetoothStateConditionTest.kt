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

class BluetoothStateConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExplicitBluetoothStatesWithConnectAccess() {
        listOf("enabled" to true, "disabled" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(mapOf("state" to MacroValue.Text(state))), "sha256:$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckBluetoothEnabled("bluetooth-state", expected),
                result.plan.conditions.single(),
            )
            assertEquals(
                setOf(AndroidPermission.BLUETOOTH_CONNECT),
                result.plan.requiredPermissions,
            )
        }
    }

    @Test
    fun rejectsMissingUnknownNonTextAndExtraConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("turning_on")),
            mapOf("state" to MacroValue.Boolean(true)),
            mapOf(
                "state" to MacroValue.Text("enabled"),
                "device" to MacroValue.Text("headphones"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-bluetooth-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key")
                else listOf("invalid_bluetooth_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("bluetooth-condition", "Bluetooth condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock("bluetooth-state", "android.bluetooth.state", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
