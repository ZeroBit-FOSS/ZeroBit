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
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardTextActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesLiteralAndDeclaredTextReference() {
        val literal = compiler.compile(
            document(MacroValue.Text("hello")),
            "sha256:clipboard-literal",
        )
        require(literal is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.CopyTextToClipboard(
                blockId = "copy",
                text = RuntimeValueSource.Literal(MacroValue.Text("hello")),
            ),
            literal.plan.actions.single(),
        )

        val reference = compiler.compile(
            document(
                MacroValue.ObjectValue(
                    mapOf("variable" to MacroValue.Text("clipboard_text")),
                ),
            ),
            "sha256:clipboard-reference",
        )
        require(reference is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.CopyTextToClipboard(
                blockId = "copy",
                text = RuntimeValueSource.Variable("clipboard_text"),
            ),
            reference.plan.actions.single(),
        )
    }

    @Test
    fun rejectsMissingAndOverLimitClipboardText() {
        listOf(
            null,
            MacroValue.Text("x".repeat(10_001)),
        ).forEachIndexed { index, value ->
            val result = compiler.compile(document(value), "sha256:invalid-clipboard-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(1, result.issues.size)
        }
    }

    private fun document(text: MacroValue?) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("clipboard-test", "Clipboard test"),
        variables = listOf(
            MacroVariable(
                name = "clipboard_text",
                type = MacroVariableType.TEXT,
                initialValue = MacroValue.Text("initial"),
            ),
        ),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "copy",
                type = "android.clipboard.set-text",
                config = if (text == null) emptyMap() else mapOf("text" to text),
            ),
        ),
    )
}
