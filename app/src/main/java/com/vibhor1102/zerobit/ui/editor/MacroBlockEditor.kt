/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreationContext
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityInsertion
import com.vibhor1102.zerobit.openmacro.model.ConditionGroupLogic
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import java.math.BigDecimal

object MacroBlockEditor {
    fun topLevelTemplates(
        registry: CapabilityRegistry,
        lane: CapabilityLane,
        document: OpenMacroDocument,
    ): List<TopLevelBlockTemplate> = capabilityTemplates(
        registry = registry,
        lane = lane,
        document = document,
        insertion = CapabilityInsertion.TopLevel,
    )

    private fun capabilityTemplates(
        registry: CapabilityRegistry,
        lane: CapabilityLane,
        document: OpenMacroDocument,
        insertion: CapabilityInsertion,
    ): List<TopLevelBlockTemplate> =
        registry.list(lane).mapNotNull { definition ->
            definition.creation?.let { creation ->
                val setup = creation.setup
                if (setup != null) {
                    val fields = definition.fields.filter { it.key in setup.fieldKeys }
                    if (fields.size != setup.fieldKeys.size) {
                        return@let null
                    }
                    TopLevelBlockTemplate(
                        lane = lane,
                        label = definition.displayName,
                        description = definition.description,
                        idBase = creation.idBase,
                        type = definition.type,
                        defaultConfig = setup.initialConfig,
                        setupFields = fields,
                        setupRequired = true,
                    )
                } else {
                    val config = creation.configFor(
                        CapabilityCreationContext(document, insertion),
                    ) ?: return@let null
                    TopLevelBlockTemplate(
                        lane = lane,
                        label = definition.displayName,
                        description = definition.description,
                        idBase = creation.idBase,
                        type = definition.type,
                        defaultConfig = config,
                    )
                }
            }
        }

    fun filterTopLevelTemplates(
        templates: List<TopLevelBlockTemplate>,
        query: String,
    ): List<TopLevelBlockTemplate> {
        val search = query.trim()
        if (search.isEmpty()) return templates
        return templates.filter { template ->
            template.label.contains(search, ignoreCase = true) ||
                template.description.contains(search, ignoreCase = true) ||
                template.type.contains(search, ignoreCase = true)
        }
    }

    fun configureTemplate(
        registry: CapabilityRegistry,
        document: OpenMacroDocument,
        template: TopLevelBlockTemplate,
        config: Map<String, MacroValue>,
    ): TemplateConfigurationResult {
        if (!template.setupRequired) {
            return TemplateConfigurationResult.Rejected("This capability is already configured.")
        }
        val definition = registry.find(template.type)
            ?: return TemplateConfigurationResult.Rejected("This capability is not supported.")
        val configured = template.copy(
            defaultConfig = config,
            setupRequired = false,
        )
        val block = configured.block(template.idBase)
        val issues = definition.validate(block, "setup") +
            definition.validateDocument(block, "setup", document, registry)
        return if (issues.isEmpty()) {
            TemplateConfigurationResult.Configured(configured)
        } else {
            TemplateConfigurationResult.Rejected(issues.first().message)
        }
    }

    fun setupRequiredPermissions(
        registry: CapabilityRegistry,
        document: OpenMacroDocument,
        template: TopLevelBlockTemplate,
        config: Map<String, MacroValue>,
    ): Set<AndroidPermission> {
        if (!template.setupRequired) return emptySet()
        val definition = registry.find(template.type) ?: return emptySet()
        val block = template.copy(
            defaultConfig = config,
            setupRequired = false,
        ).block(template.idBase)
        val issues = definition.validate(block, "setup") +
            definition.validateDocument(block, "setup", document, registry)
        if (issues.isNotEmpty()) return emptySet()
        return definition.requiredPermissions(block, document, registry)
    }

    fun addTopLevelBlock(
        document: OpenMacroDocument,
        template: TopLevelBlockTemplate,
    ): BlockEditResult {
        if (template.setupRequired) {
            return BlockEditResult.Rejected("Finish capability setup before adding it.")
        }
        if (template.lane == CapabilityLane.CONDITION && document.conditionTree != null) {
            return BlockEditResult.Rejected(
                "This macro uses a condition tree. Add conditions inside that tree.",
            )
        }
        val block = template.block(
            uniqueId(template.idBase, document.allBlockIds()),
        )
        return BlockEditResult.Updated(
            when (template.lane) {
                CapabilityLane.TRIGGER -> document.copy(triggers = document.triggers + block)
                CapabilityLane.CONDITION ->
                    document.copy(conditions = document.conditions + block)
                CapabilityLane.ACTION -> document.copy(actions = document.actions + block)
            },
        )
    }

    fun removeTopLevelBlock(
        document: OpenMacroDocument,
        blockId: String,
    ): BlockEditResult {
        val lane = document.topLevelLaneFor(blockId)
            ?: return BlockEditResult.NotFound(blockId)
        val blocks = document.topLevelBlocks(lane)
        if (lane == CapabilityLane.TRIGGER && blocks.size <= 1) {
            return BlockEditResult.Rejected("A macro must keep at least one trigger.")
        }
        if (lane == CapabilityLane.ACTION && blocks.size <= 1) {
            return BlockEditResult.Rejected("A macro must keep at least one action.")
        }
        return BlockEditResult.Updated(
            document.withTopLevelBlocks(lane, blocks.filterNot { it.id == blockId }),
        )
    }

