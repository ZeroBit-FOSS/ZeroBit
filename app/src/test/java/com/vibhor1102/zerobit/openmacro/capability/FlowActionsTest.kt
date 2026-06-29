/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.ActionGroupFailurePolicy
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowActionsTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesBoundedDelayAndStopActions() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "wait",
                        type = "openmacro.flow.delay",
                        config = mapOf(
                            "milliseconds" to MacroValue.Number(BigDecimal("250")),
                        ),
                    ),
                    MacroBlock("stop", "openmacro.flow.stop"),
                    MacroBlock(
                        id = "stop-if",
                        type = "openmacro.flow.stop-if",
                        config = mapOf(
                            "left" to MacroValue.Boolean(true),
                            "operator" to MacroValue.Text("equals"),
                            "right" to MacroValue.Boolean(true),
                        ),
                    ),
                ),
            ),
            "sha256:flow",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            listOf(
                RuntimeStep.Delay("wait", 250),
                RuntimeStep.StopActions("stop"),
                RuntimeStep.StopIf(
                    blockId = "stop-if",
                    left = com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource.Literal(
                        MacroValue.Boolean(true),
                    ),
                    operator = com.vibhor1102.zerobit.openmacro.runtime.ValueComparisonOperator.EQUALS,
                    right = com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource.Literal(
                        MacroValue.Boolean(true),
                    ),
                ),
            ),
            result.plan.actions,
        )
    }

    @Test
    fun rejectsFractionalOrExcessiveDelay() {
        listOf("1.5", "86400001").forEach { duration ->
            val result = compiler.compile(
                document(
                    listOf(
                        MacroBlock(
                            id = "wait",
                            type = "openmacro.flow.delay",
                            config = mapOf(
                                "milliseconds" to MacroValue.Number(BigDecimal(duration)),
                            ),
                        ),
                    ),
                ),
                "sha256:$duration",
            )

            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_delay"), result.issues.map { it.code })
        }
    }

    @Test
    fun compilesNestedActionGroupWithExplicitFailurePolicy() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group",
                        type = "openmacro.action.group",
                        config = mapOf(
                            "failurePolicy" to MacroValue.Text("continue"),
                            "actions" to MacroValue.ListValue(
                                listOf(
                                    nestedAction(
                                        id = "log",
                                        type = "android.log.write",
                                        config = mapOf(
                                            "message" to MacroValue.Text("inside group"),
                                        ),
                                    ),
                                    nestedAction(
                                        id = "wait",
                                        type = "openmacro.flow.delay",
                                        config = mapOf(
                                            "milliseconds" to MacroValue.Number(BigDecimal("25")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "sha256:group",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.ActionGroup(
                blockId = "group",
                failurePolicy = ActionGroupFailurePolicy.CONTINUE,
                actions = listOf(
                    RuntimeStep.WriteLog("log", "inside group"),
                    RuntimeStep.Delay("wait", 25),
                ),
            ),
            result.plan.actions.single(),
        )
    }

    @Test
    fun nestedActionGroupContributesChildPermissions() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group",
                        type = "openmacro.action.group",
                        config = groupConfig(
                            nestedAction(
                                id = "sms",
                                type = "android.sms.send",
                                config = mapOf(
                                    "phoneNumber" to MacroValue.Text("+1234567890"),
                                    "message" to MacroValue.Text("inside group"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "sha256:group-permissions",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(setOf(AndroidPermission.SEND_SMS), result.plan.requiredPermissions)
    }

    @Test
    fun rejectsNestedBlocksThatAreNotActions() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group",
                        type = "openmacro.action.group",
                        config = groupConfig(
                            nestedAction("not-action", "android.power.connected"),
                        ),
                    ),
                ),
            ),
            "sha256:wrong-lane",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("wrong_lane"), result.issues.map { it.code })
    }

    @Test
    fun rejectsMalformedNestedActionTypeWithoutUnsupportedFollowUp() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group",
                        type = "openmacro.action.group",
                        config = groupConfig(
                            nestedAction("bad", "Not A Capability"),
                        ),
                    ),
                ),
            ),
            "sha256:bad-type",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("invalid_capability_type"), result.issues.map { it.code })
    }

    @Test
    fun rejectsDuplicateNestedActionIdsInsideOneGroup() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group",
                        type = "openmacro.action.group",
                        config = groupConfig(
                            nestedAction(
                                id = "log",
                                type = "android.log.write",
                                config = mapOf("message" to MacroValue.Text("first")),
                            ),
                            nestedAction(
                                id = "log",
                                type = "android.log.write",
                                config = mapOf("message" to MacroValue.Text("second")),
                            ),
                        ),
                    ),
                ),
            ),
            "sha256:duplicate-nested",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("duplicate_nested_action_id"), result.issues.map { it.code })
    }

    @Test
    fun rejectsDuplicateNestedActionIdsAcrossNestedGroups() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group",
                        type = "openmacro.action.group",
                        config = groupConfig(
                            nestedAction(
                                id = "left-group",
                                type = "openmacro.action.group",
                                config = groupConfig(
                                    nestedAction(
                                        id = "shared",
                                        type = "android.log.write",
                                        config = mapOf("message" to MacroValue.Text("left")),
                                    ),
                                ),
                            ),
                            nestedAction(
                                id = "right-group",
                                type = "openmacro.action.group",
                                config = groupConfig(
                                    nestedAction(
                                        id = "shared",
                                        type = "android.log.write",
                                        config = mapOf("message" to MacroValue.Text("right")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "sha256:duplicate-nested-deep",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("duplicate_nested_action_id"), result.issues.map { it.code })
    }

    @Test
    fun rejectsNestedActionIdsAlreadyUsedByTopLevelBlocks() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group",
                        type = "openmacro.action.group",
                        config = groupConfig(
                            nestedAction(
                                id = "power",
                                type = "android.log.write",
                                config = mapOf("message" to MacroValue.Text("shadow")),
                            ),
                        ),
                    ),
                ),
            ),
            "sha256:duplicate-top-level",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("duplicate_block_id"), result.issues.map { it.code })
    }

    @Test
    fun rejectsActionGroupsNestedTooDeeply() {
        val result = compiler.compile(
            document(
                listOf(
                    MacroBlock(
                        id = "group-1",
                        type = "openmacro.action.group",
                        config = groupConfig(
                            nestedAction(
                                id = "group-2",
                                type = "openmacro.action.group",
                                config = groupConfig(
                                    nestedAction(
                                        id = "group-3",
                                        type = "openmacro.action.group",
                                        config = groupConfig(
                                            nestedAction(
                                                id = "group-4",
                                                type = "openmacro.action.group",
                                                config = groupConfig(
                                                    nestedAction(
                                                        id = "group-5",
                                                        type = "openmacro.action.group",
                                                        config = groupConfig(
                                                            nestedAction("log", "android.log.write"),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "sha256:too-deep",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("action_group_too_deep"), result.issues.map { it.code })
    }

    private fun document(actions: List<MacroBlock>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("flow", "Flow"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = actions,
    )

    private fun groupConfig(vararg actions: MacroValue) = mapOf(
        "failurePolicy" to MacroValue.Text("stop"),
        "actions" to MacroValue.ListValue(actions.toList()),
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
}
