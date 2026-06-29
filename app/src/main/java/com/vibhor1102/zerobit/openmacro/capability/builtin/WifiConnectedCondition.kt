/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object WifiConnectedCondition : CapabilityDefinition {
    override val type = "android.wifi.connected"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Wi-Fi connected"
    override val description = "Continues only when the device is connected to a Wi-Fi network."
    override val creation = CapabilityCreation("wifi-connected")
    override val fields = listOf(
        CapabilityField(
            key = "ssid",
            label = "SSID (Optional)",
            kind = CapabilityFieldKind.TEXT,
            required = false,
            help = "Match a specific network name.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("ssid"), path))

            val ssidVal = block.config["ssid"]
            if (ssidVal != null && ssidVal !is MacroValue.Text) {
                add(ValidationIssue("$path.config.ssid", "wrong_config_type", "Configuration 'ssid' must be text."))
            }
        }

    override fun explain(block: MacroBlock): String {
        val ssid = (block.config["ssid"] as? MacroValue.Text)?.value
        return if (ssid.isNullOrBlank()) {
            "Continue only if connected to Wi-Fi."
        } else {
            "Continue only if connected to Wi-Fi network “$ssid”."
        }
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.ACCESS_NETWORK_STATE)

    override fun compile(block: MacroBlock): RuntimeStep {
        val ssid = (block.config["ssid"] as? MacroValue.Text)?.value
        return RuntimeStep.CheckWifiConnected(blockId = block.id, ssid = ssid)
    }
}
