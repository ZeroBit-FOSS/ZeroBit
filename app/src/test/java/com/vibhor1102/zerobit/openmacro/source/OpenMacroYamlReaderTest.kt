/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMacroYamlReaderTest {
    @Test
    fun readsStrictYamlAndPreservesTheExactSource() {
        val text = """
            # This comment belongs to the user.
            format: openmacro/v0.1
            metadata:
              id: charger-greeting
              name: Charger greeting
            triggers:
              - id: charger-connected
                type: android.power.connected
            conditions: []
            actions:
              - id: show-message
                type: android.notification.show
                config:
                  title: Charging started
                  message: "The charger is connected."
                  attempts: 2
                  quiet: false
                  optional: null
                  labels:
                    - home
                    - phone
        """.trimIndent() + "\n"

        val result = OpenMacroYamlReader.read(text)

        require(result is OpenMacroSourceResult.Success)
        assertEquals(text, result.source.originalText)
        assertTrue(result.source.fingerprint.startsWith("sha256:"))
        assertEquals("charger-greeting", result.source.document.metadata.id)
        assertEquals(
            MacroValue.Number("2".toBigDecimal()),
            result.source.document.actions.single().config["attempts"],
        )
        assertEquals(
            MacroValue.ListValue(
                listOf(
                    MacroValue.Text("home"),
                    MacroValue.Text("phone"),
                ),
            ),
            result.source.document.actions.single().config["labels"],
        )
    }

    @Test
    fun rejectsYamlFeaturesOutsideTheOpenMacroSubset() {
        assertIssue(
            source = validSource().replace(
                "title: Charging started",
                "title: &shared Charging started\n      other: *shared",
            ),
            expectedCode = "anchor_not_allowed",
        )
        assertIssue(
            source = validSource().replace(
                "title: Charging started",
                "title: !custom Charging started",
            ),
            expectedCode = "tag_not_allowed",
        )
        assertIssue(
            source = validSource() + "---\nformat: openmacro/v0.1\n",
            expectedCode = "multiple_documents",
        )
        assertIssue(
            source = validSource().replace(
                "name: Charger greeting",
                "name: Charger greeting\n  name: Duplicate",
            ),
            expectedCode = "duplicate_key",
        )
        assertIssue(
            source = validSource().replace(
                "title: Charging started",
                "<<: {title: Other}\n      title: Charging started",
            ),
            expectedCode = "merge_key_not_allowed",
        )
        assertIssue(
            source = validSource().replace(
                "title: Charging started",
                "title: yes",
            ),
            expectedCode = "ambiguous_scalar",
        )
    }

    @Test
    fun rejectsUnknownStructureBeforeItCanBeSilentlyDiscarded() {
        val source = validSource().replace(
            "name: Charger greeting",
            "name: Charger greeting\n  mystery: hidden",
        )

        val result = OpenMacroYamlReader.read(source)

        require(result is OpenMacroSourceResult.Failure)
        assertEquals("unknown_key", result.issues.single().code)
        assertEquals("$.metadata.mystery", result.issues.single().path)
        assertEquals(5, result.issues.single().line)
    }

    @Test
    fun requiresTextForIdentityFields() {
        val source = validSource().replace(
            "id: charger-greeting",
            "id: 42",
        )

        assertIssue(source, expectedCode = "expected_text")
    }

    @Test
    fun boundsSourceSizeAndNestingBeforeDecoding() {
        val oversized = "x".repeat(OpenMacroYamlReader.MAX_SOURCE_CODE_POINTS + 1)
        val oversizedResult = OpenMacroYamlReader.read(oversized)
        require(oversizedResult is OpenMacroSourceResult.Failure)
        assertEquals("source_too_large", oversizedResult.issues.single().code)

        val nestedValue = buildString {
            repeat(OpenMacroYamlReader.MAX_NESTING_DEPTH + 1) { append("[") }
            append("null")
            repeat(OpenMacroYamlReader.MAX_NESTING_DEPTH + 1) { append("]") }
        }
        val deeplyNested = validSource().replace(
            "title: Charging started",
            "title: $nestedValue",
        )
        assertIssue(deeplyNested, expectedCode = "nesting_too_deep")
    }

    private fun assertIssue(source: String, expectedCode: String) {
        val result = OpenMacroYamlReader.read(source)
        require(result is OpenMacroSourceResult.Failure) {
            "Expected '$expectedCode', but source was accepted."
        }
        assertEquals(expectedCode, result.issues.single().code)
        assertTrue(result.issues.single().line != null)
        assertTrue(result.issues.single().column != null)
    }

    private fun validSource() = """
        format: openmacro/v0.1
        metadata:
          id: charger-greeting
          name: Charger greeting
        triggers:
          - id: charger-connected
            type: android.power.connected
        conditions: []
        actions:
          - id: show-message
            type: android.notification.show
            config:
              title: Charging started
              message: The charger is connected.
    """.trimIndent() + "\n"
}