    fun moveTopLevelBlock(
        document: OpenMacroDocument,
        blockId: String,
        direction: NestedActionMoveDirection,
    ): BlockEditResult {
        val lane = document.topLevelLaneFor(blockId)
            ?: return BlockEditResult.NotFound(blockId)
        val blocks = document.topLevelBlocks(lane)
        val index = blocks.indexOfFirst { it.id == blockId }
        val targetIndex = when (direction) {
            NestedActionMoveDirection.UP -> index - 1
            NestedActionMoveDirection.DOWN -> index + 1
        }
        if (targetIndex !in blocks.indices) {
            return BlockEditResult.Rejected("Block is already at that edge.")
        }
        val reordered = blocks.toMutableList().apply {
            val moved = removeAt(index)
            add(targetIndex, moved)
        }
        return BlockEditResult.Updated(document.withTopLevelBlocks(lane, reordered))
    }

    fun variableTemplates(): List<VariableDeclarationTemplate> =
        VariableDeclarationTemplate.values().toList()

    fun addVariable(
        document: OpenMacroDocument,
        template: VariableDeclarationTemplate,
    ): BlockEditResult {
        val existingNames = document.variables.mapTo(mutableSetOf(), MacroVariable::name)
        val name = uniqueVariableName(template.nameBase, existingNames)
        return BlockEditResult.Updated(
            document.copy(
                variables = document.variables + template.variable(name),
            ),
        )
    }

    fun removeVariable(
        document: OpenMacroDocument,
        variableName: String,
    ): BlockEditResult {
        if (document.variables.none { it.name == variableName }) {
            return BlockEditResult.Rejected("Variable '$variableName' was not found.")
        }
        val references = document.variableReferences(variableName)
        if (references.isNotEmpty()) {
            val places = references.distinct().joinToString()
            return BlockEditResult.Rejected(
                "Variable '$variableName' is still used by $places. Remove those references before deleting it.",
            )
        }
        return BlockEditResult.Updated(
            document.copy(
                variables = document.variables.filterNot { it.name == variableName },
            ),
        )
    }

    fun renameVariable(
        document: OpenMacroDocument,
        oldName: String,
        newName: String,
    ): BlockEditResult {
        val trimmedNewName = newName.trim()
        if (!VARIABLE_NAME_PATTERN.matches(trimmedNewName)) {
            return BlockEditResult.Rejected(
                "Variable names must start with a lowercase letter and use only lowercase letters, numbers, or underscores.",
            )
        }
        if (oldName == trimmedNewName) {
            return BlockEditResult.Rejected("Choose a different variable name.")
        }
        if (document.variables.none { it.name == oldName }) {
            return BlockEditResult.Rejected("Variable '$oldName' was not found.")
        }
        if (document.variables.any { it.name == trimmedNewName }) {
            return BlockEditResult.Rejected("Variable '$trimmedNewName' already exists.")
        }
        fun update(block: MacroBlock): MacroBlock =
            block.renameVariableReferences(oldName, trimmedNewName)
        return BlockEditResult.Updated(
            document.copy(
                variables = document.variables.map { variable ->
                    if (variable.name == oldName) {
                        variable.copy(name = trimmedNewName)
                    } else {
                        variable
                    }
                },
                triggers = document.triggers.map(::update),
                conditions = document.conditions.map(::update),
                actions = document.actions.map(::update),
                conditionTree = document.conditionTree?.mapBlocks(::update),
            ),
        )
    }

    fun switchConditionGroup(
        document: OpenMacroDocument,
        groupPath: String,
        logic: ConditionGroupLogic,
    ): BlockEditResult {
        val tree = document.conditionTree
            ?: return BlockEditResult.Rejected("This macro does not use a condition tree.")
        val tokens = groupPath.pathTokens()
            ?: return BlockEditResult.Rejected("Condition group path is invalid.")
        val switched = tree.switchConditionGroup(tokens, logic)
        return if (switched == null) {
            BlockEditResult.Rejected("Condition group was not found.")
        } else {
            BlockEditResult.Updated(
                document.copy(conditionTree = switched),
            )
        }
    }

    fun conditionTreeChildTemplates(
        registry: CapabilityRegistry,
        document: OpenMacroDocument,
        groupPath: String,
    ): List<TopLevelBlockTemplate> =
        capabilityTemplates(
            registry,
            CapabilityLane.CONDITION,
            document,
            CapabilityInsertion.ConditionGroup(groupPath),
        )

    fun newConditionTreeChild(
        document: OpenMacroDocument,
        template: TopLevelBlockTemplate,
    ): MacroBlock? =
        if (template.lane == CapabilityLane.CONDITION && !template.setupRequired) template.block(
            uniqueId(template.idBase, document.allBlockIds()),
        ) else null

