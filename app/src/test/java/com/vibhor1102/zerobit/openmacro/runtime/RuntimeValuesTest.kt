/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.storage.FakeSecretStore
import com.vibhor1102.zerobit.openmacro.storage.InMemoryVariableStore
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeValuesTest {
    private val variableStore = InMemoryVariableStore()
    private val secretStore = FakeSecretStore()
    private val values = RuntimeValues(
        macroId = "macro",
        declarations = listOf(
            MacroVariable("count", MacroVariableType.NUMBER),
            MacroVariable(
                name = "api_token",
                type = MacroVariableType.SECRET,
                secretKey = "accounts.primary",
            ),
        ),
        variables = variableStore,
        secrets = secretStore,
    )

    @Test
    fun readsAndWritesOnlyDeclaredTypedVariables() {
        assertEquals(
            RuntimeValueWriteResult.Written,
            values.write("count", MacroValue.Number(BigDecimal("2"))),
        )
        assertEquals(
            RuntimeValueResult.Available(MacroValue.Number(BigDecimal("2"))),
            values.read("count"),
        )
        assertEquals(
            RuntimeValueWriteResult.TypeMismatch(
                name = "count",
                expected = MacroVariableType.NUMBER,
                actual = "text",
            ),
            values.write("count", MacroValue.Text("two")),
        )
        assertEquals(
            RuntimeValueWriteResult.Unknown("undeclared"),
            values.write("undeclared", MacroValue.Boolean(true)),
        )
    }

    @Test
    fun resolvesDeclaredSecretWithoutExposingItsStorageKey() {
        secretStore.setSecret("accounts.primary", "super-secret")

        assertEquals(
            RuntimeValueResult.Available(MacroValue.Text("super-secret")),
            values.read("api_token"),
        )
        assertEquals(
            RuntimeValueWriteResult.ReadOnlySecret("api_token"),
            values.write("api_token", MacroValue.Text("replacement")),
        )
        assertEquals(
            RuntimeValueResult.Unknown("accounts.primary"),
            values.read("accounts.primary"),
        )
    }

    @Test
    fun executesFocusedVariableMutationActions() {
        val context = RuntimeContext(
            macroId = "macro",
            runId = 1,
            triggerBlockId = "manual-test",
            trigger = RuntimeTriggerEvent(),
            values = values,
        )
        values.write("count", MacroValue.Number(BigDecimal("2")))

        assertEquals(
            ActionResult.Succeeded,
            executeVariableAction(
                RuntimeStep.IncrementVariable(
                    blockId = "increment",
                    name = "count",
                    amount = BigDecimal("3"),
                ),
                context,
            ),
        )
        assertEquals(
            RuntimeValueResult.Available(MacroValue.Number(BigDecimal("5"))),
            values.read("count"),
        )
        assertEquals(
            ActionResult.Failed("Secret variable 'api_token' is read-only at runtime."),
            executeVariableAction(
                RuntimeStep.SetVariable(
                    blockId = "replace-secret",
                    name = "api_token",
                    value = RuntimeValueSource.Literal(MacroValue.Text("unsafe")),
                ),
                context,
            ),
        )
    }

    @Test
    fun resolvesTriggerAndVariableSourcesAtExecutionTime() {
        val context = RuntimeContext(
            macroId = "macro",
            runId = 2,
            triggerBlockId = "battery-trigger",
            trigger = RuntimeTriggerEvent(
                mapOf("battery.percentage" to MacroValue.Number(BigDecimal("19"))),
            ),
            values = values,
        )
        values.write("count", MacroValue.Number(BigDecimal("4")))

        assertEquals(
            RuntimeValueSourceResult.Available(MacroValue.Number(BigDecimal("19"))),
            RuntimeValueSource.Trigger("battery.percentage").resolve(context),
        )
        assertEquals(
            RuntimeValueSourceResult.Available(MacroValue.Number(BigDecimal("4"))),
            RuntimeValueSource.Variable("count").resolve(context),
        )
        assertEquals(
            RuntimeValueSourceResult.Missing(
                "Trigger 'battery-trigger' did not provide field 'screen.state'.",
            ),
            RuntimeValueSource.Trigger("screen.state").resolve(context),
        )
    }

    @Test
    fun evaluatesNumericTextAndPresenceConditionsDeterministically() {
        val context = RuntimeContext(
            macroId = "macro",
            runId = 3,
            triggerBlockId = "battery",
            trigger = RuntimeTriggerEvent(
                mapOf(
                    "battery.percentage" to MacroValue.Number(BigDecimal("19")),
                    "message" to MacroValue.Text("hello world"),
                ),
            ),
            values = values,
        )

        assertEquals(
            ConditionResult.Passed,
            evaluateValueCondition(
                RuntimeStep.CompareValues(
                    blockId = "below",
                    left = RuntimeValueSource.Trigger("battery.percentage"),
                    operator = ValueComparisonOperator.LESS_THAN,
                    right = RuntimeValueSource.Literal(
                        MacroValue.Number(BigDecimal("20.0")),
                    ),
                ),
                context,
            ),
        )
        assertEquals(
            ConditionResult.Passed,
            evaluateValueCondition(
                RuntimeStep.CompareValues(
                    blockId = "contains",
                    left = RuntimeValueSource.Trigger("message"),
                    operator = ValueComparisonOperator.CONTAINS,
                    right = RuntimeValueSource.Literal(MacroValue.Text("world")),
                ),
                context,
            ),
        )
        assertEquals(
            ConditionResult.Passed,
            evaluateValueCondition(
                RuntimeStep.CompareValues(
                    blockId = "missing",
                    left = RuntimeValueSource.Variable("api_token"),
                    operator = ValueComparisonOperator.IS_MISSING,
                    right = null,
                ),
                context,
            ),
        )
    }
}
