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

object WriteLogAction : CapabilityDefinition {
    override val type = "android.log.write"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Write log"
    override val description = "Writes a message to the diagnostics and Android logcat."
    override val creation = CapabilityCreation(
        idBase = "write-log",
        defaultConfig = mapOf("message" to MacroValue.Text("New log entry")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "message",
            label = "Message",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "The text to output to logs.",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("message"), path))
            addAll(block.requireTextSource("message", path, maxLiteralLength = 1_000))
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        block.validateTextSource("message", path, document, registry)

    override fun explain(block: MacroBlock): String =
        "Write ${block.describeValueSource("message")} to the runtime log."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.WriteLog(
            blockId = block.id,
            message = block.valueSource("message"),
        )
}