    fun addConditionTreeChild(
        document: OpenMacroDocument,
        groupPath: String,
        child: MacroBlock,
    ): BlockEditResult {
        val tree = document.conditionTree
            ?: return BlockEditResult.Rejected("This macro does not use a condition tree.")
        if (child.id in document.allBlockIds()) {
            return BlockEditResult.Rejected("Condition block '${child.id}' already exists.")
        }
        val tokens = groupPath.pathTokens()
            ?: return BlockEditResult.Rejected("Condition group path is invalid.")
        val updated = tree.addConditionGroupChild(tokens, child)
            ?: return BlockEditResult.Rejected("Condition group was not found.")
        return BlockEditResult.Updated(
            document.copy(conditionTree = updated),
        )
    }

    fun removeConditionTreeChild(
        document: OpenMacroDocument,
        childPath: String,
    ): BlockEditResult {
        val tree = document.conditionTree
            ?: return BlockEditResult.Rejected("This macro does not use a condition tree.")
        val tokens = childPath.pathTokens()
            ?: return BlockEditResult.Rejected("Condition child path is invalid.")
        if (tokens.isEmpty()) {
            return BlockEditResult.Rejected("The root condition group cannot be removed.")
        }
        var rejection: String? = null
        val updated = tree.removeConditionTreeChild(tokens) { message ->
            rejection = message
        }
        rejection?.let {
            return BlockEditResult.Rejected(it)
        }
        return if (updated == null) {
            BlockEditResult.NotFound(childPath)
        } else {
            BlockEditResult.Updated(
                document.copy(conditionTree = updated),
            )
        }
    }

    fun wrapConditionTreeChildInNot(
        document: OpenMacroDocument,
        childPath: String,
    ): BlockEditResult =
        transformConditionTreeChild(
            document = document,
            childPath = childPath,
        ) { child ->
            if (child is MacroConditionNode.Not) {
                ChildTransformResult.Rejected("Condition child is already a NOT group.")
            } else {
                ChildTransformResult.Changed(MacroConditionNode.Not(child))
            }
        }

    fun unwrapConditionTreeNot(
        document: OpenMacroDocument,
        childPath: String,
    ): BlockEditResult =
        transformConditionTreeChild(
            document = document,
            childPath = childPath,
        ) { child ->
            if (child is MacroConditionNode.Not) {
                ChildTransformResult.Changed(child.child)
            } else {
                ChildTransformResult.Rejected("Condition child is not a NOT group.")
            }
        }

    fun findBlock(
        document: OpenMacroDocument,
        blockId: String,
    ): MacroBlock? =
        (document.triggers + document.conditions + document.actions)
            .firstNotNullOfOrNull { it.findBlock(blockId) }
            ?: document.conditionTree?.findBlock(blockId)

    fun nestedActions(block: MacroBlock): List<MacroBlock> =
        block.nestedActionBlocks()

