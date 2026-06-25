/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.storage.FakeSecretStore
import com.vibhor1102.zerobit.openmacro.storage.InMemoryEnabledMacroStore
import com.vibhor1102.zerobit.openmacro.storage.InMemoryVariableStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class RuntimeProcessControllerTest {
    @Test
    fun restoresOnceAndOwnsRuntimeShutdown() {
        val enabled = InMemoryEnabledMacroStore(setOf("macro"))
        var subscriptionCancelled = false
        val plan = RuntimePlan(
            macroId = "macro",
            sourceFingerprint = "sha256:test",
            triggers = listOf(RuntimeStep.ObservePowerConnected("power")),
            conditions = emptyList(),
            actions = listOf(RuntimeStep.StopActions("stop")),
            requiredPermissions = emptySet(),
        )
        val coordinator = RuntimeCoordinator(
            approvedPlans = ApprovedPlanProvider {
                ApprovedPlanResult.Success("revision", plan)
            },
            triggerRegistrar = RuntimeTriggerRegistrar { _, _, _ ->
                TriggerSubscriptionResult.Success(
                    RuntimeCancellation { subscriptionCancelled = true },
                )
            },
            conditionEvaluator = RuntimeConditionEvaluator { _, _ ->
                ConditionResult.Passed
            },
            actionExecutor = RuntimeActionExecutor { _, _ ->
                ActionResult.Succeeded
            },
            permissionChecker = RuntimePermissionChecker {
                emptySet<AndroidPermission>()
            },
            dispatcher = RuntimeTaskDispatcher { it() },
            variables = InMemoryVariableStore(),
            secrets = FakeSecretStore(),
            diagnostics = BoundedRuntimeDiagnostics(),
            enabledState = enabled,
        )
        val controller = RuntimeProcessController(RuntimeOwner(coordinator))

        val first = controller.start()
        val second = controller.start()

        assertSame(first, second)
        require(first is RuntimeProcessState.Started)
        assertEquals(setOf("macro"), first.restoration.restoredMacroIds)

        controller.close()

        assertTrue(subscriptionCancelled)
        assertEquals(setOf("macro"), enabled.enabledMacroIds())
        assertEquals(RuntimeProcessState.Closed, controller.currentState())
    }

    @Test
    fun scheduleDeliveryStartsControllerBeforeRoutingAlarm() {
        var event: RuntimeTriggerEvent? = null
        val plan = RuntimePlan(
            macroId = "macro",
            sourceFingerprint = "sha256:schedule",
            triggers = listOf(
                RuntimeStep.ObserveSchedule(
                    "morning",
                    ScheduleSpec(LocalTime.of(9, 0), zoneId = ZoneId.of("UTC")),
                ),
            ),
            conditions = emptyList(),
            actions = listOf(RuntimeStep.WriteLog("log", "done")),
            requiredPermissions = emptySet(),
        )
        val coordinator = RuntimeCoordinator(
            approvedPlans = ApprovedPlanProvider {
                ApprovedPlanResult.Success("revision", plan)
            },
            triggerRegistrar = RuntimeTriggerRegistrar { _, _, _ ->
                TriggerSubscriptionResult.Success(RuntimeCancellation {})
            },
            conditionEvaluator = RuntimeConditionEvaluator { _, _ ->
                ConditionResult.Passed
            },
            actionExecutor = RuntimeActionExecutor { _, context ->
                event = context.trigger
                ActionResult.Succeeded
            },
            permissionChecker = RuntimePermissionChecker { emptySet() },
            dispatcher = RuntimeTaskDispatcher { it() },
            variables = InMemoryVariableStore(),
            secrets = FakeSecretStore(),
            diagnostics = BoundedRuntimeDiagnostics(),
            enabledState = InMemoryEnabledMacroStore(setOf("macro")),
        )
        val controller = RuntimeProcessController(RuntimeOwner(coordinator))

        assertTrue(
            controller.deliverScheduleAlarm(
                "macro",
                "morning",
                Instant.parse("2026-06-22T09:00:00Z"),
            ),
        )

        assertEquals(
            MacroValue.Text("2026-06-22T09:00:00Z"),
            event?.values?.get("schedule.instant"),
        )
        controller.close()
    }

    @Test
    fun retryRechecksDesiredMacrosAfterRecoverableFailure() {
        var approved = false
        val enabled = InMemoryEnabledMacroStore(setOf("macro"))
        val plan = RuntimePlan(
            macroId = "macro",
            sourceFingerprint = "sha256:retry",
            triggers = listOf(RuntimeStep.ObservePowerConnected("power")),
            conditions = emptyList(),
            actions = listOf(RuntimeStep.StopActions("stop")),
            requiredPermissions = emptySet(),
        )
        val coordinator = RuntimeCoordinator(
            approvedPlans = ApprovedPlanProvider {
                if (approved) {
                    ApprovedPlanResult.Success("revision", plan)
                } else {
                    ApprovedPlanResult.Missing
                }
            },
            triggerRegistrar = RuntimeTriggerRegistrar { _, _, _ ->
                TriggerSubscriptionResult.Success(RuntimeCancellation {})
            },
            conditionEvaluator = RuntimeConditionEvaluator { _, _ ->
                ConditionResult.Passed
            },
            actionExecutor = RuntimeActionExecutor { _, _ -> ActionResult.Succeeded },
            permissionChecker = RuntimePermissionChecker { emptySet() },
            dispatcher = RuntimeTaskDispatcher { it() },
            variables = InMemoryVariableStore(),
            secrets = FakeSecretStore(),
            diagnostics = BoundedRuntimeDiagnostics(),
            enabledState = enabled,
        )
        val controller = RuntimeProcessController(RuntimeOwner(coordinator))

        val first = controller.start() as RuntimeProcessState.Started
        assertEquals(setOf("macro"), first.restoration.failedMacroIds)

        approved = true
        val retried = controller.retryDesiredMacros()

        assertEquals(setOf("macro"), retried.restoration.restoredMacroIds)
        assertTrue(coordinator.isEnabled("macro"))
        controller.close()
    }
}
