/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityFormModelTest {
    private val factory = CapabilityFormModelFactory(CapabilityRegistry.builtIn())

    @Test
    fun generatesValueFieldWithOnlyAvailableTypedReferences() {
        val condition = MacroBlock(
            id = "compare",
            type = "openmacro.value.compare",
            config = mapOf(
                "left" to MacroValue.ObjectValue(
                    mapOf("trigger" to MacroValue.Text("notification.title")),
                ),
                "operator" to MacroValue.Text("contains"),
                "right" to MacroValue.Text("urgent"),
            ),
        )
        val document = OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("forms", "Forms"),
            variables = listOf(
                MacroVariable("counter", MacroVariableType.NUMBER),
                MacroVariable("api_token", MacroVariableType.SECRET, secretKey = "api.token"),
            ),
            triggers = listOf(
                MacroBlock(
                    id = "notification",
                    type = "android.notification.received",
                    config = mapOf(
                        "capture" to MacroValue.ListValue(
                            listOf(MacroValue.Text("title")),
                        ),
                    ),
                ),
            ),
            conditions = listOf(condition),
            actions = listOf(
                MacroBlock(
                    id = "log",
                    type = "android.log.write",
                    config = mapOf("message" to MacroValue.Text("matched")),
                ),
            ),
        )

        val form = checkNotNull(factory.create(document, condition))
        val left = form.fields.single { it.key == "left" }

        assertEquals(CapabilityFieldKind.VALUE, left.kind)
        assertTrue(left.referenceOptions.any { it.label == "Variable: counter" })
        assertTrue(left.referenceOptions.any { it.label == "Secret: api_token" })
        assertTrue(left.referenceOptions.any { it.label == "Trigger: notification.title" })
        assertFalse(left.referenceOptions.any { it.label == "Trigger: notification.text" })
    }

    @Test
    fun returnsNullForUnsupportedCapabilityWithoutDiscardingTheBlock() {
        val document = OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("forms", "Forms"),
            triggers = emptyList(),
            conditions = emptyList(),
            actions = emptyList(),
        )
        val block = MacroBlock("future", "future.capability")

        assertEquals(null, factory.create(document, block))
    }

    @Test
    fun exposesBoundedChoicesForListAndTextFields() {
        val trigger = MacroBlock(
            id = "schedule",
            type = "android.time.schedule",
            config = mapOf(
                "time" to MacroValue.Text("08:00"),
                "days" to MacroValue.ListValue(listOf(MacroValue.Text("mon"))),
                "timezone" to MacroValue.Text("Asia/Kolkata"),
            ),
        )
        val document = OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("forms", "Forms"),
            triggers = listOf(trigger),
            conditions = emptyList(),
            actions = emptyList(),
        )

        val form = checkNotNull(factory.create(document, trigger))

        assertEquals(
            listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
            form.fields.single { it.key == "days" }.allowedValues,
        )
        assertEquals(
            listOf("windowed", "exact"),
            form.fields.single { it.key == "delivery" }.allowedValues,
        )
    }
}
