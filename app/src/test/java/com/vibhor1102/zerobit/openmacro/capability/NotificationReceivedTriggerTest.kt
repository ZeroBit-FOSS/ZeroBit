/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.NotificationField
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReceivedTriggerTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesOnlyRequestedNotificationContext() {
        val document = document(
            capture = listOf("package", "title"),
            actionField = "notification.title",
        )

        val result = compiler.compile(document, "sha256:notification")

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.ObserveNotification(
                blockId = "notification",
                packageName = "com.example.chat",
                capturedFields = setOf(NotificationField.PACKAGE, NotificationField.TITLE),
            ),
            result.plan.triggers.single(),
        )
        assertEquals(
            RuntimeValueSource.Trigger("notification.title"),
            (result.plan.actions.single() as RuntimeStep.WriteLog).message,
        )
        assertEquals(
            setOf(AndroidPermission.NOTIFICATION_LISTENER_ACCESS),
            result.plan.requiredPermissions,
        )
    }

    @Test
    fun rejectsReferenceToNotificationFieldThatWasNotRequested() {
        val result = compiler.compile(
            document(
                capture = listOf("title"),
                actionField = "notification.text",
            ),
            "sha256:missing-notification-field",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf("unknown_trigger_field"),
            result.issues.map { it.code },
        )
    }

    @Test
    fun rejectsUnknownAndDuplicateCaptureFields() {
        val result = compiler.compile(
            document(
                capture = listOf("title", "title", "everything"),
                actionField = "notification.title",
            ),
            "sha256:bad-capture",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf("unknown_notification_field", "duplicate_notification_field"),
            result.issues.map { it.code },
        )
    }

    @Test
    fun rejectsNonPackageNotificationFilter() {
        val document = document(
            capture = listOf("title"),
            actionField = "notification.title",
        ).copy(
            triggers = listOf(
                MacroBlock(
                    id = "notification",
                    type = "android.notification.received",
                    config = mapOf(
                        "package" to MacroValue.Text("not a package"),
                        "capture" to MacroValue.ListValue(
                            listOf(MacroValue.Text("title")),
                        ),
                    ),
                ),
            ),
        )

        val result = compiler.compile(document, "sha256:invalid-package")

        require(result is PlanCompilationResult.Invalid)
        assertTrue(result.issues.any { it.code == "invalid_package_name" })
    }

    private fun document(
        capture: List<String>,
        actionField: String,
    ) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("notification-macro", "Notification macro"),
        triggers = listOf(
            MacroBlock(
                id = "notification",
                type = "android.notification.received",
                config = mapOf(
                    "package" to MacroValue.Text("com.example.chat"),
                    "capture" to MacroValue.ListValue(
                        capture.map { MacroValue.Text(it) },
                    ),
                ),
            ),
        ),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "log-notification",
                type = "android.log.write",
                config = mapOf(
                    "message" to MacroValue.ObjectValue(
                        mapOf("trigger" to MacroValue.Text(actionField)),
                    ),
                ),
            ),
        ),
    )
}