    fun groupedActionTemplates(
        registry: CapabilityRegistry,
        document: OpenMacroDocument,
        groupBlockId: String,
    ): List<TopLevelBlockTemplate> {
        val depth = document.actionGroupDepth(groupBlockId) ?: return emptyList()
        return capabilityTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
            CapabilityInsertion.ActionGroup(groupBlockId, depth),
        )
    }

    fun addGroupedAction(
        document: OpenMacroDocument,
        groupBlockId: String,
        template: TopLevelBlockTemplate,
    ): BlockEditResult {
        if (template.lane != CapabilityLane.ACTION) {
            return BlockEditResult.Rejected("Only actions can be added to an action group.")
        }
        if (template.setupRequired) {
            return BlockEditResult.Rejected("Finish capability setup before adding it.")
        }
        val existingIds = document.allBlockIds()
        val newBlockId = uniqueId("${groupBlockId}-${template.idBase}", existingIds)
        val newBlock = template.block(newBlockId)
        return updateNestedActions(
            document = document,
            groupBlockId = groupBlockId,
        ) { actions ->
            actions + newBlock
        }
    }

    fun addGroupedLogAction(
        document: OpenMacroDocument,
        groupBlockId: String,
    ): BlockEditResult {
        val template = groupedActionTemplates(
            CapabilityRegistry.builtIn(),
            document,
            groupBlockId,
        )
            .single { it.type == "android.log.write" }
        return addGroupedAction(document, groupBlockId, template)
    }

    fun removeGroupedAction(
        document: OpenMacroDocument,
        childBlockId: String,
    ): BlockEditResult =
        updateNestedActionsContainingChild(
            document = document,
            childBlockId = childBlockId,
        ) { actions, index ->
            if (actions.size <= 1) {
                return@updateNestedActionsContainingChild NestedActionListChange.Rejected(
                    "Action groups must keep at least one child action.",
                )
            }
            NestedActionListChange.Changed(
                actions.toMutableList().apply { removeAt(index) },
            )
        }

    fun moveGroupedAction(
        document: OpenMacroDocument,
        childBlockId: String,
        direction: NestedActionMoveDirection,
    ): BlockEditResult =
        updateNestedActionsContainingChild(
            document = document,
            childBlockId = childBlockId,
        ) { actions, index ->
            val targetIndex = when (direction) {
                NestedActionMoveDirection.UP -> index - 1
                NestedActionMoveDirection.DOWN -> index + 1
            }
            if (targetIndex !in actions.indices) {
                return@updateNestedActionsContainingChild NestedActionListChange.Rejected(
                    "Grouped action is already at that edge.",
                )
            }
            NestedActionListChange.Changed(
                actions.toMutableList().apply {
                    val moved = removeAt(index)
                    add(targetIndex, moved)
                },
            )
        }

    fun updateConfig(
        document: OpenMacroDocument,
        blockId: String,
        key: String,
        value: MacroValue?,
    ): BlockEditResult {
        var matches = 0
        fun update(block: MacroBlock): MacroBlock {
            val nestedConfig = block.config.updateNestedActionConfigs(
                blockId = blockId,
                key = key,
                value = value,
                onMatch = { matches += 1 },
            )
            if (block.id != blockId) {
                return block.copy(config = nestedConfig)
            }
            matches += 1
            val updatedConfig = nestedConfig.toMutableMap().apply {
                if (value == null) remove(key) else put(key, value)
            }
            return block.copy(config = updatedConfig)
        }

        val updated = document.copy(
            triggers = document.triggers.map(::update),
            conditions = document.conditions.map(::update),
            actions = document.actions.map(::update),
            conditionTree = document.conditionTree?.mapBlocks(::update),
        )
        return when (matches) {
            0 -> BlockEditResult.NotFound(blockId)
            1 -> BlockEditResult.Updated(updated)
            else -> BlockEditResult.Ambiguous(blockId, matches)
        }
    }

    private fun updateNestedActions(
        document: OpenMacroDocument,
        groupBlockId: String,
        transform: (List<MacroBlock>) -> List<MacroBlock>,
    ): BlockEditResult {
        var matches = 0
        fun update(block: MacroBlock): MacroBlock {
            val nestedConfig = block.config.updateNestedActionLists { child ->
                update(child)
            }
            if (block.id != groupBlockId || block.type != ACTION_GROUP_TYPE) {
                return block.copy(config = nestedConfig)
            }
            matches += 1
            val current = block.nestedActionBlocks()
            val updated = transform(current)
            return block.copy(
                config = nestedConfig.toMutableMap().apply {
                    put(
                        "actions",
                        MacroValue.ListValue(updated.map(MacroBlock::toNestedActionValue)),
                    )
                },
            )
        }

        val updated = document.copy(
            actions = document.actions.map(::update),
        )
        return when (matches) {
            0 -> BlockEditResult.NotFound(groupBlockId)
            1 -> BlockEditResult.Updated(updated)
            else -> BlockEditResult.Ambiguous(groupBlockId, matches)
        }
    }

    private fun updateNestedActionsContainingChild(
        document: OpenMacroDocument,
        childBlockId: String,
        transform: (List<MacroBlock>, Int) -> NestedActionListChange,
    ): BlockEditResult {
        var matches = 0
        var rejection: String? = null
        fun update(block: MacroBlock): MacroBlock {
            val nestedConfig = block.config.updateNestedActionLists { child ->
                update(child)
            }
            if (block.type != ACTION_GROUP_TYPE) {
                return block.copy(config = nestedConfig)
            }
            val current = block.nestedActionBlocks()
            val matchIndexes = current.mapIndexedNotNull { index, child ->
                index.takeIf { child.id == childBlockId }
            }
            if (matchIndexes.isEmpty()) {
                return block.copy(config = nestedConfig)
            }
            matches += matchIndexes.size
            if (matchIndexes.size > 1) {
                return block.copy(config = nestedConfig)
            }
            val updated = when (val change = transform(current, matchIndexes.single())) {
                is NestedActionListChange.Changed -> change.actions
                is NestedActionListChange.Rejected -> {
                    rejection = change.message
                    return block.copy(config = nestedConfig)
                }
            }
            return block.copy(
                config = nestedConfig.toMutableMap().apply {
                    put(
                        "actions",
                        MacroValue.ListValue(updated.map(MacroBlock::toNestedActionValue)),
                    )
                },
            )
        }

        val updated = document.copy(
            actions = document.actions.map(::update),
        )
        rejection?.let {
            return BlockEditResult.Rejected(it)
        }
        return when (matches) {
            0 -> BlockEditResult.NotFound(childBlockId)
            1 -> BlockEditResult.Updated(updated)
            else -> BlockEditResult.Ambiguous(childBlockId, matches)
        }
    }

    private fun OpenMacroDocument.allBlockIds(): Set<String> = buildSet {
        triggers.forEach { addAll(it.allBlockIds()) }
        conditions.forEach { addAll(it.allBlockIds()) }
        actions.forEach { addAll(it.allBlockIds()) }
        conditionTree?.conditionBlocks()?.forEach { addAll(it.allBlockIds()) }
    }

    private fun OpenMacroDocument.actionGroupDepth(blockId: String): Int? =
        actions.firstNotNullOfOrNull { it.actionGroupDepth(blockId, depth = 1) }

    private fun MacroBlock.actionGroupDepth(blockId: String, depth: Int): Int? {
        if (id == blockId && type == "openmacro.action.group") return depth
        return nestedActionBlocks().firstNotNullOfOrNull { child ->
            child.actionGroupDepth(blockId, depth + 1)
        }
    }

    private fun MacroBlock.allBlockIds(): Set<String> = buildSet {
        add(id)
        nestedActionBlocks().forEach { addAll(it.allBlockIds()) }
    }

    private fun OpenMacroDocument.variableReferences(variableName: String): List<String> = buildList {
        triggers.forEach { addAll(it.variableReferences(variableName)) }
        conditions.forEach { addAll(it.variableReferences(variableName)) }
        actions.forEach { addAll(it.variableReferences(variableName)) }
        conditionTree?.conditionBlocks()?.forEach { addAll(it.variableReferences(variableName)) }
    }

    private fun MacroBlock.variableReferences(variableName: String): List<String> = buildList {
        if (
            type in VARIABLE_TARGET_ACTION_TYPES &&
            (config["name"] as? MacroValue.Text)?.value == variableName
        ) {
            add(id)
        }
        if (config.referencesVariable(variableName)) {
            add(id)
        }
        nestedActionBlocks().forEach { child ->
            addAll(child.variableReferences(variableName))
        }
    }

    private fun MacroBlock.renameVariableReferences(
        oldName: String,
        newName: String,
    ): MacroBlock {
        val nestedConfig = config.updateNestedActionLists { child ->
            child.renameVariableReferences(oldName, newName)
        }
        val renamedConfig = nestedConfig.renameVariableReferences(oldName, newName)
        return copy(
            config = if (
                type in VARIABLE_TARGET_ACTION_TYPES &&
                (renamedConfig["name"] as? MacroValue.Text)?.value == oldName
            ) {
                renamedConfig.toMutableMap().apply {
                    put("name", MacroValue.Text(newName))
                }
            } else {
                renamedConfig
            },
        )
    }

    private fun updateNestedActionsValue(value: MacroValue, transform: (MacroBlock) -> MacroBlock): MacroValue {
        val child = value.asNestedActionBlock() ?: return value
        return transform(child).toNestedActionValue()
    }

    private fun Map<String, MacroValue>.updateNestedActionLists(
        transform: (MacroBlock) -> MacroBlock,
    ): Map<String, MacroValue> {
        val actions = this["actions"] as? MacroValue.ListValue ?: return this
        return toMutableMap().apply {
            put(
                "actions",
                MacroValue.ListValue(
                    actions.values.map { updateNestedActionsValue(it, transform) },
                ),
            )
        }
    }
}

