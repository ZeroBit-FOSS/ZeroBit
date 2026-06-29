/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.model.ConditionGroupLogic
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
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

    fun addTopLevelBlock(
        sourceText: String,
        lane: CapabilityLane,
        block: MacroBlock,
    ): SourcePatchResult {
        val root = validatedRoot(sourceText) ?: return invalidRoot(sourceText)
        val sequence = root.valueFor(lane.sourceKey) as? SequenceNode
            ?: return SourcePatchResult.NotFound(lane.sourceKey)
        if (sequence.value.isEmpty()) {
            val start = sequence.startMark.orElse(null)?.index
                ?: return SourcePatchResult.Unsupported("${lane.sourceKey} has no source location.")
            val end = sequence.endMark.orElse(null)?.index
                ?: return SourcePatchResult.Unsupported("${lane.sourceKey} has no source location.")
            return SourcePatchResult.Success(
                sourceText.replaceRange(start, end, "\n${block.asTopLevelYaml()}"),
            )
        }
        if (sequence.value.any { !it.isBlockSequenceItem(sourceText) }) {
            return SourcePatchResult.Unsupported(
                "Flow-style ${lane.sourceKey} cannot be edited visually.",
            )
        }
        val insertion = lineEndAfter(sourceText, sequence.value.last())
        val prefix = if (
            insertion > 0 &&
            sourceText[insertion - 1] != '\n' &&
            sourceText[insertion - 1] != '\r'
        ) {
            "\n"
        } else {
            ""
        }
        return SourcePatchResult.Success(
            sourceText.replaceRange(insertion, insertion, "$prefix${block.asTopLevelYaml()}"),
        )
    }

    fun removeTopLevelBlock(
        sourceText: String,
        lane: CapabilityLane,
        blockId: String,
    ): SourcePatchResult {
        val root = validatedRoot(sourceText) ?: return invalidRoot(sourceText)
        val tuple = root.tupleFor(lane.sourceKey)
            ?: return SourcePatchResult.NotFound(lane.sourceKey)
        val sequence = tuple.valueNode as? SequenceNode
            ?: return SourcePatchResult.Unsupported("${lane.sourceKey} is not a list.")
        val index = sequence.value.indexOfFirst { node ->
            (node as? MappingNode)?.textValue("id") == blockId
        }
        if (index < 0) {
            return SourcePatchResult.NotFound(blockId)
        }
        if (sequence.value.any { !it.isBlockSequenceItem(sourceText) }) {
            return SourcePatchResult.Unsupported(
                "Flow-style ${lane.sourceKey} cannot be edited visually.",
            )
        }
        if (sequence.value.size == 1) {
            val start = tuple.keyNode.endMark.orElse(null)?.index
                ?: return SourcePatchResult.Unsupported("${lane.sourceKey} has no source location.")
            val end = sequence.endMark.orElse(null)?.index
                ?: return SourcePatchResult.Unsupported("${lane.sourceKey} has no source location.")
            return SourcePatchResult.Success(
                sourceText.replaceRange(start, end, ": []"),
            )
        }
        val node = sequence.value[index]
        val start = node.startMark.orElse(null)?.index
            ?.let { lineStartBefore(sourceText, it) }
            ?: return SourcePatchResult.Unsupported("Block '$blockId' has no source location.")
        val end = lineEndAfter(sourceText, node)
        return SourcePatchResult.Success(sourceText.removeRange(start, end))
    }

    fun moveTopLevelBlock(
        sourceText: String,
        lane: CapabilityLane,
        blockId: String,
        offset: Int,
    ): SourcePatchResult {
        if (offset !in setOf(-1, 1)) {
            return SourcePatchResult.Unsupported("Top-level move offset must be -1 or 1.")
        }
        val root = validatedRoot(sourceText) ?: return invalidRoot(sourceText)
        val sequence = root.valueFor(lane.sourceKey) as? SequenceNode
            ?: return SourcePatchResult.NotFound(lane.sourceKey)
        val index = sequence.value.indexOfFirst { node ->
            (node as? MappingNode)?.textValue("id") == blockId
        }
        if (index < 0) {
            return SourcePatchResult.NotFound(blockId)
        }
        if (sequence.value.any { !it.isBlockSequenceItem(sourceText) }) {
            return SourcePatchResult.Unsupported(
                "Flow-style ${lane.sourceKey} cannot be edited visually.",
            )
        }
        val targetIndex = index + offset
        if (targetIndex !in sequence.value.indices) {
            return SourcePatchResult.Unsupported("Block is already at that edge.")
        }
        val firstIndex = minOf(index, targetIndex)
        val secondIndex = maxOf(index, targetIndex)
        val first = sequence.value[firstIndex]
        val second = sequence.value[secondIndex]
        val firstStart = first.startMark.orElse(null)?.index
            ?.let { lineStartBefore(sourceText, it) }
            ?: return SourcePatchResult.Unsupported("Block '$blockId' has no source location.")
        val firstEnd = lineEndAfter(sourceText, first)
        val secondStart = second.startMark.orElse(null)?.index
            ?.let { lineStartBefore(sourceText, it) }
            ?: return SourcePatchResult.Unsupported("Block '$blockId' has no source location.")
        val secondEnd = lineEndAfter(sourceText, second)
        val firstText = sourceText.substring(firstStart, firstEnd)
        val between = sourceText.substring(firstEnd, secondStart)
        val secondText = sourceText.substring(secondStart, secondEnd)
        return SourcePatchResult.Success(
            sourceText.replaceRange(
                firstStart,
                secondEnd,
                secondText + between + firstText,
            ),
        )
    }

    fun addGroupedAction(
        sourceText: String,
        groupBlockId: String,
        child: MacroBlock,
    ): SourcePatchResult {
        val root = validatedRoot(sourceText) ?: return invalidRoot(sourceText)
        val group = root.findBlock(groupBlockId)
            ?: return SourcePatchResult.NotFound(groupBlockId)
        if (group.textValue("type") != "openmacro.action.group") {
            return SourcePatchResult.Unsupported("Block '$groupBlockId' is not an action group.")
        }
        val actions = ((group.valueFor("config") as? MappingNode)?.valueFor("actions"))
            as? SequenceNode
            ?: return SourcePatchResult.Unsupported("Action group '$groupBlockId' has no actions list.")
        if (actions.value.isEmpty()) {
            return SourcePatchResult.Unsupported("Action groups must keep at least one child action.")
        }
        if (actions.value.any { !it.isBlockSequenceItem(sourceText) }) {
            return SourcePatchResult.Unsupported(
                "Flow-style grouped actions cannot be edited visually.",
            )
        }
        val indent = actions.value.first().sequenceItemIndent(sourceText)
            ?: return SourcePatchResult.Unsupported("Grouped action has no source indentation.")
        val insertion = lineEndAfter(sourceText, actions.value.last())
        val prefix = if (
            insertion > 0 &&
            sourceText[insertion - 1] != '\n' &&
            sourceText[insertion - 1] != '\r'
        ) {
            "\n"
        } else {
            ""
        }
        return SourcePatchResult.Success(
            sourceText.replaceRange(
                insertion,
                insertion,
                "$prefix${child.asSequenceItemYaml(indent)}",
            ),
        )
    }

    fun removeGroupedAction(
        sourceText: String,
        childBlockId: String,
    ): SourcePatchResult {
        val root = validatedRoot(sourceText) ?: return invalidRoot(sourceText)
        val actions = root.findGroupedActionSequenceContaining(childBlockId)
            ?: return SourcePatchResult.NotFound(childBlockId)
        if (actions.value.size <= 1) {
            return SourcePatchResult.Unsupported(
                "Action groups must keep at least one child action.",
            )
        }
        if (actions.value.any { !it.isBlockSequenceItem(sourceText) }) {
            return SourcePatchResult.Unsupported(
                "Flow-style grouped actions cannot be edited visually.",
            )
        }
        val child = actions.value.first { node ->
            (node as? MappingNode)?.textValue("id") == childBlockId
        }
        val start = child.startMark.orElse(null)?.index
            ?.let { lineStartBefore(sourceText, it) }
            ?: return SourcePatchResult.Unsupported(
                "Grouped action '$childBlockId' has no source location.",
            )
        return SourcePatchResult.Success(
            sourceText.removeRange(start, lineEndAfter(sourceText, child)),
        )
    }

    fun moveGroupedAction(
        sourceText: String,
        childBlockId: String,
        offset: Int,
    ): SourcePatchResult {
        if (offset !in setOf(-1, 1)) {
            return SourcePatchResult.Unsupported("Grouped action move offset must be -1 or 1.")
        }
        val root = validatedRoot(sourceText) ?: return invalidRoot(sourceText)
        val actions = root.findGroupedActionSequenceContaining(childBlockId)
            ?: return SourcePatchResult.NotFound(childBlockId)
        if (actions.value.any { !it.isBlockSequenceItem(sourceText) }) {
            return SourcePatchResult.Unsupported(
                "Flow-style grouped actions cannot be edited visually.",
            )
        }
        val index = actions.value.indexOfFirst { node ->
            (node as? MappingNode)?.textValue("id") == childBlockId
        }
        val targetIndex = index + offset
        if (targetIndex !in actions.value.indices) {
            return SourcePatchResult.Unsupported("Grouped action is already at that edge.")
        }
        val first = actions.value[minOf(index, targetIndex)]
        val second = actions.value[maxOf(index, targetIndex)]
        val firstStart = first.startMark.orElse(null)?.index
            ?.let { lineStartBefore(sourceText, it) }
            ?: return SourcePatchResult.Unsupported(
                "Grouped action '$childBlockId' has no source location.",
            )
        val firstEnd = lineEndAfter(sourceText, first)
        val secondStart = second.startMark.orElse(null)?.index
            ?.let { lineStartBefore(sourceText, it) }
            ?: return SourcePatchResult.Unsupported(
                "Grouped action '$childBlockId' has no source location.",
            )
        val secondEnd = lineEndAfter(sourceText, second)
        return SourcePatchResult.Success(
            sourceText.replaceRange(
                firstStart,
                secondEnd,
                sourceText.substring(secondStart, secondEnd) +
                    sourceText.substring(firstEnd, secondStart) +
                    sourceText.substring(firstStart, firstEnd),
            ),
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

    fun addVariableDeclaration(
        sourceText: String,
        variable: MacroVariable,
    ): SourcePatchResult {
        if (variable.type == MacroVariableType.SECRET && variable.secretKey.isNullOrBlank()) {
            return SourcePatchResult.Unsupported("Secret variable requires a secret key.")
        }
        val root = validatedRoot(sourceText) ?: return invalidRoot(sourceText)
        val variablesTuple = root.tupleFor("variables")
        if (variablesTuple == null) {
            val triggers = root.tupleFor("triggers")
                ?: return SourcePatchResult.Unsupported("OpenMacro triggers section is missing.")
            val insertion = triggers.keyNode.startMark.orElse(null)?.index
                ?.let { lineStartBefore(sourceText, it) }
                ?: return SourcePatchResult.Unsupported("Triggers section has no source location.")
            return SourcePatchResult.Success(
                sourceText.replaceRange(
                    insertion,
                    insertion,
                    "variables:\n${variable.asVariableYaml()}\n",
                ),
            )
        }
        val variables = variablesTuple.valueNode as? SequenceNode
            ?: return SourcePatchResult.Unsupported("OpenMacro variables is not a list.")
        if (variables.value.isEmpty()) {
            val start = variables.startMark.orElse(null)?.index
                ?: return SourcePatchResult.Unsupported("Variables section has no source location.")
            val end = variables.endMark.orElse(null)?.index
                ?: return SourcePatchResult.Unsupported("Variables section has no source location.")
            return SourcePatchResult.Success(
                sourceText.replaceRange(start, end, "\n${variable.asVariableYaml()}"),
            )
        }
        if (variables.value.any { !it.isBlockSequenceItem(sourceText) }) {
            return SourcePatchResult.Unsupported(
                "Flow-style variables cannot be edited visually.",
            )
        }
        val insertion = lineEndAfter(sourceText, variables.value.last())
        val prefix = if (
            insertion > 0 &&
            sourceText[insertion - 1] != '\n' &&
            sourceText[insertion - 1] != '\r'
        ) {
            "\n"
        } else {
            ""
        }
        return SourcePatchResult.Success(
            sourceText.replaceRange(
                insertion,
                insertion,
                "$prefix${variable.asVariableYaml()}",
            ),
        )
    }

    fun removeVariableDeclaration(
        sourceText: String,
        variableName: String,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val variablesTuple = root.tupleFor("variables")
            ?: return SourcePatchResult.NotFound(variableName)
        val variables = variablesTuple.valueNode as? SequenceNode
            ?: return SourcePatchResult.Unsupported("OpenMacro variables is not a list.")
        val variable = variables.value
            .filterIsInstance<MappingNode>()
            .find { it.textValue("name") == variableName }
            ?: return SourcePatchResult.NotFound(variableName)
        val start = (if (variables.value.size == 1) {
            variablesTuple.keyNode.startMark.orElse(null)?.index?.let {
                lineStartBefore(sourceText, it)
            }
        } else {
            variable.startMark.orElse(null)?.index?.let {
                lineStartBefore(sourceText, it)
            }
        }) ?: return SourcePatchResult.Unsupported("Variable declaration has no source location.")
        val end = if (variables.value.size == 1) {
            lineEndAfter(sourceText, variablesTuple.valueNode)
        } else {
            lineEndAfter(sourceText, variable)
        }
        return SourcePatchResult.Success(sourceText.removeRange(start, end))
    }

    fun renameVariable(
        sourceText: String,
        oldName: String,
        newName: String,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val variables = root.valueFor("variables") as? SequenceNode
            ?: return SourcePatchResult.NotFound(oldName)
        val variable = variables.value
            .filterIsInstance<MappingNode>()
            .find { it.textValue("name") == oldName }
            ?: return SourcePatchResult.NotFound(oldName)
        val targets = mutableListOf<Node>()
        val declarationName = variable.tupleFor("name")?.valueNode
            ?: return SourcePatchResult.Unsupported("Variable declaration has no name location.")
        targets += declarationName
        root.collectVariableRenameTargets(oldName, targets)
        val replacements = targets
            .map { node ->
                val start = node.startMark.orElse(null)?.index
                    ?: return SourcePatchResult.Unsupported("Variable reference has no source location.")
                val end = node.endMark.orElse(null)?.index
                    ?: return SourcePatchResult.Unsupported("Variable reference has no source location.")
                SourceReplacement(start, end, MacroValue.Text(newName).asYamlValue())
            }
            .distinctBy { it.start to it.end }
            .sortedByDescending(SourceReplacement::start)
        return SourcePatchResult.Success(
            replacements.fold(sourceText) { patched, replacement ->
                patched.replaceRange(replacement.start, replacement.end, replacement.text)
            },
        )
    }

    fun switchConditionGroup(
        sourceText: String,
        groupPath: String,
        logic: ConditionGroupLogic,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val conditionTree = root.valueFor("condition_tree") as? MappingNode
            ?: return SourcePatchResult.NotFound(groupPath)
        val target = conditionTree.findConditionNode(groupPath.pathTokens())
            ?: return SourcePatchResult.NotFound(groupPath)
        val tuple = target.tupleFor("all") ?: target.tupleFor("any")
            ?: return SourcePatchResult.Unsupported("Target condition node is not an AND/OR group.")
        val currentKey = (tuple.keyNode as? ScalarNode)?.value
            ?: return SourcePatchResult.Unsupported("Condition group key has no source value.")
        if (currentKey == logic.sourceKey) {
            return SourcePatchResult.Success(sourceText)
        }
        val start = tuple.keyNode.startMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Condition group key has no source location.")
        val end = tuple.keyNode.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Condition group key has no source location.")
        return SourcePatchResult.Success(
            sourceText.replaceRange(start, end, logic.sourceKey),
        )
    }

    fun addConditionTreeChild(
        sourceText: String,
        groupPath: String,
        child: MacroBlock,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val conditionTree = root.valueFor("condition_tree") as? MappingNode
            ?: return SourcePatchResult.NotFound(groupPath)
        val target = conditionTree.findConditionNode(groupPath.pathTokens())
            ?: return SourcePatchResult.NotFound(groupPath)
        val sequence = (target.valueFor("all") ?: target.valueFor("any")) as? SequenceNode
            ?: return SourcePatchResult.Unsupported("Target condition node is not an AND/OR group.")
        val insertion = sequence.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Condition group has no source location.")
        val column = sequence.value.firstOrNull()
            ?.startMark
            ?.orElse(null)
            ?.column
            ?: ((sequence.startMark.orElse(null)?.column ?: 0) + 2)
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
            append(" ".repeat(column))
            append("- condition:\n")
            append(" ".repeat(column + 4))
            append("id: ")
            append(MacroValue.Text(child.id).asYamlValue())
            append("\n")
            append(" ".repeat(column + 4))
            append("type: ")
            append(MacroValue.Text(child.type).asYamlValue())
            append("\n")
            if (child.config.isNotEmpty()) {
                append(" ".repeat(column + 4))
                append("config:\n")
                child.config.toSortedMap().forEach { (key, value) ->
                    append(" ".repeat(column + 6))
                    append(MacroValue.Text(key).asYamlValue())
                    append(": ")
                    append(value.asYamlValue())
                    append("\n")
                }
            }
        }
        return SourcePatchResult.Success(
            sourceText.replaceRange(insertion, insertion, text),
        )
    }

    fun removeConditionTreeChild(
        sourceText: String,
        childPath: String,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val tokens = childPath.pathTokens()
            ?: return SourcePatchResult.NotFound(childPath)
        if (tokens.isEmpty()) {
            return SourcePatchResult.Unsupported("The root condition group cannot be removed.")
        }
        val childIndex = tokens.last().toIntOrNull()
            ?: return SourcePatchResult.Unsupported("Only direct condition-tree children can be removed.")
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val conditionTree = root.valueFor("condition_tree") as? MappingNode
            ?: return SourcePatchResult.NotFound(childPath)
        val parent = conditionTree.findConditionNode(tokens.dropLast(1))
            ?: return SourcePatchResult.NotFound(childPath)
        val sequence = (parent.valueFor("all") ?: parent.valueFor("any")) as? SequenceNode
            ?: return SourcePatchResult.Unsupported("Parent condition node is not an AND/OR group.")
        if (childIndex !in sequence.value.indices) {
            return SourcePatchResult.NotFound(childPath)
        }
        if (sequence.value.size <= 1) {
            return SourcePatchResult.Unsupported("Condition groups must keep at least one child.")
        }
        val child = sequence.value[childIndex]
        val start = child.startMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Condition child has no source location.")
        val end = child.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Condition child has no source location.")
        return SourcePatchResult.Success(
            sourceText.replaceRange(
                lineStartBefore(sourceText, start),
                lineEndAfter(sourceText, end),
                "",
            ),
        )
    }

    fun wrapConditionTreeChildInNot(
        sourceText: String,
        childPath: String,
    ): SourcePatchResult =
        replaceConditionTreeChildItem(sourceText, childPath) { itemText, _ ->
            itemText.wrapSequenceItemInNot()
        }

    fun unwrapConditionTreeNot(
        sourceText: String,
        childPath: String,
    ): SourcePatchResult =
        replaceConditionTreeChildItem(sourceText, childPath) { itemText, child ->
            val notNode = child as? MappingNode
                ?: return@replaceConditionTreeChildItem SourcePatchResult.Unsupported(
                    "Condition child is not a NOT group.",
                )
            val inner = notNode.valueFor("not")
                ?: return@replaceConditionTreeChildItem SourcePatchResult.Unsupported(
                    "Condition child is not a NOT group.",
                )
            itemText.unwrapNotSequenceItem(inner)
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
                block.findBlockOrNested(blockId)?.let { found ->
                    return found
                }
            }
        }
        return (valueFor("condition_tree") as? MappingNode)?.findConditionBlock(blockId)
    }

    private fun MappingNode.findBlockOrNested(blockId: String): MappingNode? {
        if (textValue("id") == blockId) {
            return this
        }
        val actions = ((valueFor("config") as? MappingNode)?.valueFor("actions"))
            as? SequenceNode
        actions?.value?.filterIsInstance<MappingNode>()?.forEach { child ->
            child.findBlockOrNested(blockId)?.let { return it }
        }
        return null
    }

    private fun MappingNode.findGroupedActionSequenceContaining(
        childBlockId: String,
    ): SequenceNode? {
        val topActions = valueFor("actions") as? SequenceNode ?: return null
        topActions.value.filterIsInstance<MappingNode>().forEach { block ->
            block.findNestedActionSequenceContaining(childBlockId)?.let { return it }
        }
        return null
    }

    private fun MappingNode.findNestedActionSequenceContaining(
        childBlockId: String,
    ): SequenceNode? {
        val actions = ((valueFor("config") as? MappingNode)?.valueFor("actions"))
            as? SequenceNode
            ?: return null
        val containsChild = actions.value.any { node ->
            (node as? MappingNode)?.textValue("id") == childBlockId
        }
        if (containsChild) {
            return actions
        }
        actions.value.filterIsInstance<MappingNode>().forEach { child ->
            child.findNestedActionSequenceContaining(childBlockId)?.let { return it }
        }
        return null
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

    private fun MappingNode.collectVariableRenameTargets(
        oldName: String,
        targets: MutableList<Node>,
    ) {
        listOf("triggers", "conditions", "actions").forEach { lane ->
            val blocks = valueFor(lane) as? SequenceNode
            blocks?.value?.filterIsInstance<MappingNode>()?.forEach { block ->
                block.collectBlockVariableRenameTargets(oldName, targets)
            }
        }
        (valueFor("condition_tree") as? MappingNode)
            ?.collectConditionVariableRenameTargets(oldName, targets)
    }

    private fun MappingNode.collectConditionVariableRenameTargets(
        oldName: String,
        targets: MutableList<Node>,
    ) {
        (valueFor("condition") as? MappingNode)
            ?.collectBlockVariableRenameTargets(oldName, targets)
        listOf("all", "any").forEach { group ->
            val children = valueFor(group) as? SequenceNode
            children?.value?.filterIsInstance<MappingNode>()?.forEach { child ->
                child.collectConditionVariableRenameTargets(oldName, targets)
            }
        }
        (valueFor("not") as? MappingNode)
            ?.collectConditionVariableRenameTargets(oldName, targets)
    }

    private fun MappingNode.collectBlockVariableRenameTargets(
        oldName: String,
        targets: MutableList<Node>,
    ) {
        val config = valueFor("config") as? MappingNode ?: return
        if (textValue("type") in VARIABLE_TARGET_ACTION_TYPES) {
            config.tupleFor("name")
                ?.valueNode
                ?.takeIf { it.textValue() == oldName }
                ?.let(targets::add)
        }
        config.collectVariableReferenceTargets(oldName, targets)
        (config.valueFor("actions") as? SequenceNode)
            ?.value
            ?.filterIsInstance<MappingNode>()
            ?.forEach { child -> child.collectBlockVariableRenameTargets(oldName, targets) }
    }

    private fun MappingNode.findConditionNode(tokens: List<String>?): MappingNode? {
        if (tokens == null) {
            return null
        }
        if (tokens.isEmpty()) {
            return this
        }
        val head = tokens.first()
        val tail = tokens.drop(1)
        if (head == "not") {
            return (valueFor("not") as? MappingNode)?.findConditionNode(tail)
        }
        val children = ((valueFor("all") ?: valueFor("any")) as? SequenceNode)
            ?.value
            ?.filterIsInstance<MappingNode>()
            ?: return null
        val index = head.toIntOrNull() ?: return null
        return children.getOrNull(index)?.findConditionNode(tail)
    }

    private fun replaceConditionTreeChildItem(
        sourceText: String,
        childPath: String,
        replacement: (String, Node) -> SourcePatchResult,
    ): SourcePatchResult {
        val source = OpenMacroYamlReader.read(sourceText)
        if (source is OpenMacroSourceResult.Failure) {
            return SourcePatchResult.InvalidSource(source.issues)
        }
        val tokens = childPath.pathTokens()
            ?: return SourcePatchResult.NotFound(childPath)
        if (tokens.isEmpty()) {
            return SourcePatchResult.Unsupported("The root condition group cannot be changed here.")
        }
        val childIndex = tokens.last().toIntOrNull()
            ?: return SourcePatchResult.Unsupported("Only direct condition-tree children can be changed.")
        val root = Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
            ?: return SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        val conditionTree = root.valueFor("condition_tree") as? MappingNode
            ?: return SourcePatchResult.NotFound(childPath)
        val parent = conditionTree.findConditionNode(tokens.dropLast(1))
            ?: return SourcePatchResult.NotFound(childPath)
        val sequence = (parent.valueFor("all") ?: parent.valueFor("any")) as? SequenceNode
            ?: return SourcePatchResult.Unsupported("Parent condition node is not an AND/OR group.")
        if (childIndex !in sequence.value.indices) {
            return SourcePatchResult.NotFound(childPath)
        }
        val child = sequence.value[childIndex]
        val start = child.startMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Condition child has no source location.")
        val end = child.endMark.orElse(null)?.index
            ?: return SourcePatchResult.Unsupported("Condition child has no source location.")
        val replacementText = when (
            val result = replacement(
                sourceText.substring(lineStartBefore(sourceText, start), lineEndAfter(sourceText, end)),
                child,
            )
        ) {
            is SourcePatchResult.Success -> result.sourceText
            is SourcePatchResult.NotFound -> return result
            is SourcePatchResult.Unsupported -> return result
            is SourcePatchResult.InvalidSource -> return result
        }
        return SourcePatchResult.Success(
            sourceText.replaceRange(
                lineStartBefore(sourceText, start),
                lineEndAfter(sourceText, end),
                replacementText,
            ),
        )
    }

    private fun Node.collectVariableReferenceTargets(
        oldName: String,
        targets: MutableList<Node>,
    ) {
        when (this) {
            is MappingNode -> {
                if (value.size == 1 && tupleFor("variable")?.valueNode?.textValue() == oldName) {
                    targets += tupleFor("variable")!!.valueNode
                }
                value.forEach { tuple ->
                    tuple.valueNode.collectVariableReferenceTargets(oldName, targets)
                }
            }
            is SequenceNode -> value.forEach { child ->
                child.collectVariableReferenceTargets(oldName, targets)
            }
            else -> Unit
        }
    }

    private fun MappingNode.valueFor(key: String): Node? =
        tupleFor(key)?.valueNode

    private fun validatedRoot(sourceText: String): MappingNode? {
        if (OpenMacroYamlReader.read(sourceText) is OpenMacroSourceResult.Failure) {
            return null
        }
        return Compose(settings).composeString(sourceText).orElse(null) as? MappingNode
    }

    private fun invalidRoot(sourceText: String): SourcePatchResult =
        when (val source = OpenMacroYamlReader.read(sourceText)) {
            is OpenMacroSourceResult.Failure -> SourcePatchResult.InvalidSource(source.issues)
            is OpenMacroSourceResult.Success ->
                SourcePatchResult.Unsupported("OpenMacro root is not an object.")
        }

    private fun MappingNode.tupleFor(key: String) =
        value.firstOrNull { tuple ->
            (tuple.keyNode as? ScalarNode)?.value == key
        }

    private fun MappingNode.textValue(key: String): String? =
        (valueFor(key) as? ScalarNode)
            ?.takeIf { it.tag == Tag.STR }
            ?.value

    private fun Node.textValue(): String? =
        (this as? ScalarNode)
            ?.takeIf { it.tag == Tag.STR }
            ?.value

    private fun String.pathTokens(): List<String>? {
        val parts = split(".")
        if (parts.firstOrNull() != "root") {
            return null
        }
        return parts.drop(1)
    }

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

    private val CapabilityLane.sourceKey: String
        get() = when (this) {
            CapabilityLane.TRIGGER -> "triggers"
            CapabilityLane.CONDITION -> "conditions"
            CapabilityLane.ACTION -> "actions"
        }

    private fun MacroBlock.asTopLevelYaml(): String = asSequenceItemYaml(indent = 2)

    private fun MacroBlock.asSequenceItemYaml(indent: Int): String = buildString {
        append(" ".repeat(indent))
        append("- id: ")
        append(MacroValue.Text(id).asYamlValue())
        append("\n")
        append(" ".repeat(indent + 2))
        append("type: ")
        append(MacroValue.Text(type).asYamlValue())
        append("\n")
        if (config.isNotEmpty()) {
            append(" ".repeat(indent + 2))
            append("config:\n")
            config.toSortedMap().forEach { (key, value) ->
                append(" ".repeat(indent + 4))
                append(MacroValue.Text(key).asYamlValue())
                append(": ")
                append(value.asYamlValue())
                append("\n")
            }
        }
    }

    private fun Node.isBlockSequenceItem(sourceText: String): Boolean {
        val start = startMark.orElse(null)?.index ?: return false
        val lineStart = lineStartBefore(sourceText, start)
        val lineEnd = lineEndAfter(sourceText, start)
        return sourceText.substring(lineStart, lineEnd).trimStart().startsWith("- ")
    }

    private fun Node.sequenceItemIndent(sourceText: String): Int? {
        val start = startMark.orElse(null)?.index ?: return null
        val lineStart = lineStartBefore(sourceText, start)
        val lineEnd = lineEndAfter(sourceText, start)
        val line = sourceText.substring(lineStart, lineEnd)
        val content = line.indexOfFirst { it != ' ' && it != '\t' }
        return content.takeIf { it >= 0 && line.startsWith("- ", it) }
    }

    private fun MacroVariable.asVariableYaml(): String = buildString {
        append("  - name: ")
        append(MacroValue.Text(name).asYamlValue())
        append("\n    type: ")
        append(MacroValue.Text(type.name.lowercase()).asYamlValue())
        append("\n")
        initialValue?.let { initial ->
            append("    initial: ")
            append(initial.asYamlValue())
            append("\n")
        }
        if (type == MacroVariableType.SECRET) {
            append("    secret_key: ")
            append(MacroValue.Text(secretKey.orEmpty()).asYamlValue())
            append("\n")
        }
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

    private fun String.wrapSequenceItemInNot(): SourcePatchResult {
        val trailingNewline = endsWith("\n")
        val lines = trimTrailingLineBreak().split("\n").map { it.removeSuffix("\r") }
        val first = lines.firstOrNull()
            ?: return SourcePatchResult.Unsupported("Condition child source is empty.")
        val indent = first.takeWhile { it == ' ' }
        if (!first.startsWith("$indent- ")) {
            return SourcePatchResult.Unsupported("Condition child is not a YAML list item.")
        }
        val childFirstLine = first.drop(indent.length + 2)
        if (childFirstLine.isBlank()) {
            return SourcePatchResult.Unsupported("Condition child is not a block mapping.")
        }
        val wrapped = buildList {
            add("$indent- not:")
            add("$indent    $childFirstLine")
            lines.drop(1).forEach { line ->
                add(if (line.isEmpty()) line else "  $line")
            }
        }.joinToString("\n")
        return SourcePatchResult.Success(
            wrapped + if (trailingNewline) "\n" else "",
        )
    }

    private fun String.unwrapNotSequenceItem(inner: Node): SourcePatchResult {
        val trailingNewline = endsWith("\n")
        val lines = trimTrailingLineBreak().split("\n").map { it.removeSuffix("\r") }
        val first = lines.firstOrNull()
            ?: return SourcePatchResult.Unsupported("Condition child source is empty.")
        val indent = first.takeWhile { it == ' ' }
        if (!first.trimStart().startsWith("- not:")) {
            return SourcePatchResult.Unsupported("Condition child is not a NOT group.")
        }
        val childLines = lines.drop(1)
        val childFirst = childLines.firstOrNull()
            ?: return SourcePatchResult.Unsupported("NOT group has no child.")
        val childIndent = inner.startMark.orElse(null)?.column
            ?: childFirst.takeWhile { it == ' ' }.length
        val unwrapped = buildList {
            add("$indent- ${childFirst.drop(childIndent)}")
            childLines.drop(1).forEach { line ->
                add(if (line.startsWith("  ")) line.drop(2) else line)
            }
        }.joinToString("\n")
        return SourcePatchResult.Success(
            unwrapped + if (trailingNewline) "\n" else "",
        )
    }

    private fun String.trimTrailingLineBreak(): String =
        removeSuffix("\n").removeSuffix("\r")
}

private data class SourceReplacement(
    val start: Int,
    val end: Int,
    val text: String,
)

private val VARIABLE_TARGET_ACTION_TYPES = setOf(
    "openmacro.variable.set",
    "openmacro.variable.increment",
    "openmacro.variable.toggle",
)

sealed interface SourcePatchResult {
    data class Success(val sourceText: String) : SourcePatchResult

    data class NotFound(val blockId: String) : SourcePatchResult

    data class Unsupported(val message: String) : SourcePatchResult

    data class InvalidSource(val issues: List<SourceIssue>) : SourcePatchResult
}
