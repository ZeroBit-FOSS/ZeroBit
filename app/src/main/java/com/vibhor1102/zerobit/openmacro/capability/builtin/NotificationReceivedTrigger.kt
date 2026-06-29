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
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.optionalAndroidPackageName
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.NotificationField
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object NotificationReceivedTrigger : CapabilityDefinition {
    override val type = "android.notification.received"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Notification received"
    override val description =
        "Starts when Android posts a notification matching the optional app filter."
    override val creation = CapabilityCreation(
        idBase = "notification-received",
        setup = CapabilitySetup(
            fieldKeys = listOf("package", "capture"),
            initialConfig = mapOf(
                "capture" to MacroValue.ListValue(
                    listOf(MacroValue.Text("title")),
                ),
            ),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "package",
            label = "App package (optional)",
            kind = CapabilityFieldKind.TEXT,
            required = false,
            help = "Only accept notifications from this exact Android package.",
        ),
        CapabilityField(
            key = "capture",
            label = "Available fields",
            kind = CapabilityFieldKind.TEXT_LIST,
            required = true,
            help = "Choose only the notification fields this macro needs.",
            allowedValues = listOf("package", "title", "text"),
        ),
    )

    override fun triggerOutputs(block: MacroBlock): List<TriggerOutput> =
        block.captureFields().map { field ->
            TriggerOutput(
                key = field.contextKey,
                type = MacroVariableType.TEXT,
                description = when (field) {
                    NotificationField.PACKAGE -> "The posting app's package name."
                    NotificationField.TITLE -> "The notification title."
                    NotificationField.TEXT -> "The notification body text."
                },
            )
        }

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("package", "capture"), path))

            addAll(block.optionalAndroidPackageName("package", path))

            val capture = block.config["capture"]
            if (capture == null) {
                add(
                    ValidationIssue(
                        "$path.config.capture",
                        "missing_config",
                        "Configuration 'capture' is required.",
                    ),
                )
            } else if (capture !is MacroValue.ListValue) {
                add(
                    ValidationIssue(
                        "$path.config.capture",
                        "wrong_config_type",
                        "Configuration 'capture' must be a list.",
                    ),
                )
            } else {
                val names = capture.values.mapNotNull { (it as? MacroValue.Text)?.value }
                if (names.size != capture.values.size) {
                    add(
                        ValidationIssue(
                            "$path.config.capture",
                            "wrong_config_type",
                            "Every captured notification field must be text.",
                        ),
                    )
                }
                if (names.isEmpty()) {
                    add(
                        ValidationIssue(
                            "$path.config.capture",
                            "empty_capture",
                            "Choose at least one notification field.",
                        ),
                    )
                }
                val unknown = names.filterNot(ALLOWED_CAPTURE_NAMES::contains).distinct()
                unknown.forEach { name ->
                    add(
                        ValidationIssue(
                            "$path.config.capture",
                            "unknown_notification_field",
                            "Notification field '$name' is not supported.",
                        ),
                    )
                }
                if (names.distinct().size != names.size) {
                    add(
                        ValidationIssue(
                            "$path.config.capture",
                            "duplicate_notification_field",
                            "Each notification field may be captured only once.",
                        ),
                    )
                }
            }
        }

    override fun explain(block: MacroBlock): String {
        val packageName = (block.config["package"] as? MacroValue.Text)?.value
        val fields = block.captureFields().joinToString { it.name.lowercase() }
        return if (packageName == null) {
            "Start for any posted notification and expose only: $fields."
        } else {
            "Start for notifications from “$packageName” and expose only: $fields."
        }
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.NOTIFICATION_LISTENER_ACCESS)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveNotification(
            blockId = block.id,
            packageName = (block.config["package"] as? MacroValue.Text)?.value,
            capturedFields = block.captureFields().toSet(),
        )

    private val ALLOWED_CAPTURE_NAMES = setOf("package", "title", "text")
}

private fun MacroBlock.captureFields(): List<NotificationField> =
    ((config["capture"] as? MacroValue.ListValue)?.values ?: emptyList())
        .mapNotNull { (it as? MacroValue.Text)?.value }
        .mapNotNull { name ->
            when (name) {
                "package" -> NotificationField.PACKAGE
                "title" -> NotificationField.TITLE
                "text" -> NotificationField.TEXT
                else -> null
            }
        }
