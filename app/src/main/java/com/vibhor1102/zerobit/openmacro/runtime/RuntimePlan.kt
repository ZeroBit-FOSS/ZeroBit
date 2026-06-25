/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.MacroVariable

/**
 * Immutable, already-approved instructions consumed by the future runtime.
 * The runtime does not parse source files or interpret capability config.
 */
data class RuntimePlan(
    val macroId: String,
    val sourceFingerprint: String,
    val variables: List<MacroVariable> = emptyList(),
    val triggers: List<RuntimeStep>,
    val conditions: List<RuntimeStep>,
    val actions: List<RuntimeStep>,
    val requiredPermissions: Set<AndroidPermission>,
)

sealed interface RuntimeStep {
    val blockId: String

    data class ObservePowerConnected(
        override val blockId: String,
    ) : RuntimeStep

    data class ObserveScreenOn(
        override val blockId: String,
    ) : RuntimeStep

    data class ObserveScreenOff(
        override val blockId: String,
    ) : RuntimeStep

    data class ObserveBatteryLevel(
        override val blockId: String,
        val level: Int,
        val direction: BatteryDirection,
    ) : RuntimeStep

    data class CheckDeviceUnlocked(
        override val blockId: String,
    ) : RuntimeStep

    data class CheckWifiConnected(
        override val blockId: String,
        val ssid: String?,
    ) : RuntimeStep

    data class ShowNotification(
        override val blockId: String,
        val title: String,
        val message: String,
    ) : RuntimeStep

    data class WriteLog(
        override val blockId: String,
        val message: String,
    ) : RuntimeStep

    data class SendSms(
        override val blockId: String,
        val phoneNumber: String,
        val message: String,
    ) : RuntimeStep
}

enum class BatteryDirection {
    GOES_BELOW,
    GOES_ABOVE,
    EQUALS
}

