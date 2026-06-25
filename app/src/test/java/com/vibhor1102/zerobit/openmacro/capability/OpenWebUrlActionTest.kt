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
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenWebUrlActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExplicitHttpsAddress() {
        val result = compiler.compile(
            document("https://example.com/path?q=value"),
            "sha256:web",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.OpenWebUrl(
                "open",
                "https://example.com/path?q=value",
            ),
            result.plan.actions.single(),
        )
    }

    @Test
    fun rejectsNonWebSchemesCredentialsAndHostlessAddresses() {
        listOf(
            "intent://example.com",
            "https://user:pass@example.com",
            "https:///missing-host",
        ).forEach { url ->
            val result = compiler.compile(document(url), "sha256:$url")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_web_url"), result.issues.map { it.code })
        }
    }

    private fun document(url: String) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("web", "Web"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                "open",
                "android.web.open",
                mapOf("url" to MacroValue.Text(url)),
            ),
        ),
    )
}
