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

class WifiConnectivityTriggersTest {
    @Test
    fun compilesBothTransitionsWithNetworkStateAccess() {
        val result = RuntimePlanCompiler(CapabilityRegistry.builtIn()).compile(
            document(),
            "sha256:wifi-transitions",
        )
        require(result is PlanCompilationResult.Success)
        assertEquals(
            listOf(
                RuntimeStep.ObserveWifiConnectivity("wifi-connected", true),
                RuntimeStep.ObserveWifiConnectivity("wifi-disconnected", false),
            ),
            result.plan.triggers,
        )
        assertEquals(
            setOf(AndroidPermission.ACCESS_NETWORK_STATE),
            result.plan.requiredPermissions,
        )
    }

    private fun document() = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("wifi-events", "Wi-Fi events"),
        triggers = listOf(
            MacroBlock("wifi-connected", "android.wifi.connected-trigger"),
            MacroBlock("wifi-disconnected", "android.wifi.disconnected-trigger"),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("Wi-Fi changed")),
            ),
        ),
    )
}
