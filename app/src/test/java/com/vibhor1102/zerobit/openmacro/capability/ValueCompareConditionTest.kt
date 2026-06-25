/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import com.vibhor1102.zerobit.openmacro.runtime.ValueComparisonOperator
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class ValueCompareConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesTypedNumericComparison() {
        val result = compiler.compile(
            document(
                operator = "greater_or_equal",
                right = MacroValue.Number(BigDecimal("20")),
            ),
            "sha256:comparison",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.CompareValues(
                blockId = "compare",
                left = RuntimeValueSource.Trigger("battery.percentage"),
                operator = ValueComparisonOperator.GREATER_OR_EQUAL,
                right = RuntimeValueSource.Literal(
                    MacroValue.Number(BigDecimal("20")),
                ),
            ),
            result.plan.conditions.single(),
        )
    }

    @Test
    fun compilesPresenceCheckWithoutRightValue() {
        val result = compiler.compile(
            document(operator = "is_present", right = null),
            "sha256:presence",
        )

        require(result is PlanCompilationResult.Success)
        val condition = result.plan.conditions.single() as RuntimeStep.CompareValues
        assertEquals(ValueComparisonOperator.IS_PRESENT, condition.operator)
        assertEquals(null, condition.right)
    }

    @Test
    fun rejectsComparisonWithIncompatibleTypes() {
        val result = compiler.compile(
            document(
                operator = "contains",
                right = MacroValue.Number(BigDecimal("20")),
            ),
            "sha256:wrong-comparison",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf("comparison_type_mismatch"),
            result.issues.map { it.code },
        )
    }

    private fun document(
        operator: String,
        right: MacroValue?,
    ): OpenMacroDocument {
        val config = linkedMapOf<String, MacroValue>(
            "left" to reference("trigger", "battery.percentage"),
            "operator" to MacroValue.Text(operator),
        )
        if (right != null) {
            config["right"] = right
        }
        return OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("comparison", "Comparison"),
            variables = listOf(
                MacroVariable(
                    "threshold",
                    MacroVariableType.NUMBER,
                    MacroValue.Number(BigDecimal("20")),
                ),
            ),
            triggers = listOf(
                MacroBlock(
                    id = "battery",
                    type = "android.battery.level",
                    config = mapOf(
                        "level" to MacroValue.Number(BigDecimal("20")),
                        "direction" to MacroValue.Text("goes_below"),
                    ),
                ),
            ),
            conditions = listOf(
                MacroBlock(
                    id = "compare",
                    type = "openmacro.value.compare",
                    config = config,
                ),
            ),
            actions = listOf(
                MacroBlock(
                    id = "log",
                    type = "android.log.write",
                    config = mapOf("message" to MacroValue.Text("matched")),
                ),
            ),
        )
    }

    private fun reference(kind: String, name: String) =
        MacroValue.ObjectValue(mapOf(kind to MacroValue.Text(name)))
}
