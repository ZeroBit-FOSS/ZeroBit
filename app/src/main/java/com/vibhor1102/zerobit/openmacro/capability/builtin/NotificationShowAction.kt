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
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.describeValueSource
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireTextSource
import com.vibhor1102.zerobit.openmacro.capability.validateTextSource
import com.vibhor1102.zerobit.openmacro.capability.valueSource
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object NotificationShowAction : CapabilityDefinition {
    override val type = "android.notification.show"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Show notification"
    override val description = "Displays a local Android notification."
    override val creation = CapabilityCreation(
        idBase = "show-notification",
        defaultConfig = mapOf(
            "title" to MacroValue.Text("ZeroBit"),
            "message" to MacroValue.Text("Automation ran"),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "title",
            label = "Title",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "The short heading shown in the notification.",
            acceptsValueSources = true,
        ),
        CapabilityField(
            key = "message",
            label = "Message",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "The notification text.",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("title", "message"), path))
            addAll(block.requireTextSource("title", path, maxLiteralLength = 120))
            addAll(block.requireTextSource("message", path, maxLiteralLength = 1_000))
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> = buildList {
        addAll(block.validateTextSource("title", path, document, registry))
        addAll(block.validateTextSource("message", path, document, registry))
    }

    override fun explain(block: MacroBlock): String =
        "Show a notification titled ${block.describeValueSource("title")} with message ${block.describeValueSource("message")}."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.POST_NOTIFICATIONS)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ShowNotification(
            blockId = block.id,
            title = block.valueSource("title"),
            message = block.valueSource("message"),
        )
}
