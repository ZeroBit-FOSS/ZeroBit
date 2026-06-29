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
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMapLocationActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesLocationQueryWithoutLocationPermission() {
        val result = compiler.compile(document(MacroValue.Text("India Gate, New Delhi")), "sha256:map")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.OpenMapLocation(
                blockId = "open-map-location",
                query = RuntimeValueSource.Literal(MacroValue.Text("India Gate, New Delhi")),
            ),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsControlCharactersAndUnknownUriFields() {
        val control = compiler.compile(document(MacroValue.Text("Delhi\ngeo:1,2")), "sha256:bad-map")
        require(control is PlanCompilationResult.Invalid)
        assertEquals(listOf("invalid_map_query"), control.issues.map { it.code })

        val uri = compiler.compile(
            document(
                MacroValue.Text("Delhi"),
                mapOf("uri" to MacroValue.Text("geo:1,2")),
            ),
            "sha256:map-uri",
        )
        require(uri is PlanCompilationResult.Invalid)
        assertEquals(listOf("unknown_config"), uri.issues.map { it.code })
    }

    private fun document(
        query: MacroValue,
        extra: Map<String, MacroValue> = emptyMap(),
    ) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("map", "Map"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(
            MacroBlock(
                id = "open-map-location",
                type = "android.map.open",
                config = mapOf("query" to query) + extra,
            ),
        ),
    )
}
