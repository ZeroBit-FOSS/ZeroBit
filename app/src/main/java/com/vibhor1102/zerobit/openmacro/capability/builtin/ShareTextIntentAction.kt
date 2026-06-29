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
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.describeValueSource
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireAndroidPackageName
import com.vibhor1102.zerobit.openmacro.capability.requireTextSource
import com.vibhor1102.zerobit.openmacro.capability.text
import com.vibhor1102.zerobit.openmacro.capability.validateTextSource
import com.vibhor1102.zerobit.openmacro.capability.valueSource
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object ShareTextIntentAction : CapabilityDefinition {
    override val type = "android.intent.share-text"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Share text with app"
    override val description =
        "Shares text with one exact Android package using the standard share intent."
    override val creation = CapabilityCreation(
        idBase = "share-text",
        setup = CapabilitySetup(fieldKeys = listOf("package", "text")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "package",
            label = "App package",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Exact package name, for example com.example.app.",
        ),
        CapabilityField(
            key = "text",
            label = "Text",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "The text to share with the target app.",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("package", "text"), path))
            addAll(block.requireAndroidPackageName("package", path))
            addAll(block.requireTextSource("text", path, maxLiteralLength = 4_000))
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        block.validateTextSource("text", path, document, registry)

    override fun explain(block: MacroBlock): String =
        "Share ${block.describeValueSource("text")} with package ${
            runCatching { block.text("package") }.getOrNull() ?: "not set"
        }."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ShareTextIntent(
            blockId = block.id,
            packageName = block.text("package"),
            text = block.valueSource("text"),
        )
}