sealed interface BlockEditResult {
    data class Updated(val document: OpenMacroDocument) : BlockEditResult

    data class NotFound(val blockId: String) : BlockEditResult

    data class Rejected(val message: String) : BlockEditResult

    data class Ambiguous(
        val blockId: String,
        val matchCount: Int,
    ) : BlockEditResult
}

enum class NestedActionMoveDirection {
    UP,
    DOWN,
}

enum class VariableDeclarationTemplate(
    val label: String,
    val nameBase: String,
) {
    TEXT("Text", "text_value"),
    NUMBER("Number", "number_value"),
    BOOLEAN("Boolean", "boolean_value"),
    SECRET("Secret", "secret_value");

    fun variable(name: String): MacroVariable = when (this) {
        TEXT -> MacroVariable(
            name = name,
            type = MacroVariableType.TEXT,
            initialValue = MacroValue.Text(""),
        )
        NUMBER -> MacroVariable(
            name = name,
            type = MacroVariableType.NUMBER,
            initialValue = MacroValue.Number(BigDecimal.ZERO),
        )
        BOOLEAN -> MacroVariable(
            name = name,
            type = MacroVariableType.BOOLEAN,
            initialValue = MacroValue.Boolean(false),
        )
        SECRET -> MacroVariable(
            name = name,
            type = MacroVariableType.SECRET,
            secretKey = name.replace('_', '.'),
        )
    }
}

data class TopLevelBlockTemplate(
    val lane: CapabilityLane,
    val label: String,
    val description: String,
    val idBase: String,
    val type: String,
    val defaultConfig: Map<String, MacroValue>,
    val setupFields: List<CapabilityField> = emptyList(),
    val setupRequired: Boolean = false,
) {
    fun block(id: String): MacroBlock = MacroBlock(
        id = id,
        type = type,
        config = defaultConfig,
    )
}

sealed interface TemplateConfigurationResult {
    data class Configured(val template: TopLevelBlockTemplate) : TemplateConfigurationResult

    data class Rejected(val message: String) : TemplateConfigurationResult
}

private sealed interface NestedActionListChange {
    data class Changed(val actions: List<MacroBlock>) : NestedActionListChange

    data class Rejected(val message: String) : NestedActionListChange
}

private sealed interface ChildTransformResult {
    data class Changed(val node: MacroConditionNode) : ChildTransformResult

    data class Rejected(val message: String) : ChildTransformResult
}

private fun OpenMacroDocument.topLevelLaneFor(blockId: String): CapabilityLane? = when {
    triggers.any { it.id == blockId } -> CapabilityLane.TRIGGER
    conditions.any { it.id == blockId } -> CapabilityLane.CONDITION
    actions.any { it.id == blockId } -> CapabilityLane.ACTION
    else -> null
}

private fun OpenMacroDocument.topLevelBlocks(lane: CapabilityLane): List<MacroBlock> =
    when (lane) {
        CapabilityLane.TRIGGER -> triggers
        CapabilityLane.CONDITION -> conditions
        CapabilityLane.ACTION -> actions
    }

