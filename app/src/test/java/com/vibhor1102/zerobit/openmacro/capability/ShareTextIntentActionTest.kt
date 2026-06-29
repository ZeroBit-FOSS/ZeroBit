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

class ShareTextIntentActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesPackageTargetedTextShare() {
        val result = compiler.compile(
            document(
                packageName = "com.example.chat",
                text = MacroValue.Text("Hello from ZeroBit"),
            ),
            "sha256:share",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.ShareTextIntent(
                blockId = "share",
                packageName = "com.example.chat",
                text = RuntimeValueSource.Literal(MacroValue.Text("Hello from ZeroBit")),
            ),
            result.plan.actions.single(),
        )
    }

    @Test
    fun supportsTextFromDeclaredVariable() {
        val result = compiler.compile(
            document(
                variables = listOf(
                    MacroVariable(
                        name = "message",
                        type = MacroVariableType.TEXT,
                        initialValue = MacroValue.Text("Saved message"),
                    ),
                ),
                packageName = "com.example.chat",
                text = reference("variable", "message"),
            ),
            "sha256:share-variable",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeValueSource.Variable("message"),
            (result.plan.actions.single() as RuntimeStep.ShareTextIntent).text,
        )
    }

    @Test
    fun rejectsUnsafePackageNames() {
        val result = compiler.compile(
            document(
                packageName = "not a package",
                text = MacroValue.Text("Hello"),
            ),
            "sha256:bad-package",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("invalid_package_name"), result.issues.map { it.code })
    }

    @Test
    fun rejectsNumberReferenceWhereSharedTextIsRequired() {
        val result = compiler.compile(
            document(
                variables = listOf(
                    MacroVariable(
                        name = "count",
                        type = MacroVariableType.NUMBER,
                        initialValue = MacroValue.Number(BigDecimal.ONE),
                    ),
                ),
                packageName = "com.example.chat",
                text = reference("variable", "count"),
            ),
            "sha256:wrong-reference",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf("value_reference_type_mismatch"),
            result.issues.map { it.code },
        )
    }

    private fun document(
        packageName: String,
        text: MacroValue,
        variables: List<MacroVariable> = emptyList(),
    ) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("share", "Share"),
        variables = variables,
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "share",
                type = "android.intent.share-text",
                config = mapOf(
                    "package" to MacroValue.Text(packageName),
                    "text" to text,
                ),
            ),
        ),
    )

    private fun reference(kind: String, name: String) =
        MacroValue.ObjectValue(mapOf(kind to MacroValue.Text(name)))
}
