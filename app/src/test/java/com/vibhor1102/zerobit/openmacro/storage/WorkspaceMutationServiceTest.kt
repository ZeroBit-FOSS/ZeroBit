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
    fun createWritesValidStarterMacro() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("create-workspace").toPath(),
        )

        val result = WorkspaceMutationService(workspace).create("morning-routine")

        require(result is WorkspaceCreateResult.Created)
        assertEquals("morning-routine", result.source.document.metadata.id)
        assertEquals("Morning Routine", result.source.document.metadata.name)
        assertTrue(workspace.read("morning-routine") is WorkspaceMacroResult.Success)
    }

    @Test
    fun createRefusesToOverwriteExistingMacro() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("create-conflict").toPath(),
        )
        val mutations = WorkspaceMutationService(workspace)
        require(mutations.create("existing") is WorkspaceCreateResult.Created)

        assertEquals(WorkspaceCreateResult.Conflict, mutations.create("existing"))
    }

    @Test
    fun createRejectsUnsafeMacroId() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("create-invalid").toPath(),
        )

        val result = WorkspaceMutationService(workspace).create("Not Safe")

        require(result is WorkspaceCreateResult.Failure)
        assertEquals("invalid_macro_id", result.code)
        assertEquals(emptyList<String>(), workspace.listMacroIds().macroIds())
    }

    @Test
    fun guardedWriteAcceptsMatchingBaselineAndRejectsExternalEdit() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("guarded-write").toPath(),
        )
        val mutations = WorkspaceMutationService(workspace)
        val created = mutations.create("guarded")
        require(created is WorkspaceCreateResult.Created)
        val baseline = created.source
        val edited = parse(baseline.originalText.replace("A new local", "My local"))

        assertEquals(
            WorkspaceGuardedWriteResult.Written,
            mutations.writeIfUnchanged(
                source = edited,
                expectedMacroId = "guarded",
                expectedSourceText = baseline.originalText,
            ),
        )

        val staleEdit = parse(edited.originalText.replace("My local", "Another local"))
        assertEquals(
            WorkspaceGuardedWriteResult.Conflict(WorkspaceWriteConflict.MODIFIED),
            mutations.writeIfUnchanged(
                source = staleEdit,
                expectedMacroId = "guarded",
                expectedSourceText = baseline.originalText,
            ),
        )
    }

    @Test
    fun guardedWriteRejectsMissingBaselineAndExistingSaveAsTarget() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("guarded-conflicts").toPath(),
        )
        val mutations = WorkspaceMutationService(workspace)
        val existing = mutations.create("existing")
        require(existing is WorkspaceCreateResult.Created)
        workspace.delete("existing")

        assertEquals(
            WorkspaceGuardedWriteResult.Conflict(WorkspaceWriteConflict.MISSING),
            mutations.writeIfUnchanged(
                source = existing.source,
                expectedMacroId = "existing",
                expectedSourceText = existing.source.originalText,
            ),
        )
        require(mutations.create("existing") is WorkspaceCreateResult.Created)
        assertEquals(
            WorkspaceGuardedWriteResult.Conflict(WorkspaceWriteConflict.EXISTS),
            mutations.writeIfUnchanged(
                source = workspace.read("existing").successSource(),
                expectedMacroId = null,
                expectedSourceText = null,
            ),
        )
    }

    @Test
    fun identityMoveWritesCurrentEditsAndRemovesOldFile() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("identity-move").toPath(),
        )
        val mutations = WorkspaceMutationService(workspace)
        val created = mutations.create("old-id")
        require(created is WorkspaceCreateResult.Created)
        val movedSource = parse(
            created.source.originalText
                .replace("id: old-id", "id: new-id")
                .replace("A new local", "Edited local"),
        )

        assertEquals(
            WorkspaceIdentityMoveResult.Moved,
            mutations.moveEditedSource(
                source = movedSource,
                oldMacroId = "old-id",
                expectedOldSourceText = created.source.originalText,
            ),
        )
        assertEquals(WorkspaceMacroResult.Missing, workspace.read("old-id"))
        val moved = workspace.read("new-id").successSource()
        assertTrue(moved.originalText.contains("Edited local automation"))
    }

    @Test
    fun identityMoveRejectsStaleOldFileAndOccupiedTarget() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("identity-conflicts").toPath(),
        )
        val mutations = WorkspaceMutationService(workspace)
        val old = mutations.create("old-id")
        require(old is WorkspaceCreateResult.Created)
        val new = mutations.create("new-id")
        require(new is WorkspaceCreateResult.Created)
        val movedSource = parse(old.source.originalText.replace("id: old-id", "id: new-id"))

        assertEquals(
            WorkspaceIdentityMoveResult.Conflict(WorkspaceWriteConflict.EXISTS),
            mutations.moveEditedSource(
                source = movedSource,
                oldMacroId = "old-id",
                expectedOldSourceText = old.source.originalText,
            ),
        )
        workspace.delete("new-id")
        val externalEdit = parse(
            old.source.originalText.replace("A new local", "External local"),
        )
        assertEquals(WorkspaceWriteResult.Success, workspace.write(externalEdit))
        assertEquals(
            WorkspaceIdentityMoveResult.Conflict(WorkspaceWriteConflict.MODIFIED),
            mutations.moveEditedSource(
                source = movedSource,
                oldMacroId = "old-id",
                expectedOldSourceText = old.source.originalText,
            ),
        )
    }

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

    @Test
    fun deleteRemovesOnlyWorkspaceSource() {
        val workspace = WorkspaceMacroStore(
            temporaryFolder.newFolder("delete-workspace").toPath(),
        )
        val mutations = WorkspaceMutationService(workspace)
        require(mutations.create("remove-me") is WorkspaceCreateResult.Created)

        assertEquals(WorkspaceDeleteResult.Deleted, mutations.delete("remove-me"))
        assertEquals(WorkspaceMacroResult.Missing, workspace.read("remove-me"))
    }
}

private fun WorkspaceMacroListResult.macroIds(): List<String> = when (this) {
    is WorkspaceMacroListResult.Success -> macroIds
    is WorkspaceMacroListResult.Failure -> emptyList()
}

private fun parse(sourceText: String) =
    (OpenMacroYamlReader.read(sourceText) as OpenMacroSourceResult.Success).source

private fun WorkspaceMacroResult.successSource() =
    (this as WorkspaceMacroResult.Success).source
