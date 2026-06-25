/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.capability.builtin.NotificationShowAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CapabilityRegistryTest {
    @Test
    fun listsCapabilitiesByVisualLane() {
        val registry = CapabilityRegistry.builtIn()

        assertEquals(
            listOf("Battery level", "Power connected", "Screen turned off", "Screen turned on"),
            registry.list(CapabilityLane.TRIGGER).map { it.displayName },
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
}
