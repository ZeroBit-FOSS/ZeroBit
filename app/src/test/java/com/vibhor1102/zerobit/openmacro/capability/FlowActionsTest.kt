/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
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

    private fun document(actions: List<MacroBlock>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("flow", "Flow"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = actions,
    )
}
