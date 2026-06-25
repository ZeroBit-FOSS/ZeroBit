/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit

import android.app.Application
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.ApprovedMacroSnapshot
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
import com.vibhor1102.zerobit.openmacro.storage.ApprovalStore
import com.vibhor1102.zerobit.openmacro.storage.ApprovalStoreResult
import com.vibhor1102.zerobit.openmacro.storage.ApprovedRevision
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

    var runtimeRecoveryReport = RuntimeRecoveryReport(emptyList())
        private set

    override fun onCreate() {
        super.onCreate()
        val registry = CapabilityRegistry.builtIn()
        val pipeline = OpenMacroProposalPipeline(registry)
        approvalStore = ApprovalStore(filesDir.toPath(), pipeline)
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

    private fun updateRecovery(state: RuntimeProcessState) {
        if (state is RuntimeProcessState.Started) {
            runtimeRecoveryReport = RuntimeRecoveryReport.from(state.restoration)
        }
    }

    private companion object {
        const val MAX_VISIBLE_RUNTIME_EVENTS = 20
    }
}
