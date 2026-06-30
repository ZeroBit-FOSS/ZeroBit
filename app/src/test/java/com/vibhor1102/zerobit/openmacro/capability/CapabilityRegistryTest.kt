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
                "Battery temperature",
                "Bluetooth state changed",
                "Dark theme changed",
                "Location services changed",
                "NFC state changed",
                "Notification received",
                "Power connected",
                "Power disconnected",
                "Ringer mode changed",
                "Screen orientation changed",
                "Screen turned off",
                "Screen turned on",
                "Time schedule",
                "Wi-Fi connected",
                "Wi-Fi disconnected",
                "Wired headset changed",
            ),
            registry.list(CapabilityLane.TRIGGER).map { it.displayName },
        )
        assertEquals(
            listOf(
                "Airplane mode",
                "Battery charging",
                "Battery level",
                "Battery saver",
                "Battery temperature",
                "Bluetooth state",
                "Dark theme",
                "Device lock state",
                "Location services",
                "Media volume",
                "NFC state",
                "Power connection",
                "Ringer mode",
                "Screen orientation",
                "Screen state",
                "Time window",
                "Value comparison",
                "Wi-Fi connected",
                "Wired headset",
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
                "Open Airplane mode settings",
                "Open All files access settings",
                "Open Bluetooth settings",
                "Open Default apps settings",
                "Open Developer options",
                "Open Do Not Disturb access settings",
                "Open NFC settings",
                "Open Notification listener settings",
                "Open Usage access settings",
                "Open VPN settings",
                "Open Wi-Fi settings",
                "Open Wireless settings",
                "Open accessibility settings",
                "Open app all files access settings",
                "Open app details",
                "Open app language settings",
                "Open app notification bubble settings",
                "Open app overlay settings",
                "Open app picture-in-picture settings",
                "Open app unknown sources settings",
                "Open apps settings",
                "Open battery optimization settings",
                "Open data usage settings",
                "Open date & time settings",
                "Open display settings",
                "Open keyboard settings",
                "Open languages settings",
                "Open location settings",
                "Open map location",
                "Open notification settings",
                "Open privacy settings",
                "Open security settings",
                "Open sound settings",
                "Open storage settings",
                "Open system notification settings",
                "Open web page",
                "Send SMS",
                "Set alarm",
                "Set media volume",
                "Set timer",
                "Set torch",
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
