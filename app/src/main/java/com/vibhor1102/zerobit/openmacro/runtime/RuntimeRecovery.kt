/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission

data class RuntimeRecoveryReport(
    val macros: List<MacroRecoveryStatus>,
) {
    val needsAttention: Boolean
        get() = macros.any { it !is MacroRecoveryStatus.Running }

    companion object {
        fun from(summary: RuntimeRestoreSummary): RuntimeRecoveryReport =
            RuntimeRecoveryReport(
                summary.resultsByMacroId.map { (macroId, result) ->
                    result.toRecoveryStatus(macroId)
                },
            )
    }
}

sealed interface MacroRecoveryStatus {
    val macroId: String
    val explanation: String

    data class Running(
        override val macroId: String,
        val revisionId: String,
    ) : MacroRecoveryStatus {
        override val explanation =
            "Restored approved revision $revisionId."
    }

    data class ApprovalRequired(
        override val macroId: String,
    ) : MacroRecoveryStatus {
        override val explanation =
            "This macro still wants to run, but it has no approved revision."
    }

    data class AccessRequired(
        override val macroId: String,
        val permissions: Set<AndroidPermission>,
    ) : MacroRecoveryStatus {
        override val explanation =
            "This macro still wants to run, but Android access is missing: " +
                permissions.sortedBy(AndroidPermission::name)
                    .joinToString { it.userFacingName() } + "."
    }

    data class Failed(
        override val macroId: String,
        override val explanation: String,
        val reason: EnableFailureReason,
    ) : MacroRecoveryStatus
}

private fun RuntimeLifecycleResult.toRecoveryStatus(
    macroId: String,
): MacroRecoveryStatus = when (this) {
    is RuntimeLifecycleResult.Enabled ->
        MacroRecoveryStatus.Running(macroId, revisionId)
    is RuntimeLifecycleResult.EnableFailed -> when {
        reason == EnableFailureReason.NO_APPROVED_SNAPSHOT ->
            MacroRecoveryStatus.ApprovalRequired(macroId)
        missingPermissions.isNotEmpty() ->
            MacroRecoveryStatus.AccessRequired(macroId, missingPermissions)
        else -> MacroRecoveryStatus.Failed(macroId, message, reason)
    }
    RuntimeLifecycleResult.Disabled,
    RuntimeLifecycleResult.AlreadyDisabled -> MacroRecoveryStatus.Failed(
        macroId,
        "The macro was not restored.",
        EnableFailureReason.GENERAL,
    )
}

fun AndroidPermission.userFacingName(): String = when (this) {
    AndroidPermission.BLUETOOTH_CONNECT -> "nearby devices permission"
    AndroidPermission.CAMERA -> "camera permission"
    AndroidPermission.POST_NOTIFICATIONS -> "notification permission"
    AndroidPermission.SEND_SMS -> "SMS permission"
    AndroidPermission.ACCESS_NETWORK_STATE -> "network-state access"
    AndroidPermission.NOTIFICATION_LISTENER_ACCESS -> "notification access"
    AndroidPermission.SCHEDULE_EXACT_ALARM_ACCESS -> "exact alarm access"
}