private fun OpenMacroDocument.withTopLevelBlocks(
    lane: CapabilityLane,
    blocks: List<MacroBlock>,
): OpenMacroDocument = when (lane) {
    CapabilityLane.TRIGGER -> copy(triggers = blocks)
    CapabilityLane.CONDITION -> copy(conditions = blocks)
    CapabilityLane.ACTION -> copy(actions = blocks)
}

private fun MacroConditionNode.mapBlocks(
    transform: (MacroBlock) -> MacroBlock,
): MacroConditionNode = when (this) {
    is MacroConditionNode.Condition -> copy(block = transform(block))
    is MacroConditionNode.All -> copy(children = children.map { it.mapBlocks(transform) })
    is MacroConditionNode.Any -> copy(children = children.map { it.mapBlocks(transform) })
    is MacroConditionNode.Not -> copy(child = child.mapBlocks(transform))
}

private fun MacroBlock.findBlock(blockId: String): MacroBlock? =
    takeIf { id == blockId } ?: nestedActionBlocks().firstNotNullOfOrNull {
        it.findBlock(blockId)
    }

private fun MacroConditionNode.findBlock(blockId: String): MacroBlock? = when (this) {
    is MacroConditionNode.Condition -> block.findBlock(blockId)
    is MacroConditionNode.All -> children.firstNotNullOfOrNull { it.findBlock(blockId) }
    is MacroConditionNode.Any -> children.firstNotNullOfOrNull { it.findBlock(blockId) }
    is MacroConditionNode.Not -> child.findBlock(blockId)
}

private fun MacroConditionNode.switchConditionGroup(
    tokens: List<String>,
    logic: ConditionGroupLogic,
): MacroConditionNode? {
    if (tokens.isEmpty()) {
        return when (this) {
            is MacroConditionNode.All -> when (logic) {
                ConditionGroupLogic.AND -> this
                ConditionGroupLogic.OR -> MacroConditionNode.Any(children)
            }
            is MacroConditionNode.Any -> when (logic) {
                ConditionGroupLogic.AND -> MacroConditionNode.All(children)
                ConditionGroupLogic.OR -> this
            }
            is MacroConditionNode.Condition,
            is MacroConditionNode.Not -> null
        }
    }
    val head = tokens.first()
    val tail = tokens.drop(1)
    return when (this) {
        is MacroConditionNode.All -> switchIndexedChild(head, tail, logic) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Any -> switchIndexedChild(head, tail, logic) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Not -> if (head == "not") {
            child.switchConditionGroup(tail, logic)?.let { copy(child = it) }
        } else {
            null
        }
        is MacroConditionNode.Condition -> null
    }
}

private fun MacroConditionNode.addConditionGroupChild(
    tokens: List<String>,
    child: MacroBlock,
): MacroConditionNode? {
    if (tokens.isEmpty()) {
        return when (this) {
            is MacroConditionNode.All -> copy(
                children = children + MacroConditionNode.Condition(child),
            )
            is MacroConditionNode.Any -> copy(
                children = children + MacroConditionNode.Condition(child),
            )
            is MacroConditionNode.Condition,
            is MacroConditionNode.Not -> null
        }
    }
    val head = tokens.first()
    val tail = tokens.drop(1)
    return when (this) {
        is MacroConditionNode.All -> addToIndexedChild(head, tail, child) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Any -> addToIndexedChild(head, tail, child) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Not -> if (head == "not") {
            this.child.addConditionGroupChild(tail, child)?.let { copy(child = it) }
        } else {
            null
        }
        is MacroConditionNode.Condition -> null
    }
}

private fun MacroConditionNode.addToIndexedChild(
    token: String,
    tail: List<String>,
    child: MacroBlock,
    rebuild: (List<MacroConditionNode>) -> MacroConditionNode,
): MacroConditionNode? {
    val children = when (this) {
        is MacroConditionNode.All -> children
        is MacroConditionNode.Any -> children
        else -> return null
    }
    val index = token.toIntOrNull() ?: return null
    if (index !in children.indices) {
        return null
    }
    val updatedChild = children[index].addConditionGroupChild(tail, child) ?: return null
    return rebuild(
        children.toMutableList().apply {
            set(index, updatedChild)
        },
    )
}

private fun MacroConditionNode.removeConditionTreeChild(
    tokens: List<String>,
    onRejected: (String) -> Unit,
): MacroConditionNode? {
    if (tokens.isEmpty()) {
        return null
    }
    val head = tokens.first()
    val tail = tokens.drop(1)
    return when (this) {
        is MacroConditionNode.All -> removeIndexedChild(head, tail, onRejected) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Any -> removeIndexedChild(head, tail, onRejected) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Not -> if (head == "not") {
            child.removeConditionTreeChild(tail, onRejected)?.let { copy(child = it) }
        } else {
            null
        }
        is MacroConditionNode.Condition -> null
    }
}

