/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
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
                title = RuntimeValueSource.Literal(
                    MacroValue.Text("Charging started"),
                ),
                message = RuntimeValueSource.Literal(
                    MacroValue.Text("The charger is connected."),
                ),
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

    @Test
    fun compilesTarget4CapabilitiesAndDiscoversPermissions() {
        val document = OpenMacroDocument(
            format = OpenMacroValidator.SUPPORTED_FORMAT,
            metadata = MacroMetadata(
                id = "macro-target-4",
                name = "Test Target 4 Capabilities",
            ),
            triggers = listOf(
                MacroBlock(
                    id = "screen-on",
                    type = "android.screen.on",
                ),
                MacroBlock(
                    id = "screen-off",
                    type = "android.screen.off",
                ),
                MacroBlock(
                    id = "battery-level",
                    type = "android.battery.level",
                    config = mapOf(
                        "level" to MacroValue.Number(java.math.BigDecimal(20)),
                        "direction" to MacroValue.Text("goes_below"),
                    ),
                ),
            ),
            conditions = listOf(
                MacroBlock(
                    id = "wifi-connected",
                    type = "android.wifi.connected",
                    config = mapOf(
                        "ssid" to MacroValue.Text("MyHomeWifi"),
                    ),
                ),
            ),
            actions = listOf(
                MacroBlock(
                    id = "write-log",
                    type = "android.log.write",
                    config = mapOf(
                        "message" to MacroValue.Text("Running target 4 macro"),
                    ),
                ),
                MacroBlock(
                    id = "send-sms",
                    type = "android.sms.send",
                    config = mapOf(
                        "phoneNumber" to MacroValue.Text("+1234567890"),
                        "message" to MacroValue.Text("Hello from ZeroBit!"),
                    ),
                ),
            ),
        )

        val result = compiler.compile(document, sourceFingerprint = "sha256:target4")

        require(result is PlanCompilationResult.Success)
        assertEquals("macro-target-4", result.plan.macroId)
        assertEquals("sha256:target4", result.plan.sourceFingerprint)
        assertEquals(
            setOf(
                AndroidPermission.SEND_SMS,
                AndroidPermission.ACCESS_NETWORK_STATE
            ),
            result.plan.requiredPermissions,
        )

        // Triggers
        assertEquals(3, result.plan.triggers.size)
        assertTrue(result.plan.triggers[0] is RuntimeStep.ObserveScreenOn)
        assertTrue(result.plan.triggers[1] is RuntimeStep.ObserveScreenOff)
        val batteryTrigger = result.plan.triggers[2] as RuntimeStep.ObserveBatteryLevel
        assertEquals(20, batteryTrigger.level)
        assertEquals(BatteryDirection.GOES_BELOW, batteryTrigger.direction)

        // Conditions
        val wifiCondition = result.plan.conditions.single() as RuntimeStep.CheckWifiConnected
        assertEquals("MyHomeWifi", wifiCondition.ssid)

        // Actions
        assertEquals(2, result.plan.actions.size)
        val logAction = result.plan.actions[0] as RuntimeStep.WriteLog
        assertEquals(
            RuntimeValueSource.Literal(MacroValue.Text("Running target 4 macro")),
            logAction.message,
        )
        val smsAction = result.plan.actions[1] as RuntimeStep.SendSms
        assertEquals(
            RuntimeValueSource.Literal(MacroValue.Text("+1234567890")),
            smsAction.phoneNumber,
        )
        assertEquals(
            RuntimeValueSource.Literal(MacroValue.Text("Hello from ZeroBit!")),
            smsAction.message,
        )
    }

    @Test
    fun validatesTarget4ConfigurationErrors() {
        val document = OpenMacroDocument(
            format = OpenMacroValidator.SUPPORTED_FORMAT,
            metadata = MacroMetadata(
                id = "macro-target-4-invalid",
                name = "Invalid Target 4 Capabilities",
            ),
            triggers = listOf(
                MacroBlock(
                    id = "battery-level",
                    type = "android.battery.level",
                    config = mapOf(
                        "level" to MacroValue.Text("twenty"), // wrong type
                        "direction" to MacroValue.Text("somewhere"), // invalid value
                    ),
                ),
            ),
            conditions = listOf(
                MacroBlock(
                    id = "wifi-connected",
                    type = "android.wifi.connected",
                    config = mapOf(
                        "ssid" to MacroValue.Boolean(true), // wrong type
                    ),
                ),
            ),
            actions = listOf(
                MacroBlock(
                    id = "send-sms",
                    type = "android.sms.send",
                    config = mapOf(
                        "phoneNumber" to MacroValue.Number(java.math.BigDecimal(12345)), // wrong type
                        // missing message
                    ),
                ),
            ),
        )

        val result = compiler.compile(document, sourceFingerprint = "sha256:target4-invalid")

        require(result is PlanCompilationResult.Invalid)
        val codes = result.issues.map { it.code }
        
        // Assert that the compiler caught the validation errors
        assertTrue(codes.contains("wrong_config_type"))
        assertTrue(codes.contains("invalid_battery_direction"))
        assertTrue(codes.contains("missing_config"))
    }

    @Test
    fun compilesNestedConditionTree() {
        val document = validDocument().copy(
            conditions = emptyList(),
            conditionTree = MacroConditionNode.All(
                listOf(
                    MacroConditionNode.Condition(
                        MacroBlock(
                            id = "unlocked",
                            type = "android.device.unlocked",
                        ),
                    ),
                    MacroConditionNode.Any(
                        listOf(
                            MacroConditionNode.Condition(
                                MacroBlock(
                                    id = "home-wifi",
                                    type = "android.wifi.connected",
                                    config = mapOf(
                                        "ssid" to MacroValue.Text("Home"),
                                    ),
                                ),
                            ),
                            MacroConditionNode.Not(
                                MacroConditionNode.Condition(
                                    MacroBlock(
                                        id = "guest-wifi",
                                        type = "android.wifi.connected",
                                        config = mapOf(
                                            "ssid" to MacroValue.Text("Guest"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = compiler.compile(document, "sha256:tree")

        require(result is PlanCompilationResult.Success)
        val root = result.plan.conditionTree as RuntimeConditionNode.All
        assertTrue(root.children[0] is RuntimeConditionNode.Condition)
        val any = root.children[1] as RuntimeConditionNode.Any
        assertTrue(any.children[0] is RuntimeConditionNode.Condition)
        assertTrue(any.children[1] is RuntimeConditionNode.Not)
        assertEquals(
            setOf(AndroidPermission.POST_NOTIFICATIONS, AndroidPermission.ACCESS_NETWORK_STATE),
            result.plan.requiredPermissions,
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

