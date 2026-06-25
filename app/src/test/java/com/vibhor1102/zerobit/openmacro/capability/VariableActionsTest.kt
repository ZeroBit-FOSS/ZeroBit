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
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class VariableActionsTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesTypedVariableMutationActions() {
        val result = compiler.compile(validDocument(), "sha256:variables")

        require(result is PlanCompilationResult.Success)
        assertEquals(
            listOf(
                RuntimeStep.SetVariable(
                    "set-name",
                    "name",
                    RuntimeValueSource.Literal(MacroValue.Text("ZeroBit")),
                ),
                RuntimeStep.IncrementVariable("increment-count", "count", BigDecimal("2")),
                RuntimeStep.ToggleVariable("toggle-enabled", "enabled"),
            ),
            result.plan.actions,
        )
    }

    @Test
    fun rejectsUnknownWrongTypeAndSecretMutation() {
        val document = validDocument().copy(
            actions = listOf(
                RuntimeStepFixture.set("unknown", "missing", MacroValue.Text("x")),
                RuntimeStepFixture.set("wrong-type", "count", MacroValue.Text("x")),
                RuntimeStepFixture.set("secret", "token", MacroValue.Text("x")),
            ),
        )

        val result = compiler.compile(document, "sha256:invalid")

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf(
                "unknown_variable",
                "variable_type_mismatch",
                "secret_is_read_only",
            ),
            result.issues.map { it.code },
        )
    }

    @Test
    fun compilesValidatedVariableAndTriggerReferences() {
        val document = validDocument().copy(
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
            actions = listOf(
                RuntimeStepFixture.setReference(
                    id = "copy-count",
                    name = "count",
                    kind = "trigger",
                    reference = "battery.percentage",
                ),
                RuntimeStepFixture.setReference(
                    id = "copy-token",
                    name = "name",
                    kind = "variable",
                    reference = "token",
                ),
            ),
        )

        val result = compiler.compile(document, "sha256:references")

        require(result is PlanCompilationResult.Success)
        assertEquals(
            listOf(
                RuntimeStep.SetVariable(
                    "copy-count",
                    "count",
                    RuntimeValueSource.Trigger("battery.percentage"),
                ),
                RuntimeStep.SetVariable(
                    "copy-token",
                    "name",
                    RuntimeValueSource.Variable("token"),
                ),
            ),
            result.plan.actions,
        )
    }

    @Test
    fun rejectsUnavailableOrWrongTypeReferences() {
        val document = validDocument().copy(
            actions = listOf(
                RuntimeStepFixture.setReference(
                    id = "missing-trigger",
                    name = "count",
                    kind = "trigger",
                    reference = "battery.percentage",
                ),
                RuntimeStepFixture.setReference(
                    id = "wrong-type",
                    name = "enabled",
                    kind = "variable",
                    reference = "count",
                ),
            ),
        )

        val result = compiler.compile(document, "sha256:bad-references")

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf("unknown_trigger_field", "variable_type_mismatch"),
            result.issues.map { it.code },
        )
    }

    private fun validDocument() = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("variables", "Variables"),
        variables = listOf(
            MacroVariable("name", MacroVariableType.TEXT, MacroValue.Text("")),
            MacroVariable("count", MacroVariableType.NUMBER, MacroValue.Number(BigDecimal.ZERO)),
            MacroVariable("enabled", MacroVariableType.BOOLEAN, MacroValue.Boolean(false)),
            MacroVariable("token", MacroVariableType.SECRET, secretKey = "account.token"),
        ),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            RuntimeStepFixture.set("set-name", "name", MacroValue.Text("ZeroBit")),
            MacroBlock(
                id = "increment-count",
                type = "openmacro.variable.increment",
                config = mapOf(
                    "name" to MacroValue.Text("count"),
                    "amount" to MacroValue.Number(BigDecimal("2")),
                ),
            ),
            MacroBlock(
                id = "toggle-enabled",
                type = "openmacro.variable.toggle",
                config = mapOf("name" to MacroValue.Text("enabled")),
            ),
        ),
    )

    private object RuntimeStepFixture {
        fun set(id: String, name: String, value: MacroValue) = MacroBlock(
            id = id,
            type = "openmacro.variable.set",
            config = mapOf(
                "name" to MacroValue.Text(name),
                "value" to value,
            ),
        )

        fun setReference(
            id: String,
            name: String,
            kind: String,
            reference: String,
        ) = set(
            id = id,
            name = name,
            value = MacroValue.ObjectValue(
                mapOf(kind to MacroValue.Text(reference)),
            ),
        )
    }
}
