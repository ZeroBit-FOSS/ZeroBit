/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

abstract class WifiConnectivityTrigger(
    private val connected: Boolean,
) : CapabilityDefinition {
    override val lane = CapabilityLane.TRIGGER
    override val fields: List<CapabilityField> = emptyList()
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "wifi.state",
            type = MacroVariableType.TEXT,
            description = "The Wi-Fi connectivity state that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        block.rejectUnknownConfig(emptySet(), path)

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.ACCESS_NETWORK_STATE)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveWifiConnectivity(block.id, connected)
}

object WifiConnectedTrigger : WifiConnectivityTrigger(connected = true) {
    override val type = "android.wifi.connected-trigger"
    override val displayName = "Wi-Fi connected"
    override val description = "Starts when the default network changes to Wi-Fi."
    override val creation = CapabilityCreation("wifi-connected-trigger")

    override fun explain(block: MacroBlock): String =
        "Start when the phone connects to Wi-Fi."
}

object WifiDisconnectedTrigger : WifiConnectivityTrigger(connected = false) {
    override val type = "android.wifi.disconnected-trigger"
    override val displayName = "Wi-Fi disconnected"
    override val description = "Starts when the default network stops using Wi-Fi."
    override val creation = CapabilityCreation("wifi-disconnected-trigger")

    override fun explain(block: MacroBlock): String =
        "Start when the phone disconnects from Wi-Fi."
}