private fun MacroConditionNode.removeIndexedChild(
    token: String,
    tail: List<String>,
    onRejected: (String) -> Unit,
    rebuild: (List<MacroConditionNode>) -> MacroConditionNode,
): MacroConditionNode? {
    val children = when (this) {
        is MacroConditionNode.All -> children
        is MacroConditionNode.Any -> children
        else -> return null
    }
    val index = token.toIntOrNull() ?: return null
    if (index !in children.indices) {
        return null
    }
    if (tail.isEmpty()) {
        if (children.size <= 1) {
            onRejected("Condition groups must keep at least one child.")
            return this
        }
        return rebuild(
            children.toMutableList().apply {
                removeAt(index)
            },
        )
    }
    val updatedChild = children[index].removeConditionTreeChild(tail, onRejected)
        ?: return null
    return rebuild(
        children.toMutableList().apply {
            set(index, updatedChild)
        },
    )
}

private fun transformConditionTreeChild(
    document: OpenMacroDocument,
    childPath: String,
    transform: (MacroConditionNode) -> ChildTransformResult,
): BlockEditResult {
    val tree = document.conditionTree
        ?: return BlockEditResult.Rejected("This macro does not use a condition tree.")
    val tokens = childPath.pathTokens()
        ?: return BlockEditResult.Rejected("Condition child path is invalid.")
    if (tokens.isEmpty()) {
        return BlockEditResult.Rejected("The root condition group cannot be changed here.")
    }
    var rejection: String? = null
    val updated = tree.transformConditionTreeChild(tokens) { child ->
        when (val result = transform(child)) {
            is ChildTransformResult.Changed -> result.node
            is ChildTransformResult.Rejected -> {
                rejection = result.message
                child
            }
        }
    }
    rejection?.let {
        return BlockEditResult.Rejected(it)
    }
    return if (updated == null) {
        BlockEditResult.NotFound(childPath)
    } else {
        BlockEditResult.Updated(
            document.copy(conditionTree = updated),
        )
    }
}

private fun MacroConditionNode.transformConditionTreeChild(
    tokens: List<String>,
    transform: (MacroConditionNode) -> MacroConditionNode,
): MacroConditionNode? {
    if (tokens.isEmpty()) {
        return null
    }
    val head = tokens.first()
    val tail = tokens.drop(1)
    return when (this) {
        is MacroConditionNode.All -> transformIndexedChild(head, tail, transform) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Any -> transformIndexedChild(head, tail, transform) { updated ->
            copy(children = updated)
        }
        is MacroConditionNode.Not -> if (head == "not") {
            child.transformConditionTreeChild(tail, transform)?.let { copy(child = it) }
        } else {
            null
        }
        is MacroConditionNode.Condition -> null
    }
}

private fun MacroConditionNode.transformIndexedChild(
    token: String,
    tail: List<String>,
    transform: (MacroConditionNode) -> MacroConditionNode,
    rebuild: (List<MacroConditionNode>) -> MacroConditionNode,
): MacroConditionNode? {
    val children = when (this) {
        is MacroConditionNode.All -> children
        is MacroConditionNode.Any -> children
        else -> return null
    }
    val index = token.toIntOrNull() ?: return null
    if (index !in children.indices) {
        return null
    }
    val updatedChild = if (tail.isEmpty()) {
        transform(children[index])
    } else {
        children[index].transformConditionTreeChild(tail, transform) ?: return null
    }
    return rebuild(
        children.toMutableList().apply {
            set(index, updatedChild)
        },
    )
}

private fun MacroConditionNode.switchIndexedChild(
    token: String,
    tail: List<String>,
    logic: ConditionGroupLogic,
    rebuild: (List<MacroConditionNode>) -> MacroConditionNode,
): MacroConditionNode? {
    val children = when (this) {
        is MacroConditionNode.All -> children
        is MacroConditionNode.Any -> children
        else -> return null
    }
    val index = token.toIntOrNull() ?: return null
    if (index !in children.indices) {
        return null
    }
    val switched = children[index].switchConditionGroup(tail, logic) ?: return null
    return rebuild(
        children.toMutableList().apply {
            set(index, switched)
        },
    )
}

private fun String.pathTokens(): List<String>? {
    val parts = split(".")
    if (parts.firstOrNull() != "root") {
        return null
    }
    return parts.drop(1)
}

private fun Map<String, MacroValue>.updateNestedActionConfigs(
    blockId: String,
    key: String,
    value: MacroValue?,
    onMatch: () -> Unit,
): Map<String, MacroValue> {
    val actions = this["actions"] as? MacroValue.ListValue ?: return this
    val updatedActions = actions.copy(
        values = actions.values.map { nested ->
            nested.updateNestedActionConfig(blockId, key, value, onMatch)
        },
    )
    return toMutableMap().apply {
        put("actions", updatedActions)
    }
}

private fun MacroValue.updateNestedActionConfig(
    blockId: String,
    key: String,
    value: MacroValue?,
    onMatch: () -> Unit,
): MacroValue {
    val objectValue = this as? MacroValue.ObjectValue ?: return this
    val id = (objectValue.values["id"] as? MacroValue.Text)?.value
    val existingConfig = (objectValue.values["config"] as? MacroValue.ObjectValue)
        ?.values
        .orEmpty()
    val nestedConfig = existingConfig.updateNestedActionConfigs(
        blockId = blockId,
        key = key,
        value = value,
        onMatch = onMatch,
    )
    val updatedConfig = if (id == blockId) {
        onMatch()
        nestedConfig.toMutableMap().apply {
            if (value == null) remove(key) else put(key, value)
        }
    } else {
        nestedConfig
    }
    return objectValue.copy(
        values = objectValue.values.toMutableMap().apply {
            if (updatedConfig.isEmpty()) {
                remove("config")
            } else {
                put("config", MacroValue.ObjectValue(updatedConfig))
            }
        },
    )
}

