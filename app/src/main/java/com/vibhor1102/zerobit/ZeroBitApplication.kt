/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.ApprovedMacroSnapshot
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSource
import com.vibhor1102.zerobit.openmacro.storage.AndroidSafWorkspaceStore
import com.vibhor1102.zerobit.openmacro.runtime.ApprovalStorePlanProvider
import com.vibhor1102.zerobit.openmacro.runtime.BoundedRuntimeDiagnostics
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeCoordinator
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeOwner
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeProcessController
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeProcessState
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeRecoveryReport
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeLifecycleResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeMacroOverview
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeDiagnosticKind
import com.vibhor1102.zerobit.openmacro.runtime.android.AndroidActionExecutor
import com.vibhor1102.zerobit.openmacro.runtime.android.AndroidConditionEvaluator
import com.vibhor1102.zerobit.openmacro.runtime.android.AndroidRuntimePermissionChecker
import com.vibhor1102.zerobit.openmacro.runtime.android.AndroidTriggerRegistrar
import com.vibhor1102.zerobit.openmacro.runtime.android.ExecutorRuntimeTaskDispatcher
import com.vibhor1102.zerobit.openmacro.storage.AndroidEnabledMacroStore
import com.vibhor1102.zerobit.openmacro.storage.AndroidKeystoreSecretStore
import com.vibhor1102.zerobit.openmacro.storage.AndroidPreferencesVariableStore
import com.vibhor1102.zerobit.openmacro.storage.AndroidWorkspaceSelection
import com.vibhor1102.zerobit.openmacro.storage.AndroidWorkspaceSelectionStore
import com.vibhor1102.zerobit.openmacro.storage.ApprovalStore
import com.vibhor1102.zerobit.openmacro.storage.ApprovalStoreResult
import com.vibhor1102.zerobit.openmacro.storage.ApprovedRevision
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceMacroListResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceMacroResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceCreateResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceDeleteResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceGuardedWriteResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceIdentityMoveResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceMutationService
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceRenameResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceWriteResult
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * Owns the one runtime graph shared by activities, listeners, and receivers in
 * this process. Desired macros are restored before a schedule receiver runs.
 */
class ZeroBitApplication : Application() {
    lateinit var runtimeController: RuntimeProcessController
        private set

    lateinit var runtimeDiagnostics: BoundedRuntimeDiagnostics
        private set

    private lateinit var approvalStore: ApprovalStore

    private lateinit var workspaceSelectionStore: AndroidWorkspaceSelectionStore

    var runtimeRecoveryReport = RuntimeRecoveryReport(emptyList())
        private set

