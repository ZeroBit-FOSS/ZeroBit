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
    fun filtersReferenceOptionsByNameOrType() {
        val options = listOf(
            ValueReferenceOption(
                label = "Variable: counter",
                type = MacroVariableType.NUMBER,
                value = MacroValue.ObjectValue(
                    mapOf("variable" to MacroValue.Text("counter")),
                ),
            ),
            ValueReferenceOption(
                label = "Trigger: notification.title",
                type = MacroVariableType.TEXT,
                value = MacroValue.ObjectValue(
                    mapOf("trigger" to MacroValue.Text("notification.title")),
                ),
            ),
        )

        assertEquals(options, filterValueReferenceOptions(options, "  "))
        assertEquals(
            listOf("Variable: counter"),
            filterValueReferenceOptions(options, "COUNTER").map { it.label },
        )
        assertEquals(
            listOf("Trigger: notification.title"),
            filterValueReferenceOptions(options, "text").map { it.label },
        )
        assertTrue(filterValueReferenceOptions(options, "missing").isEmpty())
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

    @Test
    fun exposesBoundedIntentActionFields() {
        val shareAction = MacroBlock(
            id = "share",
            type = "android.intent.share-text",
            config = mapOf(
                "package" to MacroValue.Text("com.example.chat"),
                "text" to MacroValue.ObjectValue(
                    mapOf("variable" to MacroValue.Text("message")),
                ),
            ),
        )
        val detailsAction = MacroBlock(
            id = "details",
            type = "android.app.details",
            config = mapOf("package" to MacroValue.Text("com.example.chat")),
        )
        val notificationSettingsAction = MacroBlock(
            id = "notification-settings",
            type = "android.app.notification-settings",
            config = mapOf("package" to MacroValue.Text("com.example.chat")),
        )
        val document = OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("forms", "Forms"),
            variables = listOf(
                MacroVariable("message", MacroVariableType.TEXT),
                MacroVariable("count", MacroVariableType.NUMBER),
            ),
            triggers = emptyList(),
            conditions = emptyList(),
            actions = listOf(shareAction, detailsAction, notificationSettingsAction),
        )

        val shareForm = checkNotNull(factory.create(document, shareAction))
        val detailsForm = checkNotNull(factory.create(document, detailsAction))
        val notificationSettingsForm =
            checkNotNull(factory.create(document, notificationSettingsAction))
        val shareText = shareForm.fields.single { it.key == "text" }

        assertEquals("Share text with app", shareForm.title)
        assertEquals(listOf("package", "text"), shareForm.fields.map { it.key })
        assertTrue(shareText.acceptsValueSources)
        assertEquals(
            listOf("Variable: message"),
            shareText.referenceOptions.map { it.label },
        )
        assertEquals("Open app details", detailsForm.title)
        assertEquals(listOf("package"), detailsForm.fields.map { it.key })
        assertTrue(detailsForm.fields.single().referenceOptions.isEmpty())
        assertEquals("Open notification settings", notificationSettingsForm.title)
        assertEquals(
            listOf("package"),
            notificationSettingsForm.fields.map { it.key },
        )
        assertTrue(notificationSettingsForm.fields.single().referenceOptions.isEmpty())
    }

    @Test
    fun exposesActionGroupFailurePolicyAsBoundedChoice() {
        val groupAction = MacroBlock(
            id = "group",
            type = "openmacro.action.group",
            config = mapOf(
                "failurePolicy" to MacroValue.Text("continue"),
                "actions" to MacroValue.ListValue(
                    listOf(
                        MacroValue.ObjectValue(
                            mapOf(
                                "id" to MacroValue.Text("log"),
                                "type" to MacroValue.Text("android.log.write"),
                                "config" to MacroValue.ObjectValue(
                                    mapOf("message" to MacroValue.Text("inside")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val document = OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("forms", "Forms"),
            triggers = emptyList(),
            conditions = emptyList(),
            actions = listOf(groupAction),
        )

        val form = checkNotNull(factory.create(document, groupAction))
        val field = form.fields.single()

        assertEquals("failurePolicy", field.key)
        assertEquals(listOf("stop", "continue"), field.allowedValues)
        assertEquals(MacroValue.Text("continue"), field.currentValue)
    }
}