private fun MacroBlock.nestedActionBlocks(): List<MacroBlock> =
    ((config["actions"] as? MacroValue.ListValue)?.values ?: emptyList())
        .mapNotNull { it.asNestedActionBlock() }

private fun MacroValue.asNestedActionBlock(): MacroBlock? {
    val values = (this as? MacroValue.ObjectValue)?.values ?: return null
    val id = (values["id"] as? MacroValue.Text)?.value ?: return null
    val type = (values["type"] as? MacroValue.Text)?.value ?: return null
    val config = (values["config"] as? MacroValue.ObjectValue)?.values.orEmpty()
    return MacroBlock(id = id, type = type, config = config)
}

private fun MacroBlock.toNestedActionValue(): MacroValue.ObjectValue =
    MacroValue.ObjectValue(
        buildMap {
            put("id", MacroValue.Text(id))
            put("type", MacroValue.Text(type))
            if (config.isNotEmpty()) {
                put("config", MacroValue.ObjectValue(config))
            }
        },
    )

private fun MacroConditionNode.conditionBlocks(): List<MacroBlock> = when (this) {
    is MacroConditionNode.Condition -> listOf(block)
    is MacroConditionNode.All -> children.flatMap { it.conditionBlocks() }
    is MacroConditionNode.Any -> children.flatMap { it.conditionBlocks() }
    is MacroConditionNode.Not -> child.conditionBlocks()
}

private fun Map<String, MacroValue>.referencesVariable(variableName: String): Boolean =
    values.any { it.referencesVariable(variableName) }

private fun MacroValue.referencesVariable(variableName: String): Boolean = when (this) {
    is MacroValue.ObjectValue -> {
        val directReference = values.size == 1 &&
            (values["variable"] as? MacroValue.Text)?.value == variableName
        directReference || values.any { (_, value) -> value.referencesVariable(variableName) }
    }
    is MacroValue.ListValue -> values.any { it.referencesVariable(variableName) }
    else -> false
}

private fun Map<String, MacroValue>.renameVariableReferences(
    oldName: String,
    newName: String,
): Map<String, MacroValue> =
    mapValues { (_, value) -> value.renameVariableReferences(oldName, newName) }

private fun MacroValue.renameVariableReferences(
    oldName: String,
    newName: String,
): MacroValue = when (this) {
    is MacroValue.ObjectValue -> {
        val directReference = values.size == 1 &&
            (values["variable"] as? MacroValue.Text)?.value == oldName
        if (directReference) {
            copy(values = mapOf("variable" to MacroValue.Text(newName)))
        } else {
            copy(values = values.renameVariableReferences(oldName, newName))
        }
    }
    is MacroValue.ListValue -> copy(
        values = values.map { it.renameVariableReferences(oldName, newName) },
    )
    else -> this
}

private fun uniqueId(base: String, existingIds: Set<String>): String {
    val normalized = base
        .lowercase()
        .replace(Regex("[^a-z0-9-]"), "-")
        .trim('-')
        .ifBlank { "grouped-action" }
        .take(MAX_BLOCK_ID_LENGTH)
        .trim('-')
        .ifBlank { "grouped-action" }
    if (normalized !in existingIds) {
        return normalized
    }
    var suffix = 2
    while (true) {
        val suffixText = "-$suffix"
        val candidate = normalized
            .take(MAX_BLOCK_ID_LENGTH - suffixText.length)
            .trim('-') + suffixText
        if (candidate !in existingIds) {
            return candidate
        }
        suffix += 1
    }
}

private fun uniqueVariableName(base: String, existingNames: Set<String>): String {
    val normalized = base
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .trim('_')
        .ifBlank { "value" }
        .let { if (it.first().isLetter()) it else "value_$it" }
        .take(MAX_VARIABLE_NAME_LENGTH)
        .trim('_')
        .ifBlank { "value" }
    if (normalized !in existingNames) {
        return normalized
    }
    var suffix = 2
    while (true) {
        val suffixText = "_$suffix"
        val candidate = normalized
            .take(MAX_VARIABLE_NAME_LENGTH - suffixText.length)
            .trim('_') + suffixText
        if (candidate !in existingNames) {
            return candidate
        }
        suffix += 1
    }
}

private const val ACTION_GROUP_TYPE = "openmacro.action.group"
private val VARIABLE_TARGET_ACTION_TYPES = setOf(
    "openmacro.variable.set",
    "openmacro.variable.increment",
    "openmacro.variable.toggle",
)
private val VARIABLE_NAME_PATTERN = Regex("^[a-z][a-z0-9_]{0,63}$")
private const val MAX_BLOCK_ID_LENGTH = 64
private const val MAX_VARIABLE_NAME_LENGTH = 64