    override fun onCreate() {
        super.onCreate()
        val registry = CapabilityRegistry.builtIn()
        val pipeline = OpenMacroProposalPipeline(registry)
        approvalStore = ApprovalStore(filesDir.toPath(), pipeline)
        workspaceSelectionStore = AndroidWorkspaceSelectionStore(this)
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "zerobit-runtime").apply { isDaemon = true }
        }
        runtimeDiagnostics = BoundedRuntimeDiagnostics()
        val coordinator = RuntimeCoordinator(
            approvedPlans = ApprovalStorePlanProvider(approvalStore::loadCurrent),
            triggerRegistrar = AndroidTriggerRegistrar(
                context = this,
                onScheduleFailure = { subscriptionId, problem ->
                    val macroId = subscriptionId.substringBefore(':')
                    val blockId = subscriptionId.substringAfter(':', "")
                    runtimeDiagnostics.record(
                        macroId = macroId,
                        kind = RuntimeDiagnosticKind.SCHEDULE_REARM_FAILED,
                        blockId = blockId.ifBlank { null },
                        message = problem.message
                            ?: "Could not schedule the next occurrence.",
                    )
                },
            ),
            conditionEvaluator = AndroidConditionEvaluator(this),
            actionExecutor = AndroidActionExecutor(this),
            permissionChecker = AndroidRuntimePermissionChecker(this),
            dispatcher = ExecutorRuntimeTaskDispatcher(executor),
            variables = AndroidPreferencesVariableStore(this),
            secrets = AndroidKeystoreSecretStore(this),
            diagnostics = runtimeDiagnostics,
            enabledState = AndroidEnabledMacroStore(this),
        )
        runtimeController = RuntimeProcessController(
            RuntimeOwner(
                coordinator = coordinator,
                ownedResources = listOf(
                    Closeable { executor.shutdownNow() },
                ),
            ),
        )
        updateRecovery(runtimeController.start())
    }

    override fun onTerminate() {
        runtimeController.close()
        super.onTerminate()
    }

    fun retryDesiredMacros(): RuntimeRecoveryReport {
        updateRecovery(runtimeController.retryDesiredMacros())
        return runtimeRecoveryReport
    }

    fun approveMacro(
        proposal: OpenMacroProposal,
    ): ApprovalStoreResult<ApprovedRevision> = approvalStore.approve(proposal)

    fun currentApprovedSnapshot(macroId: String): ApprovedMacroSnapshot? =
        when (val result = approvalStore.loadCurrent(macroId)) {
            is ApprovalStoreResult.Success -> result.value?.snapshot
            is ApprovalStoreResult.Failure -> null
        }

    fun enableMacro(macroId: String): RuntimeLifecycleResult =
        runtimeController.enable(macroId)

    fun disableMacro(macroId: String): RuntimeLifecycleResult =
        runtimeController.disable(macroId)

    fun isMacroEnabled(macroId: String): Boolean =
        runtimeController.isEnabled(macroId)

    fun macroOverview(macroId: String): RuntimeMacroOverview =
        runtimeDiagnostics.snapshot(macroId).let { events ->
            RuntimeMacroOverview(
                active = runtimeController.status(macroId),
                lastEvent = events.lastOrNull(),
                recentEvents = events.takeLast(MAX_VISIBLE_RUNTIME_EVENTS),
            )
        }

    fun selectedWorkspace(): AndroidWorkspaceSelection? =
        workspaceSelectionStore.selected()

    fun selectWorkspace(treeUri: Uri): AndroidWorkspaceSelection {
        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        return workspaceSelectionStore.save(treeUri)
    }

    fun listWorkspaceMacroIds(): WorkspaceMacroListResult =
        workspaceStore()?.listMacroIds()
            ?: WorkspaceMacroListResult.Success(emptyList())

    fun readWorkspaceMacro(macroId: String): WorkspaceMacroResult =
        workspaceStore()?.read(macroId)
            ?: WorkspaceMacroResult.Missing

    fun writeWorkspaceMacro(source: OpenMacroSource): WorkspaceWriteResult =
        workspaceStore()?.write(source)
            ?: WorkspaceWriteResult.Failure(
                code = "workspace_not_selected",
                message = "Choose a workspace folder first.",
            )

    fun writeWorkspaceMacroIfUnchanged(
        source: OpenMacroSource,
        expectedMacroId: String?,
        expectedSourceText: String?,
    ): WorkspaceGuardedWriteResult =
        workspaceStore()?.let {
            WorkspaceMutationService(it).writeIfUnchanged(
                source = source,
                expectedMacroId = expectedMacroId,
                expectedSourceText = expectedSourceText,
            )
        } ?: WorkspaceGuardedWriteResult.Failure(
            code = "workspace_not_selected",
            message = "Choose a workspace folder first.",
        )

    fun moveEditedWorkspaceMacro(
        source: OpenMacroSource,
        oldMacroId: String,
        expectedOldSourceText: String,
    ): WorkspaceIdentityMoveResult =
        workspaceStore()?.let {
            WorkspaceMutationService(it).moveEditedSource(
                source = source,
                oldMacroId = oldMacroId,
                expectedOldSourceText = expectedOldSourceText,
            )
        } ?: WorkspaceIdentityMoveResult.Failure(
            code = "workspace_not_selected",
            message = "Choose a workspace folder first.",
        )

    fun createWorkspaceMacro(macroId: String): WorkspaceCreateResult =
        workspaceStore()?.let { WorkspaceMutationService(it).create(macroId) }
            ?: WorkspaceCreateResult.Failure(
                code = "workspace_not_selected",
                message = "Choose a workspace folder first.",
            )

    fun renameWorkspaceMacro(oldId: String, newId: String): WorkspaceRenameResult =
        workspaceStore()?.let { WorkspaceMutationService(it).rename(oldId, newId) }
            ?: WorkspaceRenameResult.Failure(
                code = "workspace_not_selected",
                message = "Choose a workspace folder first.",
            )

    fun deleteWorkspaceMacro(macroId: String): WorkspaceDeleteResult =
        workspaceStore()?.let { WorkspaceMutationService(it).delete(macroId) }
            ?: WorkspaceDeleteResult.Failure(
                code = "workspace_not_selected",
                message = "Choose a workspace folder first.",
            )

    private fun updateRecovery(state: RuntimeProcessState) {
        if (state is RuntimeProcessState.Started) {
            runtimeRecoveryReport = RuntimeRecoveryReport.from(state.restoration)
        }
    }

    private fun workspaceStore(): AndroidSafWorkspaceStore? =
        workspaceSelectionStore.selected()?.let { selection ->
            AndroidSafWorkspaceStore(contentResolver, selection.treeUri)
        }

    private companion object {
        const val MAX_VISIBLE_RUNTIME_EVENTS = 20
    }
}
