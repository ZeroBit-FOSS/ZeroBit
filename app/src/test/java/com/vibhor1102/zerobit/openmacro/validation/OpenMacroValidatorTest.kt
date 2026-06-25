/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.validation

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMacroValidatorTest {
    @Test
    fun acceptsSmallValidMacro() {
        val document = validDocument()

        assertTrue(OpenMacroValidator.validate(document).isEmpty())
    }

    @Test
    fun rejectsDuplicateIdsAcrossSections() {
        val document = validDocument().copy(
            conditions = listOf(
                MacroBlock(
                    id = "show-message",
                    type = "android.device.unlocked",
                ),
            ),
        )

        val issues = OpenMacroValidator.validate(document)

        assertEquals(
            listOf("duplicate_block_id"),
            issues.map { it.code },
        )
    }

    @Test
    fun reportsMissingRequiredExecutionParts() {
        val document = validDocument().copy(
            triggers = emptyList(),
            actions = emptyList(),
        )

        val issues = OpenMacroValidator.validate(document)

        assertEquals(
            listOf("missing_trigger", "missing_action"),
            issues.map { it.code },
        )
    }

    @Test
    fun rejectsAmbiguousOrDuplicateVariableDeclarations() {
        val document = validDocument().copy(
            variables = listOf(
                MacroVariable(
                    name = "Battery Level",
                    type = MacroVariableType.NUMBER,
                ),
                MacroVariable(
                    name = "token",
                    type = MacroVariableType.SECRET,
                    secretKey = "Accounts/Primary",
                ),
                MacroVariable(
                    name = "token",
                    type = MacroVariableType.TEXT,
                ),
            ),
        )

        val issues = OpenMacroValidator.validate(document)

        assertEquals(
            listOf(
                "invalid_variable_name",
                "invalid_secret_key",
                "duplicate_variable_name",
            ),
            issues.map { it.code },
        )
    }

    @Test
    fun rejectsMixingFlatConditionsWithAConditionTree() {
        val document = validDocument().copy(
            conditions = listOf(
                MacroBlock("flat-condition", "android.device.unlocked"),
            ),
            conditionTree = MacroConditionNode.Condition(
                MacroBlock("tree-condition", "android.device.unlocked"),
            ),
        )

        val issues = OpenMacroValidator.validate(document)

        assertEquals(listOf("mixed_condition_forms"), issues.map { it.code })
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
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "show-message",
                type = "android.notification.show",
            ),
        ),
    )
}
