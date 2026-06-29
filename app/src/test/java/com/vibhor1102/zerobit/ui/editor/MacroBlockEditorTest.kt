/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.ConditionGroupLogic
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.validation.OpenMacroValidator
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MacroBlockEditorTest {
    @Test
    fun addsVariablesFromBoundedTemplatesWithUniqueNames() {
        val document = document().copy(
            variables = listOf(
                MacroVariable(
                    name = "text_value",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text("existing"),
                ),
            ),
        )

        val textResult = MacroBlockEditor.addVariable(
            document = document,
            template = VariableDeclarationTemplate.TEXT,
        )
        require(textResult is BlockEditResult.Updated)
        assertEquals(
            listOf("text_value", "text_value_2"),
            textResult.document.variables.map { it.name },
        )
        assertEquals(MacroVariableType.TEXT, textResult.document.variables.last().type)
        assertEquals(MacroValue.Text(""), textResult.document.variables.last().initialValue)

        val secretResult = MacroBlockEditor.addVariable(
            document = textResult.document,
            template = VariableDeclarationTemplate.SECRET,
        )
        require(secretResult is BlockEditResult.Updated)
        val secret = secretResult.document.variables.last()
        assertEquals("secret_value", secret.name)
        assertEquals(MacroVariableType.SECRET, secret.type)
        assertEquals("secret.value", secret.secretKey)
    }

    @Test
    fun removesUnreferencedVariableDeclaration() {
        val document = document().copy(
            variables = listOf(
                MacroVariable(
                    name = "unused",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text("safe to remove"),
                ),
                MacroVariable(
                    name = "kept",
                    type = MacroVariableType.NUMBER,
                    initialValue = MacroValue.Number(java.math.BigDecimal.ONE),
                ),
            ),
        )

        val result = MacroBlockEditor.removeVariable(document, "unused")

        require(result is BlockEditResult.Updated)
        assertEquals(listOf("kept"), result.document.variables.map { it.name })
        assertEquals(document.actions, result.document.actions)
    }

    @Test
    fun refusesToRemoveVariableUsedByActionsOrValueReferences() {
        val targetDocument = document().copy(
            variables = listOf(
                MacroVariable(
                    name = "counter",
                    type = MacroVariableType.NUMBER,
                    initialValue = MacroValue.Number(java.math.BigDecimal.ZERO),
                ),
            ),
            actions = listOf(
                MacroBlock(
                    id = "increment-counter",
                    type = "openmacro.variable.increment",
                    config = mapOf(
                        "name" to MacroValue.Text("counter"),
                        "amount" to MacroValue.Number(java.math.BigDecimal.ONE),
                    ),
                ),
            ),
        )

        val targetResult = MacroBlockEditor.removeVariable(targetDocument, "counter")

        require(targetResult is BlockEditResult.Rejected)
        assertTrue(targetResult.message.contains("increment-counter"))

        val referenceDocument = document().copy(
            variables = listOf(
                MacroVariable(
                    name = "message",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text("hello"),
                ),
            ),
            actions = listOf(
                MacroBlock(
                    id = "log-message",
                    type = "android.log.write",
                    config = mapOf(
                        "message" to MacroValue.ObjectValue(
                            mapOf("variable" to MacroValue.Text("message")),
                        ),
                    ),
                ),
            ),
        )

        val referenceResult = MacroBlockEditor.removeVariable(referenceDocument, "message")

        require(referenceResult is BlockEditResult.Rejected)
        assertTrue(referenceResult.message.contains("log-message"))
    }

    @Test
    fun renamesVariableDeclarationAndReferences() {
        val document = document().copy(
            variables = listOf(
                MacroVariable(
                    name = "message",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text("hello"),
                ),
            ),
            actions = listOf(
                MacroBlock(
                    id = "set-message",
                    type = "openmacro.variable.set",
                    config = mapOf(
                        "name" to MacroValue.Text("message"),
                        "value" to MacroValue.Text("next"),
                    ),
                ),
                MacroBlock(
                    id = "group",
                    type = "openmacro.action.group",
                    config = mapOf(
                        "failurePolicy" to MacroValue.Text("stop"),
                        "actions" to MacroValue.ListValue(
                            listOf(
                                nestedAction(
                                    id = "nested-log",
                                    type = "android.log.write",
                                    config = mapOf(
                                        "message" to MacroValue.ObjectValue(
                                            mapOf("variable" to MacroValue.Text("message")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = MacroBlockEditor.renameVariable(
            document = document,
            oldName = "message",
            newName = "status_text",
        )

        require(result is BlockEditResult.Updated)
        assertEquals(listOf("status_text"), result.document.variables.map { it.name })
        assertEquals(
            MacroValue.Text("status_text"),
            result.document.actions.first().config["name"],
        )
        val nested = MacroBlockEditor.nestedActions(result.document.actions.last()).single()
        assertEquals(
            MacroValue.ObjectValue(mapOf("variable" to MacroValue.Text("status_text"))),
            nested.config["message"],
        )
    }

    @Test
    fun rejectsInvalidOrDuplicateVariableRename() {
        val document = document().copy(
            variables = listOf(
                MacroVariable(
                    name = "message",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text("hello"),
                ),
                MacroVariable(
                    name = "other",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text("world"),
                ),
            ),
        )

        assertEquals(
            BlockEditResult.Rejected(
                "Variable names must start with a lowercase letter and use only lowercase letters, numbers, or underscores.",
            ),
            MacroBlockEditor.renameVariable(document, "message", "Bad Name"),
        )
        assertEquals(
            BlockEditResult.Rejected("Variable 'other' already exists."),
            MacroBlockEditor.renameVariable(document, "message", "other"),
        )
    }

    @Test
    fun switchesConditionTreeGroupLogicByPath() {
        val document = document().copy(
            conditionTree = MacroConditionNode.All(
                listOf(
                    MacroConditionNode.Condition(
                        MacroBlock("unlocked", "android.device.unlocked"),
                    ),
                    MacroConditionNode.Any(
                        listOf(
                            MacroConditionNode.Condition(
                                MacroBlock("wifi", "android.wifi.connected"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val rootResult = MacroBlockEditor.switchConditionGroup(
            document = document,
            groupPath = "root",
            logic = ConditionGroupLogic.OR,
        )

        require(rootResult is BlockEditResult.Updated)
        assertTrue(rootResult.document.conditionTree is MacroConditionNode.Any)

        val nestedResult = MacroBlockEditor.switchConditionGroup(
            document = rootResult.document,
            groupPath = "root.1",
            logic = ConditionGroupLogic.AND,
        )

        require(nestedResult is BlockEditResult.Updated)
        val root = nestedResult.document.conditionTree as MacroConditionNode.Any
        assertTrue(root.children[1] is MacroConditionNode.All)
    }

    @Test
    fun addsConditionTreeChildWithUniqueDefaultBlock() {
        val document = document().copy(
            conditionTree = MacroConditionNode.All(
                listOf(
                    MacroConditionNode.Condition(
                        MacroBlock("device-unlocked", "android.device.unlocked"),
                    ),
                    MacroConditionNode.Any(
                        listOf(
                            MacroConditionNode.Condition(
                                MacroBlock("wifi", "android.wifi.connected"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val child = requireNotNull(MacroBlockEditor.newConditionTreeChild(
            document = document,
            template = template("android.device.unlocked"),
        ))

        val result = MacroBlockEditor.addConditionTreeChild(
            document = document,
            groupPath = "root.1",
            child = child,
        )

        require(result is BlockEditResult.Updated)
        assertEquals("device-unlocked-2", child.id)
        val root = result.document.conditionTree as MacroConditionNode.All
        val nested = root.children[1] as MacroConditionNode.Any
        val added = nested.children.last() as MacroConditionNode.Condition
        assertEquals("device-unlocked-2", added.block.id)
        assertEquals("android.device.unlocked", added.block.type)
    }

    @Test
    fun conditionTreePickerUsesEveryAvailableValidatedConditionStarter() {
        val registry = CapabilityRegistry.builtIn()
        val document = document()
        val templates = MacroBlockEditor.conditionTreeChildTemplates(
            registry,
            document,
            "root",
        )

        assertEquals(
            MacroBlockEditor.topLevelTemplates(registry, CapabilityLane.CONDITION, document),
            templates,
        )
        templates.forEach { template ->
            val child = requireNotNull(MacroBlockEditor.newConditionTreeChild(document, template))
            val added = MacroBlockEditor.addConditionTreeChild(document, "root", child)
            require(added is BlockEditResult.Updated)
            assertEquals(
                emptyList<ValidationIssue>(),
                OpenMacroValidator.validate(added.document, registry),
            )
        }
        assertEquals(
            null,
            MacroBlockEditor.newConditionTreeChild(
                document,
                template("android.notification.show"),
            ),
        )
    }

    @Test
    fun removesConditionTreeChildButKeepsGroupNonEmpty() {
        val document = document().copy(
            conditionTree = MacroConditionNode.All(
                listOf(
                    MacroConditionNode.Condition(
                        MacroBlock("first", "android.device.unlocked"),
                    ),
                    MacroConditionNode.Condition(
                        MacroBlock("second", "android.wifi.connected"),
                    ),
                ),
            ),
        )

        val result = MacroBlockEditor.removeConditionTreeChild(document, "root.0")

        require(result is BlockEditResult.Updated)
        val root = result.document.conditionTree as MacroConditionNode.All
        assertEquals(1, root.children.size)
        val remaining = root.children.single() as MacroConditionNode.Condition
        assertEquals("second", remaining.block.id)

        assertEquals(
            BlockEditResult.Rejected("Condition groups must keep at least one child."),
            MacroBlockEditor.removeConditionTreeChild(result.document, "root.0"),
        )
    }

    @Test
    fun wrapsAndUnwrapsConditionTreeChildInNot() {
        val document = document().copy(
            conditionTree = MacroConditionNode.All(
                listOf(
                    MacroConditionNode.Condition(
                        MacroBlock("first", "android.device.unlocked"),
                    ),
                ),
            ),
        )

        val wrapped = MacroBlockEditor.wrapConditionTreeChildInNot(document, "root.0")

        require(wrapped is BlockEditResult.Updated)
        val wrappedRoot = wrapped.document.conditionTree as MacroConditionNode.All
        val notNode = wrappedRoot.children.single() as MacroConditionNode.Not
        val wrappedCondition = notNode.child as MacroConditionNode.Condition
        assertEquals("first", wrappedCondition.block.id)

        val unwrapped = MacroBlockEditor.unwrapConditionTreeNot(wrapped.document, "root.0")

        require(unwrapped is BlockEditResult.Updated)
        val unwrappedRoot = unwrapped.document.conditionTree as MacroConditionNode.All
        val condition = unwrappedRoot.children.single() as MacroConditionNode.Condition
        assertEquals("first", condition.block.id)
    }

    @Test
    fun updatesNestedConditionWithoutChangingOtherBlocks() {
        val document = document()

        val result = MacroBlockEditor.updateConfig(
            document = document,
            blockId = "nested",
            key = "ssid",
            value = MacroValue.Text("Home"),
        )

        require(result is BlockEditResult.Updated)
        val root = result.document.conditionTree as MacroConditionNode.Not
        val nested = (root.child as MacroConditionNode.Condition).block
        assertEquals(MacroValue.Text("Home"), nested.config["ssid"])
        assertEquals(document.actions, result.document.actions)
    }

    @Test
    fun removesOptionalConfigKeyAndReportsMissingBlock() {
        val document = document().copy(
            actions = listOf(
                MacroBlock(
                    id = "log",
                    type = "android.log.write",
                    config = mapOf("message" to MacroValue.Text("hello")),
                ),
            ),
        )
        val removed = MacroBlockEditor.updateConfig(document, "log", "message", null)
        require(removed is BlockEditResult.Updated)
        assertTrue(removed.document.actions.single().config.isEmpty())

        assertEquals(
            BlockEditResult.NotFound("missing"),
            MacroBlockEditor.updateConfig(document, "missing", "message", null),
        )
    }

    @Test
    fun findsAndUpdatesNestedActionGroupChild() {
        val document = document().copy(
            actions = listOf(
                MacroBlock(
                    id = "group",
                    type = "openmacro.action.group",
                    config = mapOf(
                        "failurePolicy" to MacroValue.Text("stop"),
                        "actions" to MacroValue.ListValue(
                            listOf(
                                nestedAction(
                                    id = "child-log",
                                    type = "android.log.write",
                                    config = mapOf(
                                        "message" to MacroValue.Text("before"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            "android.log.write",
            MacroBlockEditor.findBlock(document, "child-log")?.type,
        )

        val result = MacroBlockEditor.updateConfig(
            document = document,
            blockId = "child-log",
            key = "message",
            value = MacroValue.Text("after"),
        )

        require(result is BlockEditResult.Updated)
        val group = result.document.actions.single()
        val child = MacroBlockEditor.nestedActions(group).single()
        assertEquals(MacroValue.Text("after"), child.config["message"])
        assertEquals(document.triggers, result.document.triggers)
    }

    @Test
    fun reportsAmbiguousNestedActionGroupChildIds() {
        val document = document().copy(
            actions = listOf(
                MacroBlock(
                    id = "group",
                    type = "openmacro.action.group",
                    config = mapOf(
                        "failurePolicy" to MacroValue.Text("stop"),
                        "actions" to MacroValue.ListValue(
                            listOf(
                                nestedAction("dup", "android.log.write"),
                                nestedAction("dup", "android.log.write"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            BlockEditResult.Ambiguous("dup", 2),
            MacroBlockEditor.updateConfig(
                document = document,
                blockId = "dup",
                key = "message",
                value = MacroValue.Text("after"),
            ),
        )
    }

    @Test
    fun addsGroupedActionsFromBoundedTemplatesWithUniqueIds() {
        val document = groupedDocument(
            nestedAction("group-log", "android.log.write"),
        )

        val logResult = MacroBlockEditor.addGroupedAction(
            document = document,
            groupBlockId = "group",
            template = actionTemplate(document, "android.log.write"),
        )

        require(logResult is BlockEditResult.Updated)
        val children = MacroBlockEditor.nestedActions(logResult.document.actions.single())
        assertEquals(listOf("group-log", "group-write-log"), children.map { it.id })
        assertEquals("android.log.write", children.last().type)
        assertEquals(
            MacroValue.Text("Automation ran"),
            children.last().config["message"],
        )

        val waitResult = MacroBlockEditor.addGroupedAction(
            document = logResult.document,
            groupBlockId = "group",
            template = actionTemplate(logResult.document, "openmacro.flow.delay"),
        )
        require(waitResult is BlockEditResult.Updated)
        val wait = MacroBlockEditor.nestedActions(waitResult.document.actions.single()).last()
        assertEquals("group-delay", wait.id)
        assertEquals("openmacro.flow.delay", wait.type)
        assertEquals(
            MacroValue.Number(java.math.BigDecimal("1000")),
            wait.config["milliseconds"],
        )
    }

    @Test
    fun groupedActionPickerUsesEveryAvailableValidatedActionStarter() {
        val registry = CapabilityRegistry.builtIn()
        val document = groupedDocument(nestedAction("existing", "openmacro.flow.stop"))
        val groupedTemplates = MacroBlockEditor.groupedActionTemplates(
            registry,
            document,
            "group",
        )
        val topLevelTemplates = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
        )

        assertEquals(topLevelTemplates, groupedTemplates)
        groupedTemplates.filterNot { it.setupRequired }.forEach { template ->
            val added = MacroBlockEditor.addGroupedAction(document, "group", template)
            require(added is BlockEditResult.Updated)
            assertEquals(
                emptyList<ValidationIssue>(),
                OpenMacroValidator.validate(added.document, registry),
            )
        }
        assertEquals(
            BlockEditResult.Rejected("Only actions can be added to an action group."),
            MacroBlockEditor.addGroupedAction(
                document,
                "group",
                template("android.power.connected"),
            ),
        )
    }

    @Test
    fun actionGroupStarterRespectsNestedDepthAndUsesUniqueChildIds() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(
            actions = listOf(
                MacroBlock(
                    id = "level-1",
                    type = "openmacro.action.group",
                    config = groupConfig(
                        nestedAction(
                            id = "level-2",
                            type = "openmacro.action.group",
                            config = groupConfig(
                                nestedAction(
                                    id = "level-3",
                                    type = "openmacro.action.group",
                                    config = groupConfig(
                                        nestedAction(
                                            id = "level-4",
                                            type = "openmacro.action.group",
                                            config = groupConfig(
                                                nestedAction("group-step", "openmacro.flow.stop"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val levelThree = MacroBlockEditor.groupedActionTemplates(
            registry,
            document,
            "level-3",
        ).single { it.type == "openmacro.action.group" }
        val child = (levelThree.defaultConfig.getValue("actions") as MacroValue.ListValue)
            .values.single() as MacroValue.ObjectValue
        assertEquals(MacroValue.Text("group-step-2"), child.values["id"])
        val added = MacroBlockEditor.addGroupedAction(document, "level-3", levelThree)
        require(added is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(added.document, registry))

        val levelFourTypes = MacroBlockEditor.groupedActionTemplates(
            registry,
            document,
            "level-4",
        ).map { it.type }
        assertTrue("openmacro.action.group" !in levelFourTypes)
    }

    @Test
    fun movesAndRemovesGroupedActionsWithoutTouchingOtherLanes() {
        val document = groupedDocument(
            nestedAction(
                id = "first",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("first")),
            ),
            nestedAction(
                id = "second",
                type = "openmacro.flow.stop",
            ),
        )

        val moved = MacroBlockEditor.moveGroupedAction(
            document = document,
            childBlockId = "second",
            direction = NestedActionMoveDirection.UP,
        )
        require(moved is BlockEditResult.Updated)
        assertEquals(
            listOf("second", "first"),
            MacroBlockEditor.nestedActions(moved.document.actions.single()).map { it.id },
        )

        val removed = MacroBlockEditor.removeGroupedAction(moved.document, "first")
        require(removed is BlockEditResult.Updated)
        assertEquals(
            listOf("second"),
            MacroBlockEditor.nestedActions(removed.document.actions.single()).map { it.id },
        )
        assertEquals(document.triggers, removed.document.triggers)
    }

    @Test
    fun refusesStructureEditsThatWouldBreakGroupShape() {
        val document = groupedDocument(
            nestedAction("only-child", "openmacro.flow.stop"),
        )

        assertEquals(
            BlockEditResult.Rejected("Action groups must keep at least one child action."),
            MacroBlockEditor.removeGroupedAction(document, "only-child"),
        )
        assertEquals(
            BlockEditResult.Rejected("Grouped action is already at that edge."),
            MacroBlockEditor.moveGroupedAction(
                document = document,
                childBlockId = "only-child",
                direction = NestedActionMoveDirection.UP,
            ),
        )
    }

    @Test
    fun addsBoundedTopLevelBlocksWithUniqueIds() {
        val document = document().copy(conditionTree = null)

        val trigger = MacroBlockEditor.addTopLevelBlock(
            document,
            template("android.power.connected"),
        )
        require(trigger is BlockEditResult.Updated)
        assertEquals(
            listOf("power", "power-connected"),
            trigger.document.triggers.map { it.id },
        )

        val condition = MacroBlockEditor.addTopLevelBlock(
            trigger.document,
            template("android.device.unlocked"),
        )
        require(condition is BlockEditResult.Updated)
        assertEquals("android.device.unlocked", condition.document.conditions.single().type)

        val action = MacroBlockEditor.addTopLevelBlock(
            condition.document,
            template("android.notification.show"),
        )
        require(action is BlockEditResult.Updated)
        assertEquals(
            MacroValue.Text("Automation ran"),
            action.document.actions.last().config["message"],
        )
    }

    @Test
    fun everyPublishedCapabilityStarterProducesValidDocument() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val templates = CapabilityLane.values().flatMap { lane ->
            MacroBlockEditor.topLevelTemplates(registry, lane, document)
        }.filterNot { it.setupRequired }

        assertEquals(30, templates.size)
        templates.forEach { template ->
            val result = MacroBlockEditor.addTopLevelBlock(document, template)
            require(result is BlockEditResult.Updated)
            assertEquals(
                emptyList<ValidationIssue>(),
                OpenMacroValidator.validate(result.document, registry),
            )
        }
    }

    @Test
    fun documentAwareStartersUseCompatibleVariablesAndHideWhenUnavailable() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(
            conditionTree = null,
            variables = listOf(
                MacroVariable(
                    name = "text_value",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text(""),
                ),
                MacroVariable(
                    name = "number_value",
                    type = MacroVariableType.NUMBER,
                    initialValue = MacroValue.Number(java.math.BigDecimal.ZERO),
                ),
                MacroVariable(
                    name = "boolean_value",
                    type = MacroVariableType.BOOLEAN,
                    initialValue = MacroValue.Boolean(false),
                ),
                MacroVariable(
                    name = "secret_value",
                    type = MacroVariableType.SECRET,
                    secretKey = "secret.value",
                ),
            ),
        )
        val templates = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
        ).associateBy { it.type }

        assertEquals(
            MacroValue.Text("text_value"),
            templates.getValue("openmacro.variable.set").defaultConfig["name"],
        )
        assertEquals(
            MacroValue.Text("number_value"),
            templates.getValue("openmacro.variable.increment").defaultConfig["name"],
        )
        assertEquals(
            MacroValue.Text("boolean_value"),
            templates.getValue("openmacro.variable.toggle").defaultConfig["name"],
        )
        templates.values.filterNot { it.setupRequired }.forEach { template ->
            val added = MacroBlockEditor.addTopLevelBlock(document, template)
            require(added is BlockEditResult.Updated)
            assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(added.document, registry))
        }

        val secretOnly = document.copy(variables = document.variables.takeLast(1))
        val unavailable = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            secretOnly,
        ).map { it.type }
        assertTrue("openmacro.variable.set" !in unavailable)
        assertTrue("openmacro.variable.increment" !in unavailable)
        assertTrue("openmacro.variable.toggle" !in unavailable)
    }

    @Test
    fun filtersCapabilityStartersByNameDescriptionOrType() {
        val templates = MacroBlockEditor.topLevelTemplates(
            CapabilityRegistry.builtIn(),
            CapabilityLane.ACTION,
            document().copy(conditionTree = null),
        )

        assertEquals(templates, MacroBlockEditor.filterTopLevelTemplates(templates, "  "))
        assertEquals(
            listOf("Open notification settings", "Show notification"),
            MacroBlockEditor.filterTopLevelTemplates(templates, "NOTIFICATION")
                .map { it.label },
        )
        assertEquals(
            listOf("Write log"),
            MacroBlockEditor.filterTopLevelTemplates(templates, "diagnostic")
                .map { it.label },
        )
        assertEquals(
            listOf("Delay"),
            MacroBlockEditor.filterTopLevelTemplates(templates, "openmacro.flow.delay")
                .map { it.label },
        )
        assertTrue(MacroBlockEditor.filterTopLevelTemplates(templates, "no-such-option").isEmpty())
    }

    @Test
    fun configuresOpenWebBeforeAllowingSourceInsertion() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val option = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
        ).single { it.type == "android.web.open" }

        assertTrue(option.setupRequired)
        assertEquals(listOf("url"), option.setupFields.map { it.key })
        assertEquals(
            BlockEditResult.Rejected("Finish capability setup before adding it."),
            MacroBlockEditor.addTopLevelBlock(document, option),
        )
        assertTrue(
            MacroBlockEditor.configureTemplate(
                registry,
                document,
                option,
                mapOf("url" to MacroValue.Text("not a web address")),
            ) is TemplateConfigurationResult.Rejected,
        )

        val configured = MacroBlockEditor.configureTemplate(
            registry,
            document,
            option,
            mapOf("url" to MacroValue.Text("https://example.com/path")),
        )
        require(configured is TemplateConfigurationResult.Configured)
        val added = MacroBlockEditor.addTopLevelBlock(document, configured.template)
        require(added is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(added.document, registry))
    }

    @Test
    fun packageActionsRequireValidatedSetupBeforeInsertion() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val options = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
        ).filter { it.setupRequired }
        assertEquals(
            listOf(
                "android.app.launch",
                "android.app.details",
                "android.app.notification-settings",
                "android.alarm.set",
                "android.calendar.event-draft",
                "android.email.compose",
                "android.intent.share-text",
                "android.map.open",
                "android.phone.dial",
                "android.sms.send",
                "android.timer.set",
                "android.web.open",
            ).sorted(),
            options.map { it.type }.sorted(),
        )

        options.filter { it.type.startsWith("android.app.") }.forEach { option ->
            assertTrue(
                MacroBlockEditor.configureTemplate(
                    registry,
                    document,
                    option,
                    mapOf("package" to MacroValue.Text("not a package")),
                ) is TemplateConfigurationResult.Rejected,
            )
            val configured = MacroBlockEditor.configureTemplate(
                registry,
                document,
                option,
                mapOf("package" to MacroValue.Text("com.example.app")),
            )
            require(configured is TemplateConfigurationResult.Configured)
            val added = MacroBlockEditor.addTopLevelBlock(document, configured.template)
            require(added is BlockEditResult.Updated)
            assertEquals(
                emptyList<ValidationIssue>(),
                OpenMacroValidator.validate(added.document, registry),
            )
        }
    }

    @Test
    fun textSourceActionsAcceptValidatedReferencesDuringSetup() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(
            conditionTree = null,
            variables = listOf(
                MacroVariable(
                    name = "message_text",
                    type = MacroVariableType.TEXT,
                    initialValue = MacroValue.Text("hello"),
                ),
            ),
        )
        val options = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
        ).associateBy { it.type }
        val reference = MacroValue.ObjectValue(
            mapOf("variable" to MacroValue.Text("message_text")),
        )

        val share = MacroBlockEditor.configureTemplate(
            registry,
            document,
            options.getValue("android.intent.share-text"),
            mapOf(
                "package" to MacroValue.Text("com.example.app"),
                "text" to reference,
            ),
        )
        require(share is TemplateConfigurationResult.Configured)
        val shareAdded = MacroBlockEditor.addTopLevelBlock(document, share.template)
        require(shareAdded is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(shareAdded.document, registry))

        val sms = MacroBlockEditor.configureTemplate(
            registry,
            document,
            options.getValue("android.sms.send"),
            mapOf(
                "phoneNumber" to MacroValue.Text("+15551234567"),
                "message" to reference,
            ),
        )
        require(sms is TemplateConfigurationResult.Configured)
        val smsAdded = MacroBlockEditor.addTopLevelBlock(document, sms.template)
        require(smsAdded is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(smsAdded.document, registry))

        val email = MacroBlockEditor.configureTemplate(
            registry,
            document,
            options.getValue("android.email.compose"),
            mapOf(
                "recipient" to MacroValue.Text("person@example.com"),
                "subject" to reference,
                "body" to reference,
            ),
        )
        require(email is TemplateConfigurationResult.Configured)
        val emailAdded = MacroBlockEditor.addTopLevelBlock(document, email.template)
        require(emailAdded is BlockEditResult.Updated)
        assertEquals(
            emptyList<ValidationIssue>(),
            OpenMacroValidator.validate(emailAdded.document, registry),
        )

        val map = MacroBlockEditor.configureTemplate(
            registry,
            document,
            options.getValue("android.map.open"),
            mapOf("query" to reference),
        )
        require(map is TemplateConfigurationResult.Configured)
        val mapAdded = MacroBlockEditor.addTopLevelBlock(document, map.template)
        require(mapAdded is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(mapAdded.document, registry))

        val calendar = MacroBlockEditor.configureTemplate(
            registry,
            document,
            options.getValue("android.calendar.event-draft"),
            mapOf(
                "start" to MacroValue.Text("2026-07-01T09:00"),
                "end" to MacroValue.Text("2026-07-01T10:00"),
                "timezone" to MacroValue.Text("Asia/Kolkata"),
                "title" to reference,
                "location" to reference,
                "description" to reference,
            ),
        )
        require(calendar is TemplateConfigurationResult.Configured)
        val calendarAdded = MacroBlockEditor.addTopLevelBlock(document, calendar.template)
        require(calendarAdded is BlockEditResult.Updated)
        assertEquals(
            emptyList<ValidationIssue>(),
            OpenMacroValidator.validate(calendarAdded.document, registry),
        )
    }

    @Test
    fun notificationTriggerSetupKeepsPackageOptionalAndCaptureBounded() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val option = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.TRIGGER,
            document,
        ).single { it.type == "android.notification.received" }

        assertTrue(option.setupRequired)
        assertEquals(null, option.defaultConfig["package"])
        assertEquals(
            MacroValue.ListValue(listOf(MacroValue.Text("title"))),
            option.defaultConfig["capture"],
        )
        val configured = MacroBlockEditor.configureTemplate(
            registry,
            document,
            option,
            option.defaultConfig + ("package" to MacroValue.Text("com.example.chat")),
        )
        require(configured is TemplateConfigurationResult.Configured)
        val added = MacroBlockEditor.addTopLevelBlock(document, configured.template)
        require(added is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(added.document, registry))

        assertTrue(
            MacroBlockEditor.configureTemplate(
                registry,
                document,
                option,
                mapOf("capture" to MacroValue.ListValue(emptyList())),
            ) is TemplateConfigurationResult.Rejected,
        )
    }

    @Test
    fun alarmSetupPublishesBoundedDefaultsAndOptionalLabel() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val option = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
        ).single { it.type == "android.alarm.set" }

        assertTrue(option.setupRequired)
        assertEquals(MacroValue.Number(BigDecimal("7")), option.defaultConfig["hour"])
        assertEquals(MacroValue.Number(BigDecimal.ZERO), option.defaultConfig["minute"])
        assertEquals(MacroValue.Boolean(false), option.defaultConfig["skipUi"])
        assertEquals(null, option.defaultConfig["label"])
        val configured = MacroBlockEditor.configureTemplate(
            registry,
            document,
            option,
            option.defaultConfig + ("label" to MacroValue.Text("Morning")),
        )
        require(configured is TemplateConfigurationResult.Configured)
        val added = MacroBlockEditor.addTopLevelBlock(document, configured.template)
        require(added is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(added.document, registry))
    }

    @Test
    fun timerSetupPublishesBoundedDefaultsAndOptionalLabel() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val option = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.ACTION,
            document,
        ).single { it.type == "android.timer.set" }

        assertTrue(option.setupRequired)
        assertEquals(MacroValue.Number(BigDecimal("300")), option.defaultConfig["seconds"])
        assertEquals(MacroValue.Boolean(false), option.defaultConfig["skipUi"])
        assertEquals(null, option.defaultConfig["label"])
        val configured = MacroBlockEditor.configureTemplate(
            registry,
            document,
            option,
            option.defaultConfig + ("label" to MacroValue.Text("Tea")),
        )
        require(configured is TemplateConfigurationResult.Configured)
        val added = MacroBlockEditor.addTopLevelBlock(document, configured.template)
        require(added is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(added.document, registry))
    }

    @Test
    fun scheduleSetupPublishesExplicitPortableDefaults() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val option = MacroBlockEditor.topLevelTemplates(
            registry,
            CapabilityLane.TRIGGER,
            document,
        ).single { it.type == "android.time.schedule" }

        assertTrue(option.setupRequired)
        assertEquals(MacroValue.Text("08:00"), option.defaultConfig["time"])
        assertEquals(MacroValue.Text("UTC"), option.defaultConfig["timezone"])
        assertEquals(MacroValue.Text("windowed"), option.defaultConfig["delivery"])
        assertEquals(
            MacroValue.Number(java.math.BigDecimal("15")),
            option.defaultConfig["window_minutes"],
        )
        val configured = MacroBlockEditor.configureTemplate(
            registry,
            document,
            option,
            option.defaultConfig,
        )
        require(configured is TemplateConfigurationResult.Configured)
        val added = MacroBlockEditor.addTopLevelBlock(document, configured.template)
        require(added is BlockEditResult.Updated)
        assertEquals(emptyList<ValidationIssue>(), OpenMacroValidator.validate(added.document, registry))

        assertTrue(
            MacroBlockEditor.configureTemplate(
                registry,
                document,
                option,
                option.defaultConfig + ("timezone" to MacroValue.Text("not/a-zone")),
            ) is TemplateConfigurationResult.Rejected,
        )
    }

    @Test
    fun setupAccessPreviewTracksConfiguredCapabilitySemantics() {
        val registry = CapabilityRegistry.builtIn()
        val document = document().copy(conditionTree = null)
        val options = CapabilityLane.values().flatMap { lane ->
            MacroBlockEditor.topLevelTemplates(registry, lane, document)
        }.associateBy { it.type }

        val sms = options.getValue("android.sms.send")
        assertEquals(
            setOf(com.vibhor1102.zerobit.openmacro.capability.AndroidPermission.SEND_SMS),
            MacroBlockEditor.setupRequiredPermissions(
                registry,
                document,
                sms,
                mapOf(
                    "phoneNumber" to MacroValue.Text("+15551234567"),
                    "message" to MacroValue.Text("Automation ran"),
                ),
            ),
        )

        val schedule = options.getValue("android.time.schedule")
        assertEquals(
            emptySet<com.vibhor1102.zerobit.openmacro.capability.AndroidPermission>(),
            MacroBlockEditor.setupRequiredPermissions(
                registry,
                document,
                schedule,
                schedule.defaultConfig,
            ),
        )
        assertEquals(
            setOf(
                com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
                    .SCHEDULE_EXACT_ALARM_ACCESS,
            ),
            MacroBlockEditor.setupRequiredPermissions(
                registry,
                document,
                schedule,
                schedule.defaultConfig + ("delivery" to MacroValue.Text("exact")),
            ),
        )
    }

    @Test
    fun removesAndReordersTopLevelBlocksButKeepsRequiredLanes() {
        val document = document().copy(
            triggers = listOf(
                MacroBlock("first", "android.power.connected"),
                MacroBlock("second", "android.screen.on"),
            ),
            conditionTree = null,
            conditions = listOf(MacroBlock("unlocked", "android.device.unlocked")),
        )

        val moved = MacroBlockEditor.moveTopLevelBlock(
            document,
            "second",
            NestedActionMoveDirection.UP,
        )
        require(moved is BlockEditResult.Updated)
        assertEquals(listOf("second", "first"), moved.document.triggers.map { it.id })

        val removed = MacroBlockEditor.removeTopLevelBlock(moved.document, "first")
        require(removed is BlockEditResult.Updated)
        assertEquals(listOf("second"), removed.document.triggers.map { it.id })
        assertEquals(
            BlockEditResult.Rejected("A macro must keep at least one trigger."),
            MacroBlockEditor.removeTopLevelBlock(removed.document, "second"),
        )
        assertTrue(
            MacroBlockEditor.removeTopLevelBlock(removed.document, "log")
                is BlockEditResult.Rejected,
        )
    }

    @Test
    fun directsConditionAddsToExistingConditionTree() {
        assertEquals(
            BlockEditResult.Rejected(
                "This macro uses a condition tree. Add conditions inside that tree.",
            ),
            MacroBlockEditor.addTopLevelBlock(
                document(),
                template("android.device.unlocked"),
            ),
        )
    }

    private fun document() = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("editor", "Editor"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        conditionTree = MacroConditionNode.Not(
            MacroConditionNode.Condition(
                MacroBlock(
                    id = "nested",
                    type = "android.wifi.connected",
                ),
            ),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("hello")),
            ),
        ),
    )

    private fun template(type: String): TopLevelBlockTemplate =
        CapabilityLane.values()
            .flatMap { lane ->
                MacroBlockEditor.topLevelTemplates(
                    CapabilityRegistry.builtIn(),
                    lane,
                    document().copy(conditionTree = null),
                )
            }
            .single { it.type == type }

    private fun actionTemplate(
        document: OpenMacroDocument,
        type: String,
    ): TopLevelBlockTemplate =
        MacroBlockEditor.groupedActionTemplates(
            CapabilityRegistry.builtIn(),
            document,
            "group",
        )
            .single { it.type == type }

    private fun groupedDocument(vararg children: MacroValue) = document().copy(
        actions = listOf(
            MacroBlock(
                id = "group",
                type = "openmacro.action.group",
                config = mapOf(
                    "failurePolicy" to MacroValue.Text("stop"),
                    "actions" to MacroValue.ListValue(children.toList()),
                ),
            ),
        ),
    )

    private fun nestedAction(
        id: String,
        type: String,
        config: Map<String, MacroValue> = emptyMap(),
    ) = MacroValue.ObjectValue(
        buildMap {
            put("id", MacroValue.Text(id))
            put("type", MacroValue.Text(type))
            if (config.isNotEmpty()) {
                put("config", MacroValue.ObjectValue(config))
            }
        },
    )

    private fun groupConfig(child: MacroValue): Map<String, MacroValue> = mapOf(
        "failurePolicy" to MacroValue.Text("stop"),
        "actions" to MacroValue.ListValue(listOf(child)),
    )
}
