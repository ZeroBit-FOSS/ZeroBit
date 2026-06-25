/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidScheduleAlarmDeliveryBrokerTest {
    @Test
    fun staleCancellationCannotRemoveReplacementCallback() {
        val deliveries = mutableListOf<String>()
        val first = AndroidScheduleAlarmDeliveryBroker.register("macro:block") {
            deliveries += "first"
        }
        val replacement = AndroidScheduleAlarmDeliveryBroker.register("macro:block") {
            deliveries += "replacement"
        }

        first.cancel()

        assertTrue(AndroidScheduleAlarmDeliveryBroker.deliver("macro:block"))
        assertEquals(listOf("replacement"), deliveries)

        replacement.cancel()
        assertFalse(AndroidScheduleAlarmDeliveryBroker.deliver("macro:block"))
    }
}
