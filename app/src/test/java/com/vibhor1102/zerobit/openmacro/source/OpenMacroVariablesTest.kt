/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class OpenMacroVariablesTest {

    @Test
    fun parsesValidVariablesAndTypes() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: test-variables
              name: Test Variables
            variables:
              - name: text_var
                type: text
                initial: "hello"
              - name: num_var
                type: number
                initial: 42.5
              - name: bool_var
                type: boolean
                initial: true
              - name: secret_var
                type: secret
                secret_key: "my_token"
            triggers: []
            actions: []
        """.trimIndent()

        val result = OpenMacroYamlReader.read(yaml)
        require(result is OpenMacroSourceResult.Success)

        val vars = result.source.document.variables
        assertEquals(4, vars.size)

        val textVar = vars[0]
        assertEquals("text_var", textVar.name)
        assertEquals(MacroVariableType.TEXT, textVar.type)
        assertEquals(MacroValue.Text("hello"), textVar.initialValue)
        assertNull(textVar.secretKey)

        val numVar = vars[1]
        assertEquals("num_var", numVar.name)
        assertEquals(MacroVariableType.NUMBER, numVar.type)
        assertEquals(MacroValue.Number(BigDecimal("42.5")), numVar.initialValue)
        assertNull(numVar.secretKey)

        val boolVar = vars[2]
        assertEquals("bool_var", boolVar.name)
        assertEquals(MacroVariableType.BOOLEAN, boolVar.type)
        assertEquals(MacroValue.Boolean(true), boolVar.initialValue)
        assertNull(boolVar.secretKey)

        val secretVar = vars[3]
        assertEquals("secret_var", secretVar.name)
        assertEquals(MacroVariableType.SECRET, secretVar.type)
        assertNull(secretVar.initialValue)
        assertEquals("my_token", secretVar.secretKey)
    }

    @Test
    fun rejectsSecretVariableWithInitialValue() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: test-variables
              name: Test Variables
            variables:
              - name: bad_secret
                type: secret
                secret_key: "key"
                initial: "no-initial-allowed"
            triggers: []
            actions: []
        """.trimIndent()

        val result = OpenMacroYamlReader.read(yaml)
        require(result is OpenMacroSourceResult.Failure)
        val issue = result.issues.single()
        assertEquals("invalid_secret_initial", issue.code)
    }

    @Test
    fun rejectsSecretVariableWithoutSecretKey() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: test-variables
              name: Test Variables
            variables:
              - name: bad_secret
                type: secret
            triggers: []
            actions: []
        """.trimIndent()

        val result = OpenMacroYamlReader.read(yaml)
        require(result is OpenMacroSourceResult.Failure)
        val issue = result.issues.single()
        assertEquals("missing_secret_key", issue.code)
    }

    @Test
    fun rejectsTextVariableWithSecretKey() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: test-variables
              name: Test Variables
            variables:
              - name: bad_text
                type: text
                secret_key: "some_key"
            triggers: []
            actions: []
        """.trimIndent()

        val result = OpenMacroYamlReader.read(yaml)
        require(result is OpenMacroSourceResult.Failure)
        val issue = result.issues.single()
        assertEquals("invalid_variable_secret_key", issue.code)
    }

    @Test
    fun rejectsTypeMismatchForInitialValue() {
        val yaml = """
            format: openmacro/v0.1
            metadata:
              id: test-variables
              name: Test Variables
            variables:
              - name: bad_type
                type: boolean
                initial: "not-a-bool"
            triggers: []
            actions: []
        """.trimIndent()

        val result = OpenMacroYamlReader.read(yaml)
        require(result is OpenMacroSourceResult.Failure)
        val issue = result.issues.single()
        assertEquals("type_mismatch", issue.code)
    }

    @Test
    fun writesVariablesToYamlCorrectly() {
        val yaml = """
            format: "openmacro/v0.1"

            metadata:
              id: "test-variables"
              name: "Test Variables"

            variables:
              - name: "text_var"
                type: "text"
                initial: "hello"
              - name: "secret_var"
                type: "secret"
                secret_key: "my_token"

            triggers: []

            conditions: []

            actions: []
        """.trimIndent() + "\n"

        val result = OpenMacroYamlReader.read(yaml)
        require(result is OpenMacroSourceResult.Success)

        val written = OpenMacroYamlWriter.write(result.source.document)
        assertEquals(yaml, written)
    }
}
