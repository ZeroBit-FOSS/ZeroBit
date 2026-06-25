/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.storage.ApprovalStoreResult
import com.vibhor1102.zerobit.openmacro.storage.ApprovedRevision

data class RuntimeTriggerEvent(
    val values: Map<String, MacroValue> = emptyMap(),
)

data class RuntimeContext(
    val macroId: String,
    val runId: Long,
    val triggerBlockId: String,
    val trigger: RuntimeTriggerEvent,
    val values: RuntimeValues,
)

fun interface RuntimeTaskDispatcher {
    fun dispatch(task: () -> Unit)
}

fun interface RuntimeCancellation {
    fun cancel()
}

fun interface RuntimeTriggerRegistrar {
    fun subscribe(
        macroId: String,
        trigger: RuntimeStep,
        onTriggered: (RuntimeTriggerEvent) -> Unit,
    ): TriggerSubscriptionResult
}

sealed interface TriggerSubscriptionResult {
    data class Success(
        val cancellation: RuntimeCancellation,
    ) : TriggerSubscriptionResult

    data class Failure(
        val message: String,
    ) : TriggerSubscriptionResult
}

fun interface RuntimeConditionEvaluator {
    fun evaluate(condition: RuntimeStep, context: RuntimeContext): ConditionResult
}

sealed interface ConditionResult {
    data object Passed : ConditionResult

    data class Blocked(val reason: String) : ConditionResult

    data class Failed(val message: String) : ConditionResult
}

fun interface RuntimeActionExecutor {
    fun execute(action: RuntimeStep, context: RuntimeContext): ActionResult
}

sealed interface ActionResult {
    data object Succeeded : ActionResult

    data class Failed(val message: String) : ActionResult
}


fun interface RuntimePermissionChecker {
    fun missingPermissions(required: Set<AndroidPermission>): Set<AndroidPermission>
}

fun interface ApprovedPlanProvider {
    fun loadCurrent(macroId: String): ApprovedPlanResult
}

sealed interface ApprovedPlanResult {
    data class Success(
        val revisionId: String,
        val plan: RuntimePlan,
    ) : ApprovedPlanResult

    data object Missing : ApprovedPlanResult

    data class Failure(
        val message: String,
    ) : ApprovedPlanResult
}

class ApprovalStorePlanProvider(
    private val load: (String) -> ApprovalStoreResult<ApprovedRevision?>,
) : ApprovedPlanProvider {
    override fun loadCurrent(macroId: String): ApprovedPlanResult =
        when (val result = load(macroId)) {
            is ApprovalStoreResult.Failure -> ApprovedPlanResult.Failure(result.message)
            is ApprovalStoreResult.Success -> result.value?.let {
                ApprovedPlanResult.Success(
                    revisionId = it.revisionId,
                    plan = it.snapshot.runtimePlan,
                )
            } ?: ApprovedPlanResult.Missing
        }
}
