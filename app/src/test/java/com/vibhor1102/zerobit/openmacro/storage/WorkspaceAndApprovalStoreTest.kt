/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourceResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroYamlReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceAndApprovalStoreTest {
    @Test
    fun workspaceDeleteIsExplicitAndIdempotent() {
        val root = temporaryFolder.newFolder("workspace-delete").toPath()
        val workspace = WorkspaceMacroStore(root)
        val source = parsed(validSource())
        assertEquals(WorkspaceWriteResult.Success, workspace.write(source))

        assertEquals(
            WorkspaceDeleteResult.Deleted,
            workspace.delete("charger-greeting"),
        )
        assertEquals(
            WorkspaceDeleteResult.Missing,
            workspace.delete("charger-greeting"),
        )
        assertEquals(
            WorkspaceMacroResult.Missing,
            workspace.read("charger-greeting"),
        )
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val pipeline = OpenMacroProposalPipeline(CapabilityRegistry.builtIn())

    @Test
    fun workspaceUsesStablePathSafeNamesAndChecksDeclaredId() {
        val root = temporaryFolder.newFolder("workspace").toPath()
        val store = WorkspaceMacroStore(root)
        val source = parsed(validSource())

        assertEquals(WorkspaceWriteResult.Success, store.write(source))

        val listed = store.listMacroIds()
        require(listed is WorkspaceMacroListResult.Success)
        assertEquals(listOf("charger-greeting"), listed.macroIds)
        val loaded = store.read("charger-greeting")
        require(loaded is WorkspaceMacroResult.Success)
        assertEquals(source, loaded.source)
        assertTrue(store.read("../escape") is WorkspaceMacroResult.InvalidId)

        val path = root.resolve("macros/charger-greeting.openmacro.yaml")
        Files.write(
            path,
            validSource()
                .replace("id: charger-greeting", "id: different-id")
                .toByteArray(StandardCharsets.UTF_8),
        )
        val mismatch = store.read("charger-greeting")
        require(mismatch is WorkspaceMacroResult.InvalidSource)
        assertEquals("workspace_id_mismatch", mismatch.issues.single().code)
    }

    @Test
    fun externalWorkspaceEditCannotReplaceTheApprovedRuntimeSnapshot() {
        val workspaceRoot = temporaryFolder.newFolder("workspace-edit").toPath()
        val privateRoot = temporaryFolder.newFolder("private-edit").toPath()
        val workspace = WorkspaceMacroStore(workspaceRoot)
        val approvals = ApprovalStore(privateRoot, pipeline, IncrementingClock())
        val service = WorkspaceReviewService(workspace, approvals, pipeline)
        assertEquals(
            WorkspaceWriteResult.Success,
            workspace.write(parsed(validSource())),
        )

        val initialReview = service.review("charger-greeting")
        require(initialReview is WorkspaceReviewResult.Ready)
        val firstApproval = service.approve(initialReview.proposal)
        require(firstApproval is ApprovalStoreResult.Success)

        val workspacePath =
            workspaceRoot.resolve("macros/charger-greeting.openmacro.yaml")
        Files.write(
            workspacePath,
            changedSource().toByteArray(StandardCharsets.UTF_8),
        )

        val stillApproved = approvals.loadCurrent("charger-greeting")
        require(stillApproved is ApprovalStoreResult.Success)
        val approvedMessage = stillApproved.value
            ?.snapshot
            ?.explanation
            ?.blocks
            ?.single { it.blockId == "show-message" }
            ?.summary
            .orEmpty()
        assertTrue(approvedMessage.contains("The charger is connected."))
        assertFalse(approvedMessage.contains("Time to charge."))

        val changedReview = service.review("charger-greeting")
        require(changedReview is WorkspaceReviewResult.Ready)
        assertTrue(changedReview.proposal.comparison.approvalRequired)
    }

    @Test
    fun approvalsAreImmutableAndRollbackCreatesAnAuditableRevision() {
        val privateRoot = temporaryFolder.newFolder("private-history").toPath()
        val approvals = ApprovalStore(privateRoot, pipeline, IncrementingClock())

        val first = approvals.approve(ready(validSource()))
        require(first is ApprovalStoreResult.Success)
        val second = approvals.approve(ready(changedSource()))
        require(second is ApprovalStoreResult.Success)

        val historyBefore = approvals.listRevisions("charger-greeting")
        require(historyBefore is ApprovalStoreResult.Success)
        assertEquals(2, historyBefore.value.size)

        val rollback = approvals.rollback(
            macroId = "charger-greeting",
            targetRevisionId = first.value.revisionId,
        )
        require(rollback is ApprovalStoreResult.Success)
        assertEquals(ApprovalKind.ROLLBACK, rollback.value.kind)
        assertEquals(first.value.revisionId, rollback.value.restoredFromRevisionId)
        assertEquals(second.value.revisionId, rollback.value.previousRevisionId)

        val current = approvals.loadCurrent("charger-greeting")
        require(current is ApprovalStoreResult.Success)
        assertEquals(rollback.value.revisionId, current.value?.revisionId)
        assertTrue(
            current.value
                ?.snapshot
                ?.explanation
                ?.blocks
                ?.single { it.blockId == "show-message" }
                ?.summary
                .orEmpty()
                .contains("The charger is connected."),
        )

        val historyAfter = approvals.listRevisions("charger-greeting")
        require(historyAfter is ApprovalStoreResult.Success)
        assertEquals(3, historyAfter.value.size)
    }

    @Test
    fun corruptedApprovedSourceIsNeverReturnedAsRunnable() {
        val privateRoot = temporaryFolder.newFolder("private-corrupt").toPath()
        val approvals = ApprovalStore(privateRoot, pipeline, IncrementingClock())
        val approved = approvals.approve(ready(validSource()))
        require(approved is ApprovalStoreResult.Success)
        val sourcePath = privateRoot.resolve(
            "approvals/charger-greeting/revisions/" +
                "${approved.value.revisionId}/source.openmacro.yaml",
        )
        Files.write(
            sourcePath,
            changedSource().toByteArray(StandardCharsets.UTF_8),
        )

        val loaded = approvals.loadCurrent("charger-greeting")

        require(loaded is ApprovalStoreResult.Failure)
        assertEquals("corrupt_approval_integrity", loaded.code)
    }

    private fun parsed(text: String) = when (val result = OpenMacroYamlReader.read(text)) {
        is OpenMacroSourceResult.Success -> result.source
        is OpenMacroSourceResult.Failure -> error("Test source did not parse: ${result.issues}")
    }

    private fun ready(text: String): OpenMacroProposal {
        val result = pipeline.propose(text)
        require(result is ProposalResult.Ready)
        return result.proposal
    }

    private fun changedSource() = validSource().replace(
        "message: The charger is connected.",
        "message: Time to charge.",
    )

    private fun validSource() = """
        format: openmacro/v0.1
        metadata:
          id: charger-greeting
          name: Charger greeting
        triggers:
          - id: charger-connected
            type: android.power.connected
        conditions:
          - id: device-is-unlocked
            type: android.device.unlocked
        actions:
          - id: show-message
            type: android.notification.show
            config:
              title: Charging started
              message: The charger is connected.
    """.trimIndent() + "\n"

    private class IncrementingClock : MillisecondClock {
        private var next = 1_800_000_000_000L

        override fun nowEpochMillis(): Long = next++
    }
}
