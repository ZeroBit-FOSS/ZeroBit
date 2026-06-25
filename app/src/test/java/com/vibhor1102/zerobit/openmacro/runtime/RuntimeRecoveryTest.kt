/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeRecoveryTest {
    @Test
    fun turnsRestoreResultsIntoPlainEnglishAttentionStates() {
        val report = RuntimeRecoveryReport.from(
            RuntimeRestoreSummary(
                mapOf(
                    "running" to RuntimeLifecycleResult.Enabled("revision-1", 2),
                    "unapproved" to RuntimeLifecycleResult.EnableFailed(
                        message = "No approved snapshot is available.",
                        reason = EnableFailureReason.NO_APPROVED_SNAPSHOT,
                    ),
                    "access" to RuntimeLifecycleResult.EnableFailed(
                        message = "Missing permissions.",
                        missingPermissions = setOf(
                            AndroidPermission.NOTIFICATION_LISTENER_ACCESS,
                            AndroidPermission.SCHEDULE_EXACT_ALARM_ACCESS,
                        ),
                        reason = EnableFailureReason.MISSING_PERMISSIONS,
                    ),
                ),
            ),
        )

        assertTrue(report.needsAttention)
        assertTrue(report.macros[0] is MacroRecoveryStatus.Running)
        assertTrue(report.macros[1] is MacroRecoveryStatus.ApprovalRequired)
        val access = report.macros[2] as MacroRecoveryStatus.AccessRequired
        assertEquals(
            "This macro still wants to run, but Android access is missing: " +
                "notification access, exact alarm access.",
            access.explanation,
        )
    }
}
