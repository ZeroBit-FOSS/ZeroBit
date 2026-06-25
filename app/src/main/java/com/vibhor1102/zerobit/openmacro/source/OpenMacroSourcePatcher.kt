/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.schema.JsonSchema

/**
 * Applies narrow visual-form edits without reformatting the surrounding YAML.
 *
 * Replacements use compact flow-style YAML for collection values so the patch
 * remains local to one value range. Key insertion and removal preserve the
 * surrounding mapping and comments outside the changed entry.
 */
object OpenMacroSourcePatcher {
    private val settings = LoadSettings.builder()
        .setLabel("OpenMacro source patch")
        .setSchema(JsonSchema())
        .setUseMarks(true)
        .build()

    fun replaceScalarConfig(
        sourceText: String,
        blockId: String,
        key: String,
        value: MacroValue,
    ): SourcePatchResult = setConfig(sourceText, blockId, key, value)

    fun replaceMetadataText(
        sourceText: String,
        key: String,
        value: String,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val metadata = root.valueFor("metadata") as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro metadata is missing.")
        val target = metadata.valueFor(key)
            ?: return SourcePatchResult.Unsupported("Metadata '$key' is missing.")
        if (target !is ScalarNode || target.tag != Tag.STR) {
            return SourcePatchResult.Unsupported("Metadata '$key' is not text.")
        }
        val start = target.startMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Metadata '$key' has no source location.")
        val end = target.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Metadata '$key' has no source location.")
        return SourcePatchResult.Success(
            sourceText.replaceRange(start, end, MacroValue.Text(value).asYamlValue()),
        )
    }

    fun setConfig(
        sourceText: String,
        blockId: String,
        key: String,
        value: MacroValue?,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val block = root.findBlock(blockId)
            ?: return SourcePatchResult.NotFound(blockId)
        val config = block.valueFor("config") as? MappingNode
        if (config == null) {
            if (value == null) {
                return SourcePatchResult.Success(sourceText)
            }
            return insertConfigObject(sourceText, block, key, value)
        }
        val tuple = config.tupleFor(key)
        if (tuple == null) {
            if (value == null) {
                return SourcePatchResult.Success(sourceText)
            }
            return insertConfigEntry(sourceText, config, key, value)
        }
        if (value == null) {
            return removeConfigEntry(sourceText, tuple.keyNode, tuple.valueNode)
        }
        val start = tuple.valueNode.startMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Target value has no source location.")
        val end = tuple.valueNode.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Target value has no source location.")
        return SourcePatchResult.Success(
            sourceText.replaceRange(start, end, value.asYamlValue()),
        )
    }

    fun setVariableField(
        sourceText: String,
        variableName: String,
        key: String,
        value: MacroValue?,
    ): SourcePatchResult {
        if (key !in setOf("initial", "secret_key")) {
            return SourcePatchResult.Unsupported(
                "Variable field '$key' is not editable with a local patch.",
            )
        }
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val variables = root.valueFor("variables") as? SequenceNode
            ?: return SourcePatchResult.NotFound(variableName)
        val variable = variables.value
            .filterIsInstance<MappingNode>()
            .find { it.textValue("name") == variableName }
            ?: return SourcePatchResult.NotFound(variableName)
        val tuple = variable.tupleFor(key)
        if (tuple == null) {
            if (value == null) {
                return SourcePatchResult.Success(sourceText)
            }
            return insertConfigEntry(sourceText, variable, key, value)
        }
        if (value == null) {
            return removeConfigEntry(sourceText, tuple.keyNode, tuple.valueNode)
        }
        val start = tuple.valueNode.startMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Variable field has no source location.")
        val end = tuple.valueNode.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Variable field has no source location.")
        return SourcePatchResult.Success(
            sourceText.replaceRange(start, end, value.asYamlValue()),
        )
    }

    private fun insertConfigObject(
        sourceText: String,
        block: MappingNode,
        key: String,
        value: MacroValue,
    ): SourcePatchResult {
        val typeTuple = block.tupleFor("type")
            ?: return SourcePatchResult.Unsupported("Block has no type entry.")
        val insertion = lineEndAfter(sourceText, typeTuple.valueNode)
        val typeColumn = typeTuple.keyNode.startMark.orElse(null)?.column
            ?: return SourcePatchResult.Unsupported("Block type has no source column.")
        val indent = " ".repeat(typeColumn)
        val configIndent = "$indent  "
        val prefix = if (
            insertion > 0 &&
            sourceText[insertion - 1] != '\n' &&
            sourceText[insertion - 1] != '\r'
        ) {
            "\n"
        } else {
            ""
        }
        val text = buildString {
            append(prefix)
            append(indent)
            append("config:\n")
            append(configIndent)
            append(quote(key))
            append(": ")
            append(value.asYamlValue())
            append("\n")
        }
        return SourcePatchResult.Success(
            sourceText.replaceRange(insertion, insertion, text),
        )
    }

