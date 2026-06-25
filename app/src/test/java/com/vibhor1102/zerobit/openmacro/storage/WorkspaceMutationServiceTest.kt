/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourceResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroYamlReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceMutationServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun renameChangesFileAndDeclaredIdWithoutReformattingComments() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("rename-workspace").toPath(),
        )
        val source = """
            # keep
            format: openmacro/v0.1
            metadata:
              id: old-id # keep inline
              name: Rename me
            triggers:
              - id: power
                type: android.power.connected
            conditions: []
            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent()
        val parsed = OpenMacroYamlReader.read(source)
        require(parsed is OpenMacroSourceResult.Success)
        assertEquals(WorkspaceWriteResult.Success, workspace.write(parsed.source))

        val result = WorkspaceMutationService(workspace).rename("old-id", "new-id")

        assertEquals(WorkspaceRenameResult.Renamed, result)
        assertEquals(WorkspaceMacroResult.Missing, workspace.read("old-id"))
        val renamed = workspace.read("new-id")
        require(renamed is WorkspaceMacroResult.Success)
        assertEquals("new-id", renamed.source.document.metadata.id)
        assertTrue(renamed.source.originalText.startsWith("# keep"))
        assertTrue(renamed.source.originalText.contains("\"new-id\" # keep inline"))
    }

    @Test
    fun renameRefusesToOverwriteExistingMacro() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("rename-conflict").toPath(),
        )
        listOf("old-id", "new-id").forEach { id ->
            val parsed = OpenMacroYamlReader.read(
                """
                    format: openmacro/v0.1
                    metadata:
                      id: $id
                      name: $id
                    triggers:
                      - id: power
                        type: android.power.connected
                    conditions: []
                    actions:
                      - id: stop
                        type: openmacro.flow.stop
                """.trimIndent(),
            )
            require(parsed is OpenMacroSourceResult.Success)
            workspace.write(parsed.source)
        }

        assertEquals(
            WorkspaceRenameResult.Conflict,
            WorkspaceMutationService(workspace).rename("old-id", "new-id"),
        )
    }
}
