/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
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

object ClipboardTextAction : CapabilityDefinition {
    override val type = "android.clipboard.set-text"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Copy text to clipboard"
    override val description = "Copies bounded text to Android's clipboard."
    override val creation = CapabilityCreation(
        idBase = "copy-to-clipboard",
        defaultConfig = mapOf("text" to MacroValue.Text("Copied by ZeroBit")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "text",
            label = "Text",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "Text to copy, up to 10000 characters.",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("text"), path))
            addAll(block.requireTextSource("text", path, maxLiteralLength = 10_000))
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        block.validateTextSource("text", path, document, registry)

    override fun explain(block: MacroBlock): String =
        "Copy ${block.describeValueSource("text")} to the clipboard."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CopyTextToClipboard(
            blockId = block.id,
            text = block.valueSource("text"),
        )
}
