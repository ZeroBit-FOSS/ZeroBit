/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.capability.builtin.NotificationShowAction
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CapabilityRegistryTest {
    @Test
    fun listsCapabilitiesByVisualLane() {
        val registry = CapabilityRegistry.builtIn()

        assertEquals(
            listOf(
                "Airplane mode changed",
                "Battery level",
                "Battery saver changed",
                "Notification received",
                "Power connected",
                "Power disconnected",
                "Ringer mode changed",
                "Screen turned off",
                "Screen turned on",
                "Time schedule",
                "Wi-Fi connected",
                "Wi-Fi disconnected",
            ),
            registry.list(CapabilityLane.TRIGGER).map { it.displayName },
        )
        assertEquals(
            listOf(
                "Airplane mode",
                "Battery charging",
                "Battery level",
                "Battery saver",
                "Device lock state",
                "Power connection",
                "Ringer mode",
                "Screen state",
                "Time window",
                "Value comparison",
                "Wi-Fi connected",
            ),
            registry.list(CapabilityLane.CONDITION).map { it.displayName },
        )
        assertEquals(
            listOf(
                "Action group",
                "Compose email",
                "Copy text to clipboard",
                "Create calendar event draft",
                "Create contact draft",
                "Dial number",
                "Increment variable",
                "Launch app",
                "Open Bluetooth settings",
                "Open location settings",
                "Open NFC settings",
                "Open Wi-Fi settings",
                "Open accessibility settings",
                "Open app details",
                "Open battery optimization settings",
                "Open data usage settings",
                "Open display settings",
                "Open map location",
                "Open notification settings",
                "Open privacy settings",
                "Open security settings",
                "Open sound settings",
                "Open web page",
                "Send SMS",
                "Set alarm",
                "Set timer",
                "Set variable",
                "Share text with app",
                "Show alarms",
                "Show notification",
                "Stop actions",
                "Stop actions if",
                "Toggle variable",
                "Vibrate",
                "Wait",
                "Write log",
            ),
            registry.list(CapabilityLane.ACTION).map { it.displayName },
        )
        assertSame(
            NotificationShowAction,
            registry.find("android.notification.show"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateCapabilityTypes() {
        CapabilityRegistry.of(
            NotificationShowAction,
            NotificationShowAction,
        )
    }

    @Test
    fun visualCreationIsCapabilityOwnedAndOptIn() {
        val registry = CapabilityRegistry.builtIn()

        assertEquals("battery-level", registry.find("android.battery.level")?.creation?.idBase)
        assertEquals(
            "Automation ran",
            (registry.find("android.notification.show")
                ?.creation
                ?.defaultConfig
                ?.get("message") as? MacroValue.Text)
                ?.value,
        )
        assertEquals(
            listOf("phoneNumber", "message"),
            registry.find("android.sms.send")?.creation?.setup?.fieldKeys,
        )
    }
}
