/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission

/**
 * Owns enabled macro subscriptions and deterministic executions.
 *
 * The coordinator only accepts plans loaded from ApprovedPlanProvider. Queued
 * callbacks carry a generation token and become no-ops after disable/re-enable.
 */
class RuntimeCoordinator(
    private val approvedPlans: ApprovedPlanProvider,
    private val triggerRegistrar: RuntimeTriggerRegistrar,
    private val conditionEvaluator: RuntimeConditionEvaluator,
    private val actionExecutor: RuntimeActionExecutor,
    private val permissionChecker: RuntimePermissionChecker,
    private val dispatcher: RuntimeTaskDispatcher,
    private val diagnostics: BoundedRuntimeDiagnostics,
) {
    private val lock = Any()
    private val sessions = mutableMapOf<String, EnabledSession>()
    private var nextGeneration = 1L
    private var nextRunId = 1L

    fun enable(macroId: String): RuntimeLifecycleResult {
        val approved = when (val result = approvedPlans.loadCurrent(macroId)) {
            is ApprovedPlanResult.Failure -> {
                return enableFailure(macroId, result.message)
            }
            ApprovedPlanResult.Missing -> {
                return enableFailure(macroId, "No approved snapshot is available.")
            }
            is ApprovedPlanResult.Success -> result
        }
        if (approved.plan.macroId != macroId) {
            return enableFailure(macroId, "The approved plan belongs to a different macro.")
        }

        val missingPermissions =
            permissionChecker.missingPermissions(approved.plan.requiredPermissions)
        if (missingPermissions.isNotEmpty()) {
            return enableFailure(
                macroId,
                "Missing permissions: ${missingPermissions.sortedBy { it.name }.joinToString { it.manifestName }}",
                missingPermissions,
            )
        }

        val generation = synchronized(lock) { nextGeneration++ }
        val subscriptions = mutableListOf<RuntimeCancellation>()
        approved.plan.triggers.forEach { trigger ->
            val result = try {
                triggerRegistrar.subscribe(
                    macroId = macroId,
                    trigger = trigger,
                    onTriggered = { queueTrigger(macroId, generation, trigger.blockId) },
                )
            } catch (problem: RuntimeException) {
                TriggerSubscriptionResult.Failure(
                    problem.message ?: "Trigger subscription failed.",
                )
            }
            when (result) {
                is TriggerSubscriptionResult.Failure -> {
                    cancelAll(subscriptions)
                    return enableFailure(
                        macroId,
                        "Could not subscribe trigger '${trigger.blockId}': ${result.message}",
                    )
                }
                is TriggerSubscriptionResult.Success ->
                    subscriptions += result.cancellation
            }
        }

        val previous = synchronized(lock) {
            sessions.put(
                macroId,
                EnabledSession(
                    generation = generation,
                    revisionId = approved.revisionId,
                    plan = approved.plan,
                    subscriptions = subscriptions,
                ),
            )
        }
        previous?.let { cancelAll(it.subscriptions) }
        diagnostics.record(
            macroId = macroId,
            kind = RuntimeDiagnosticKind.ENABLED,
            message = "Enabled approved revision ${approved.revisionId}.",
        )
        return RuntimeLifecycleResult.Enabled(
            revisionId = approved.revisionId,
            triggerCount = subscriptions.size,
        )
    }

    fun disable(macroId: String): RuntimeLifecycleResult =
        disable(macroId, recordWhenMissing = true)

    fun isEnabled(macroId: String): Boolean = synchronized(lock) {
        macroId in sessions
    }

    fun enabledMacroIds(): Set<String> = synchronized(lock) {
        sessions.keys.toSet()
    }

    fun disableAll() {
        enabledMacroIds().forEach(::disable)
    }

    private fun disable(
        macroId: String,
        recordWhenMissing: Boolean,
    ): RuntimeLifecycleResult {
        val removed = synchronized(lock) { sessions.remove(macroId) }
        if (removed == null) {
            if (recordWhenMissing) {
                diagnostics.record(
                    macroId = macroId,
                    kind = RuntimeDiagnosticKind.DISABLED,
                    message = "Macro was already disabled.",
                )
            }
            return RuntimeLifecycleResult.AlreadyDisabled
        }
        cancelAll(removed.subscriptions)
        diagnostics.record(
            macroId = macroId,
            kind = RuntimeDiagnosticKind.DISABLED,
            message = "Disabled approved revision ${removed.revisionId}.",
        )
        return RuntimeLifecycleResult.Disabled
    }

    private fun queueTrigger(
        macroId: String,
        generation: Long,
        triggerBlockId: String,
    ) {
        try {
            dispatcher.dispatch {
                executeTrigger(macroId, generation, triggerBlockId)
            }
        } catch (problem: RuntimeException) {
            diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.TRIGGER_DISPATCH_FAILED,
                blockId = triggerBlockId,
                message = problem.message ?: "Could not queue trigger work.",
            )
        }
    }

    private fun executeTrigger(
        macroId: String,
        generation: Long,
        triggerBlockId: String,
    ) {
        val start = synchronized(lock) {
            val session = sessions[macroId]
            if (session == null || session.generation != generation) {
                return
            }
            if (session.executing) {
                null
            } else {
                session.executing = true
                RunStart(
                    runId = nextRunId++,
                    plan = session.plan,
                )
            }
        }
        if (start == null) {
            diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.TRIGGER_IGNORED_BUSY,
                blockId = triggerBlockId,
                message = "Ignored trigger while this macro was already running.",
            )
            return
        }

        diagnostics.record(
            macroId = macroId,
            kind = RuntimeDiagnosticKind.TRIGGER_RECEIVED,
            runId = start.runId,
            blockId = triggerBlockId,
            message = "Trigger started evaluation.",
        )
        try {
            if (
                !conditionsPass(
                    macroId,
                    generation,
                    start.runId,
                    start.plan.conditions,
                )
            ) {
                return
            }
            if (
                !actionsSucceed(
                    macroId,
                    generation,
                    start.runId,
                    start.plan.actions,
                )
            ) {
                return
            }
            if (!isSessionActive(macroId, generation)) {
                recordCancellation(macroId, start.runId)
                return
            }
            diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.RUN_SUCCEEDED,
                runId = start.runId,
                message = "All actions completed.",
            )
        } finally {
            synchronized(lock) {
                sessions[macroId]
                    ?.takeIf { it.generation == generation }
                    ?.executing = false
            }
        }
    }

    private fun conditionsPass(
        macroId: String,
        generation: Long,
        runId: Long,
        conditions: List<RuntimeStep>,
    ): Boolean {
        conditions.forEach { condition ->
            if (!isSessionActive(macroId, generation)) {
                recordCancellation(macroId, runId)
                return false
            }
            val result = try {
                conditionEvaluator.evaluate(condition)
            } catch (problem: RuntimeException) {
                ConditionResult.Failed(problem.message ?: "Condition evaluation failed.")
            }
            when (result) {
                ConditionResult.Passed -> diagnostics.record(
                    macroId = macroId,
                    kind = RuntimeDiagnosticKind.CONDITION_PASSED,
                    runId = runId,
                    blockId = condition.blockId,
                    message = "Condition passed.",
                )
                is ConditionResult.Blocked -> {
                    diagnostics.record(
                        macroId = macroId,
                        kind = RuntimeDiagnosticKind.CONDITION_BLOCKED,
                        runId = runId,
                        blockId = condition.blockId,
                        message = result.reason,
                    )
                    return false
                }
                is ConditionResult.Failed -> {
                    diagnostics.record(
                        macroId = macroId,
                        kind = RuntimeDiagnosticKind.CONDITION_FAILED,
                        runId = runId,
                        blockId = condition.blockId,
                        message = result.message,
                    )
                    return false
                }
            }
        }
        return true
    }

    private fun actionsSucceed(
        macroId: String,
        generation: Long,
        runId: Long,
        actions: List<RuntimeStep>,
    ): Boolean {
        actions.forEach { action ->
            if (!isSessionActive(macroId, generation)) {
                recordCancellation(macroId, runId)
                return false
            }
            val result = try {
                actionExecutor.execute(action)
            } catch (problem: RuntimeException) {
                ActionResult.Failed(problem.message ?: "Action execution failed.")
            }
            when (result) {
                ActionResult.Succeeded -> diagnostics.record(
                    macroId = macroId,
                    kind = RuntimeDiagnosticKind.ACTION_SUCCEEDED,
                    runId = runId,
                    blockId = action.blockId,
                    message = "Action completed.",
                )
                is ActionResult.Failed -> {
                    diagnostics.record(
                        macroId = macroId,
                        kind = RuntimeDiagnosticKind.ACTION_FAILED,
                        runId = runId,
                        blockId = action.blockId,
                        message = result.message,
                    )
                    return false
                }
            }
            if (!isSessionActive(macroId, generation)) {
                recordCancellation(macroId, runId)
                return false
            }
        }
        return true
    }

    private fun isSessionActive(macroId: String, generation: Long): Boolean =
        synchronized(lock) {
            sessions[macroId]?.generation == generation
        }

    private fun recordCancellation(macroId: String, runId: Long) {
        diagnostics.record(
            macroId = macroId,
            kind = RuntimeDiagnosticKind.RUN_CANCELLED,
            runId = runId,
            message = "Run stopped because the macro was disabled or replaced.",
        )
    }

    private fun enableFailure(
        macroId: String,
        message: String,
        missingPermissions: Set<AndroidPermission> = emptySet(),
    ): RuntimeLifecycleResult.EnableFailed {
        diagnostics.record(
            macroId = macroId,
            kind = RuntimeDiagnosticKind.ENABLE_FAILED,
            message = message,
        )
        return RuntimeLifecycleResult.EnableFailed(message, missingPermissions)
    }

    private fun cancelAll(subscriptions: List<RuntimeCancellation>) {
        subscriptions.asReversed().forEach { cancellation ->
            runCatching(cancellation::cancel)
        }
    }

    private data class EnabledSession(
        val generation: Long,
        val revisionId: String,
        val plan: RuntimePlan,
        val subscriptions: List<RuntimeCancellation>,
        var executing: Boolean = false,
    )

    private data class RunStart(
        val runId: Long,
        val plan: RuntimePlan,
    )
}

sealed interface RuntimeLifecycleResult {
    data class Enabled(
        val revisionId: String,
        val triggerCount: Int,
    ) : RuntimeLifecycleResult

    data class EnableFailed(
        val message: String,
        val missingPermissions: Set<AndroidPermission> = emptySet(),
    ) : RuntimeLifecycleResult

    data object Disabled : RuntimeLifecycleResult

    data object AlreadyDisabled : RuntimeLifecycleResult
}
