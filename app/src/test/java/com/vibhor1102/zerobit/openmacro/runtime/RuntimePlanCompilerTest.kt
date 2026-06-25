/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.validation.OpenMacroValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePlanCompilerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesValidatedDocumentAndDiscoversPermissions() {
        val result = compiler.compile(validDocument(), sourceFingerprint = "sha256:example")

        require(result is PlanCompilationResult.Success)
        assertEquals("charger-greeting", result.plan.macroId)
        assertEquals("sha256:example", result.plan.sourceFingerprint)
        assertEquals(
            setOf(AndroidPermission.POST_NOTIFICATIONS),
            result.plan.requiredPermissions,
        )
        assertTrue(result.plan.triggers.single() is RuntimeStep.ObservePowerConnected)
        assertTrue(result.plan.conditions.single() is RuntimeStep.CheckDeviceUnlocked)
        assertEquals(
            RuntimeStep.ShowNotification(
                blockId = "show-message",
                title = "Charging started",
                message = "The charger is connected.",
            ),
            result.plan.actions.single(),
        )
    }

    @Test
    fun refusesCapabilityPlacedInWrongLane() {
        val document = validDocument().copy(
            actions = listOf(
                MacroBlock(
                    id = "wrong-place",
                    type = "android.power.connected",
                ),
            ),
        )

        val issues = OpenMacroValidator.validate(document, registry)

        assertEquals(listOf("wrong_lane"), issues.map { it.code })
    }

    @Test
    fun refusesUnknownOrInvalidCapabilityConfiguration() {
        val document = validDocument().copy(
            actions = listOf(
                MacroBlock(
                    id = "show-message",
                    type = "android.notification.show",
                    config = mapOf(
                        "title" to MacroValue.Text("Charging started"),
                        "surprise" to MacroValue.Boolean(true),
                    ),
                ),
            ),
        )

        val result = compiler.compile(document, sourceFingerprint = "sha256:invalid")

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf("unknown_config", "missing_config"),
            result.issues.map { it.code },
        )
    }

    private fun validDocument() = OpenMacroDocument(
        format = OpenMacroValidator.SUPPORTED_FORMAT,
        metadata = MacroMetadata(
            id = "charger-greeting",
            name = "Charger greeting",
        ),
        triggers = listOf(
            MacroBlock(
                id = "charger-connected",
                type = "android.power.connected",
            ),
        ),
        conditions = listOf(
            MacroBlock(
                id = "device-is-unlocked",
                type = "android.device.unlocked",
            ),
        ),
        actions = listOf(
            MacroBlock(
                id = "show-message",
                type = "android.notification.show",
                config = mapOf(
                    "title" to MacroValue.Text("Charging started"),
                    "message" to MacroValue.Text("The charger is connected."),
                ),
            ),
        ),
    )
}
