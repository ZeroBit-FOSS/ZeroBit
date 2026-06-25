/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.storage.SecretStore
import com.vibhor1102.zerobit.openmacro.storage.VariableStore
import com.vibhor1102.zerobit.openmacro.storage.EnabledMacroStore
import com.vibhor1102.zerobit.openmacro.storage.InMemoryEnabledMacroStore

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
    private val variables: VariableStore,
    private val secrets: SecretStore,
    private val diagnostics: BoundedRuntimeDiagnostics,
    private val enabledState: EnabledMacroStore = InMemoryEnabledMacroStore(),
) {
    private val lock = Any()
    private val sessions = mutableMapOf<String, EnabledSession>()
    private var nextGeneration = 1L
    private var nextRunId = 1L

    fun enable(macroId: String): RuntimeLifecycleResult {
        val approved = when (val result = approvedPlans.loadCurrent(macroId)) {
            is ApprovedPlanResult.Failure -> {
                return enableFailure(
                    macroId,
                    result.message,
                    reason = EnableFailureReason.APPROVED_PLAN_UNAVAILABLE,
                )
            }
            ApprovedPlanResult.Missing -> {
                return enableFailure(
                    macroId,
                    "No approved snapshot is available.",
                    reason = EnableFailureReason.NO_APPROVED_SNAPSHOT,
                )
            }
            is ApprovedPlanResult.Success -> result
        }
        if (approved.plan.macroId != macroId) {
            return enableFailure(
                macroId,
                "The approved plan belongs to a different macro.",
                reason = EnableFailureReason.INVALID_APPROVED_PLAN,
            )
        }

        val missingPermissions =
            permissionChecker.missingPermissions(approved.plan.requiredPermissions)
        if (missingPermissions.isNotEmpty()) {
            return enableFailure(
                macroId,
                "Missing permissions: ${missingPermissions.sortedBy { it.name }.joinToString { it.manifestName }}",
                missingPermissions,
                EnableFailureReason.MISSING_PERMISSIONS,
            )
        }

        // Keep durable values across re-enable and process restarts. An initial
        // value is used only when the variable has never been written.
        approved.plan.variables.forEach { variable ->
            variable.initialValue?.let { initial ->
                if (variables.getValue(macroId, variable.name) == null) {
                    variables.setValue(macroId, variable.name, initial)
                }
            }
        }

        val generation = synchronized(lock) { nextGeneration++ }
        val runCancellation = RuntimeRunCancellation()
        val subscriptions = mutableListOf<RuntimeCancellation>()
        approved.plan.triggers.forEach { trigger ->
            val result = try {
                triggerRegistrar.subscribe(
                    macroId = macroId,
                    trigger = trigger,
                    onTriggered = { event ->
                        queueTrigger(macroId, generation, trigger.blockId, event)
                    },
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
                        reason = EnableFailureReason.SUBSCRIPTION_FAILED,
                    )
                }
                is TriggerSubscriptionResult.Success ->
                    subscriptions += result.cancellation
            }
        }

        try {
            enabledState.setEnabled(macroId, true)
        } catch (problem: RuntimeException) {
            cancelAll(subscriptions)
            return enableFailure(
                macroId,
                problem.message ?: "Could not persist enabled state.",
                reason = EnableFailureReason.STATE_PERSISTENCE_FAILED,
            )
        }

        val previous = synchronized(lock) {
            sessions.put(
                macroId,
                EnabledSession(
                    generation = generation,
                    revisionId = approved.revisionId,
                    plan = approved.plan,
                    subscriptions = subscriptions,
                    runCancellation = runCancellation,
                ),
            )
        }
        previous?.let {
            it.runCancellation.cancel()
            cancelAll(it.subscriptions)
        }
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
        disable(macroId, recordWhenMissing = true, persist = true)

    fun isEnabled(macroId: String): Boolean = synchronized(lock) {
        macroId in sessions
    }

    fun enabledMacroIds(): Set<String> = synchronized(lock) {
        sessions.keys.toSet()
    }

    fun status(macroId: String): RuntimeMacroStatus? = synchronized(lock) {
        sessions[macroId]?.let { session ->
            RuntimeMacroStatus(
                macroId = macroId,
                revisionId = session.revisionId,
                sourceFingerprint = session.plan.sourceFingerprint,
                triggerCount = session.subscriptions.size,
                executing = session.executing,
            )
        }
    }

    fun deliverScheduleAlarm(
        macroId: String,
        blockId: String,
        occurrence: java.time.Instant,
    ): Boolean {
        val delivery = synchronized(lock) {
            val session = sessions[macroId] ?: return false
            val trigger = session.plan.triggers
                .filterIsInstance<RuntimeStep.ObserveSchedule>()
                .find { it.blockId == blockId }
                ?: return false
            ScheduleDeliveryTarget(session.generation, trigger)
        }
        val local = occurrence.atZone(delivery.trigger.schedule.zoneId)
        queueTrigger(
            macroId = macroId,
            generation = delivery.generation,
            triggerBlockId = blockId,
            event = RuntimeTriggerEvent(
                mapOf(
                    "schedule.instant" to
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text(
                            occurrence.toString(),
                        ),
                    "schedule.local_time" to
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text(
                            local.toString(),
                        ),
                ),
            ),
        )
        return true
    }

    fun disableAll() {
        enabledMacroIds().forEach { macroId ->
            disable(macroId, recordWhenMissing = false, persist = false)
        }
    }

    fun restoreEnabledMacros(): RuntimeRestoreSummary {
        val results = enabledState.enabledMacroIds()
            .sorted()
            .associateWith(::enable)
        return RuntimeRestoreSummary(results)
    }

    private fun disable(
        macroId: String,
        recordWhenMissing: Boolean,
        persist: Boolean,
    ): RuntimeLifecycleResult {
        if (persist) {
            enabledState.setEnabled(macroId, false)
        }
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
        removed.runCancellation.cancel()
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
        event: RuntimeTriggerEvent,
    ) {
        val eventSnapshot = RuntimeTriggerEvent(event.values.toMap())
        try {
            dispatcher.dispatch {
                executeTrigger(macroId, generation, triggerBlockId, eventSnapshot)
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
        event: RuntimeTriggerEvent,
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
                    cancellation = session.runCancellation,
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

        val runId = start.runId
        val context = RuntimeContext(
            macroId = macroId,
            runId = runId,
            triggerBlockId = triggerBlockId,
            trigger = event,
            values = RuntimeValues(
                macroId = macroId,
                declarations = start.plan.variables,
                variables = variables,
                secrets = secrets,
            ),
        )

        diagnostics.record(
            macroId = macroId,
            kind = RuntimeDiagnosticKind.TRIGGER_RECEIVED,
            runId = runId,
            blockId = triggerBlockId,
            message = "Trigger started evaluation.",
        )
        try {
            if (
                !conditionsPass(
                    macroId,
                    generation,
                    runId,
                    start.plan.conditions,
                    start.plan.conditionTree,
                    context,
                )
            ) {
                return
            }
            if (
                !actionsSucceed(
                    macroId,
                    generation,
                    runId,
                    start.plan.actions,
                    context,
                    start.cancellation,
                )
            ) {
                return
            }
            if (!isSessionActive(macroId, generation)) {
                recordCancellation(macroId, runId)
                return
            }
            diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.RUN_SUCCEEDED,
                runId = runId,
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
        conditionTree: RuntimeConditionNode?,
        context: RuntimeContext,
    ): Boolean {
        val root = conditionTree ?: RuntimeConditionNode.All(
            conditions.map(RuntimeConditionNode::Condition),
        )
        return when (
            evaluateConditionNode(
                node = root,
                macroId = macroId,
                generation = generation,
                runId = runId,
                context = context,
                groupPath = if (conditionTree == null) null else "condition_tree",
            )
        ) {
            ConditionResult.Passed -> true
            is ConditionResult.Blocked,
            is ConditionResult.Failed -> false
        }
    }

    private fun evaluateConditionNode(
        node: RuntimeConditionNode,
        macroId: String,
        generation: Long,
        runId: Long,
        context: RuntimeContext,
        groupPath: String?,
    ): ConditionResult {
        if (!isSessionActive(macroId, generation)) {
            recordCancellation(macroId, runId)
            return ConditionResult.Failed("Run was cancelled.")
        }
        return when (node) {
            is RuntimeConditionNode.Condition -> evaluateCondition(
                condition = node.step,
                macroId = macroId,
                runId = runId,
                context = context,
            )
            is RuntimeConditionNode.All -> {
                var outcome: ConditionResult = ConditionResult.Passed
                for ((index, child) in node.children.withIndex()) {
                    when (
                        val result = evaluateConditionNode(
                            child,
                            macroId,
                            generation,
                            runId,
                            context,
                            groupPath?.let { "$it.all[$index]" },
                        )
                    ) {
                        ConditionResult.Passed -> Unit
                        is ConditionResult.Blocked -> {
                            outcome = result
                            break
                        }
                        is ConditionResult.Failed -> return result
                    }
                }
                recordConditionGroup(macroId, runId, groupPath, "AND", outcome)
                outcome
            }
            is RuntimeConditionNode.Any -> {
                var lastBlocked: ConditionResult.Blocked? = null
                var passed = false
                for ((index, child) in node.children.withIndex()) {
                    when (
                        val result = evaluateConditionNode(
                            child,
                            macroId,
                            generation,
                            runId,
                            context,
                            groupPath?.let { "$it.any[$index]" },
                        )
                    ) {
                        ConditionResult.Passed -> {
                            passed = true
                            break
                        }
                        is ConditionResult.Blocked -> lastBlocked = result
                        is ConditionResult.Failed -> return result
                    }
                }
                val outcome = if (passed) {
                    ConditionResult.Passed
                } else {
                    lastBlocked ?: ConditionResult.Blocked("No condition in the OR group passed.")
                }
                recordConditionGroup(macroId, runId, groupPath, "OR", outcome)
                outcome
            }
            is RuntimeConditionNode.Not -> when (
                val result = evaluateConditionNode(
                    node.child,
                    macroId,
                    generation,
                    runId,
                    context,
                    groupPath?.let { "$it.not" },
                )
            ) {
                ConditionResult.Passed -> {
                    val outcome = ConditionResult.Blocked("The condition inside NOT passed.")
                    recordConditionGroup(macroId, runId, groupPath, "NOT", outcome)
                    outcome
                }
                is ConditionResult.Blocked -> {
                    val outcome = ConditionResult.Passed
                    recordConditionGroup(macroId, runId, groupPath, "NOT", outcome)
                    outcome
                }
                is ConditionResult.Failed -> result
            }
        }
    }

    private fun recordConditionGroup(
        macroId: String,
        runId: Long,
        groupPath: String?,
        operator: String,
        result: ConditionResult,
    ) {
        if (groupPath == null) {
            return
        }
        when (result) {
            ConditionResult.Passed -> diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.CONDITION_GROUP_PASSED,
                runId = runId,
                message = "$operator group '$groupPath' passed.",
            )
            is ConditionResult.Blocked -> diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.CONDITION_GROUP_BLOCKED,
                runId = runId,
                message = "$operator group '$groupPath' blocked: ${result.reason}",
            )
            is ConditionResult.Failed -> Unit
        }
    }

    private fun evaluateCondition(
        condition: RuntimeStep,
        macroId: String,
        runId: Long,
        context: RuntimeContext,
    ): ConditionResult {
        val result = try {
            conditionEvaluator.evaluate(condition, context)
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
            is ConditionResult.Blocked -> diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.CONDITION_BLOCKED,
                runId = runId,
                blockId = condition.blockId,
                message = result.reason,
            )
            is ConditionResult.Failed -> diagnostics.record(
                macroId = macroId,
                kind = RuntimeDiagnosticKind.CONDITION_FAILED,
                runId = runId,
                blockId = condition.blockId,
                message = result.message,
            )
        }
        return result
    }

    private fun actionsSucceed(
        macroId: String,
        generation: Long,
        runId: Long,
        actions: List<RuntimeStep>,
        context: RuntimeContext,
        cancellation: RuntimeRunCancellation,
    ): Boolean {
        for (action in actions) {
            if (!isSessionActive(macroId, generation)) {
                recordCancellation(macroId, runId)
                return false
            }
            if (action is RuntimeStep.StopActions) {
                diagnostics.record(
                    macroId = macroId,
                    kind = RuntimeDiagnosticKind.ACTION_SUCCEEDED,
                    runId = runId,
                    blockId = action.blockId,
                    message = "Stopped before later actions.",
                )
                return true
            }
            if (action is RuntimeStep.StopIf) {
                val comparison = RuntimeStep.CompareValues(
                    blockId = action.blockId,
                    left = action.left,
                    operator = action.operator,
                    right = action.right,
                )
                when (val result = evaluateValueCondition(comparison, context)) {
                    ConditionResult.Passed -> {
                        diagnostics.record(
                            macroId = macroId,
                            kind = RuntimeDiagnosticKind.ACTION_SUCCEEDED,
                            runId = runId,
                            blockId = action.blockId,
                            message = "Comparison passed; stopped before later actions.",
                        )
                        return true
                    }
                    is ConditionResult.Blocked -> {
                        diagnostics.record(
                            macroId = macroId,
                            kind = RuntimeDiagnosticKind.ACTION_SUCCEEDED,
                            runId = runId,
                            blockId = action.blockId,
                            message = "Comparison did not pass; continued.",
                        )
                        continue
                    }
                    is ConditionResult.Failed -> {
                        diagnostics.record(
                            macroId = macroId,
                            kind = RuntimeDiagnosticKind.ACTION_FAILED,
                            runId = runId,
                            blockId = action.blockId,
                            message = result.message,
                        )
                        return false
                    }
                    null -> error("Stop-if must compile to a value comparison.")
                }
            }
            if (action is RuntimeStep.Delay) {
                when (cancellation.await(action.durationMillis)) {
                    DelayWaitResult.COMPLETED -> diagnostics.record(
                        macroId = macroId,
                        kind = RuntimeDiagnosticKind.ACTION_SUCCEEDED,
                        runId = runId,
                        blockId = action.blockId,
                        message = "Delay completed.",
                    )
                    DelayWaitResult.CANCELLED -> {
                        recordCancellation(macroId, runId)
                        return false
                    }
                }
                continue
            }
            val result = try {
                actionExecutor.execute(action, context)
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
        reason: EnableFailureReason = EnableFailureReason.GENERAL,
    ): RuntimeLifecycleResult.EnableFailed {
        diagnostics.record(
            macroId = macroId,
            kind = RuntimeDiagnosticKind.ENABLE_FAILED,
            message = message,
        )
        return RuntimeLifecycleResult.EnableFailed(message, missingPermissions, reason)
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
        val runCancellation: RuntimeRunCancellation,
        var executing: Boolean = false,
    )

    private data class RunStart(
        val runId: Long,
        val plan: RuntimePlan,
        val cancellation: RuntimeRunCancellation,
    )

    private data class ScheduleDeliveryTarget(
        val generation: Long,
        val trigger: RuntimeStep.ObserveSchedule,
    )
}

private class RuntimeRunCancellation {
    private val lock = Object()
    private var cancelled = false

    fun cancel() {
        synchronized(lock) {
            cancelled = true
            lock.notifyAll()
        }
    }

    fun await(durationMillis: Long): DelayWaitResult {
        val deadline = java.lang.System.nanoTime() +
            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(durationMillis)
        synchronized(lock) {
            while (!cancelled) {
                val remainingNanos = deadline - java.lang.System.nanoTime()
                if (remainingNanos <= 0) {
                    return DelayWaitResult.COMPLETED
                }
                val millis = java.util.concurrent.TimeUnit.NANOSECONDS
                    .toMillis(remainingNanos)
                    .coerceAtLeast(1L)
                lock.wait(millis)
            }
            return DelayWaitResult.CANCELLED
        }
    }
}

private enum class DelayWaitResult {
    COMPLETED,
    CANCELLED,
}

sealed interface RuntimeLifecycleResult {
    data class Enabled(
        val revisionId: String,
        val triggerCount: Int,
    ) : RuntimeLifecycleResult

    data class EnableFailed(
        val message: String,
        val missingPermissions: Set<AndroidPermission> = emptySet(),
        val reason: EnableFailureReason = EnableFailureReason.GENERAL,
    ) : RuntimeLifecycleResult

    data object Disabled : RuntimeLifecycleResult

    data object AlreadyDisabled : RuntimeLifecycleResult
}

enum class EnableFailureReason {
    NO_APPROVED_SNAPSHOT,
    APPROVED_PLAN_UNAVAILABLE,
    INVALID_APPROVED_PLAN,
    MISSING_PERMISSIONS,
    SUBSCRIPTION_FAILED,
    STATE_PERSISTENCE_FAILED,
    GENERAL,
}

data class RuntimeRestoreSummary(
    val resultsByMacroId: Map<String, RuntimeLifecycleResult>,
) {
    val restoredMacroIds: Set<String>
        get() = resultsByMacroId
            .filterValues { it is RuntimeLifecycleResult.Enabled }
            .keys

    val failedMacroIds: Set<String>
        get() = resultsByMacroId
            .filterValues { it is RuntimeLifecycleResult.EnableFailed }
            .keys
}