    private fun insertConfigEntry(
        sourceText: String,
        config: MappingNode,
        key: String,
        value: MacroValue,
    ): SourcePatchResult {
        val insertion = config.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Config object has no source location.")
        val column = config.value.firstOrNull()
            ?.keyNode
            ?.startMark
            ?.orElse(null)
            ?.column
            ?: ((config.startMark.orElse(null)?.column ?: 0) + 2)
        val prefix = if (
            insertion > 0 &&
            sourceText[insertion - 1] != '\n' &&
            sourceText[insertion - 1] != '\r'
        ) {
            "\n"
        } else {
            ""
        }
        val text = "$prefix${" ".repeat(column)}${quote(key)}: ${value.asYamlValue()}\n"
        return SourcePatchResult.Success(
            sourceText.replaceRange(insertion, insertion, text),
        )
    }

    private fun removeConfigEntry(
        sourceText: String,
        keyNode: Node,
        valueNode: Node,
    ): SourcePatchResult {
        val keyStart = keyNode.startMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Config key has no source location.")
        val valueEnd = valueNode.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Config value has no source location.")
        val start = lineStartBefore(sourceText, keyStart)
        val end = lineEndAfter(sourceText, valueEnd)
        return SourcePatchResult.Success(sourceText.removeRange(start, end))
    }

    private fun MappingNode.findBlock(blockId: String): MappingNode? {
        listOf("triggers", "conditions", "actions").forEach { lane ->
            val blocks = valueFor(lane) as? SequenceNode
            blocks?.value?.forEach { node ->
                val block = node as? MappingNode ?: return@forEach
                if (block.textValue("id") == blockId) {
                    return block
                }
            }
        }
        return (valueFor("condition_tree") as? MappingNode)?.findConditionBlock(blockId)
    }

    private fun MappingNode.findConditionBlock(blockId: String): MappingNode? {
        (valueFor("condition") as? MappingNode)?.let { block ->
            if (block.textValue("id") == blockId) {
                return block
            }
        }
        listOf("all", "any").forEach { group ->
            val children = valueFor(group) as? SequenceNode
            children?.value?.forEach { child ->
                val found = (child as? MappingNode)?.findConditionBlock(blockId)
                if (found != null) {
                    return found
                }
            }
        }
        return (valueFor("not") as? MappingNode)?.findConditionBlock(blockId)
    }

    private fun MappingNode.valueFor(key: String): Node? =
        tupleFor(key)?.valueNode

    private fun MappingNode.tupleFor(key: String) =
        value.firstOrNull { tuple ->
            (tuple.keyNode as? ScalarNode)?.value == key
        }

    private fun MappingNode.textValue(key: String): String? =
        (valueFor(key) as? ScalarNode)
            ?.takeIf { it.tag == Tag.STR }
            ?.value

    private fun MacroValue.asYamlValue(): String = when (this) {
        is MacroValue.Text -> quote(value)
        is MacroValue.Number -> value.toPlainString()
        is MacroValue.Boolean -> value.toString()
        MacroValue.Null -> "null"
        is MacroValue.ListValue -> values.joinToString(
            prefix = "[",
            postfix = "]",
        ) { it.asYamlValue() }
        is MacroValue.ObjectValue -> values.toSortedMap().entries.joinToString(
            prefix = "{",
            postfix = "}",
        ) { (key, value) -> "${quote(key)}: ${value.asYamlValue()}" }
    }

    private fun quote(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
            append('"')
        }

    private fun lineStartBefore(source: String, index: Int): Int {
        val newline = source.lastIndexOf('\n', startIndex = (index - 1).coerceAtLeast(0))
        return if (newline < 0) 0 else newline + 1
    }

    private fun lineEndAfter(source: String, node: Node): Int {
        val index = node.endMark.orElse(null)?.index ?: return source.length
        return lineEndAfter(source, index)
    }

    private fun lineEndAfter(source: String, index: Int): Int {
        val newline = source.indexOf('\n', startIndex = index)
        return if (newline < 0) source.length else newline + 1
    }
}

sealed interface SourcePatchResult {
    data class Success(val sourceText: String) : SourcePatchResult

    data class NotFound(val blockId: String) : SourcePatchResult

    data class Unsupported(val message: String) : SourcePatchResult

    data class InvalidSource(val issues: List<SourceIssue>) : SourcePatchResult
}
