/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCoordinatorTest {
    @Test
    fun enablesApprovedPlanAndRunsConditionsThenActions() {
        val fixture = Fixture()

        val enabled = fixture.coordinator.enable("charger-greeting")
        fixture.registrar.fire("charger-connected")

        assertEquals(RuntimeLifecycleResult.Enabled("revision-1", 1), enabled)
        assertEquals(listOf("device-unlocked"), fixture.conditions.evaluatedBlockIds)
        assertEquals(listOf("show-message"), fixture.actions.executedBlockIds)
        assertEquals(
            listOf(
                RuntimeDiagnosticKind.ENABLED,
                RuntimeDiagnosticKind.TRIGGER_RECEIVED,
                RuntimeDiagnosticKind.CONDITION_PASSED,
                RuntimeDiagnosticKind.ACTION_SUCCEEDED,
                RuntimeDiagnosticKind.RUN_SUCCEEDED,
            ),
            fixture.diagnostics.snapshot().map { it.kind },
        )
        val runEvents = fixture.diagnostics.snapshot().filter { it.runId != null }
        assertEquals(1, runEvents.map { it.runId }.distinct().size)
    }

    @Test
    fun refusesMissingApprovalOrPermissionBeforeSubscribing() {
        val noApproval = Fixture(planResult = ApprovedPlanResult.Missing)
        val missingResult = noApproval.coordinator.enable("charger-greeting")
        require(missingResult is RuntimeLifecycleResult.EnableFailed)
        assertTrue(missingResult.message.contains("No approved snapshot"))
        assertTrue(noApproval.registrar.callbacks.isEmpty())

        val missingPermission = Fixture(
            missingPermissions = setOf(AndroidPermission.POST_NOTIFICATIONS),
        )
        val permissionResult = missingPermission.coordinator.enable("charger-greeting")
        require(permissionResult is RuntimeLifecycleResult.EnableFailed)
        assertEquals(
            setOf(AndroidPermission.POST_NOTIFICATIONS),
            permissionResult.missingPermissions,
        )
        assertTrue(missingPermission.registrar.callbacks.isEmpty())
    }

    @Test
    fun blockedConditionExplainsWhyActionsDidNotRun() {
        val fixture = Fixture()
        fixture.conditions.result = ConditionResult.Blocked("The phone is locked.")
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertTrue(fixture.actions.executedBlockIds.isEmpty())
        assertEquals(
            RuntimeDiagnosticKind.CONDITION_BLOCKED,
            fixture.diagnostics.snapshot().last().kind,
        )
        assertEquals("The phone is locked.", fixture.diagnostics.snapshot().last().message)
    }

    @Test
    fun failedActionStopsLaterActionsAndIsContained() {
        val fixture = Fixture(plan = planWithTwoActions())
        fixture.actions.results["first-action"] = ActionResult.Failed("Notifications are unavailable.")
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertEquals(listOf("first-action"), fixture.actions.executedBlockIds)
        assertEquals(
            RuntimeDiagnosticKind.ACTION_FAILED,
            fixture.diagnostics.snapshot().last().kind,
        )
    }

    @Test
    fun queuedTriggerBecomesHarmlessAfterDisable() {
        val dispatcher = ManualDispatcher()
        val fixture = Fixture(dispatcher = dispatcher)
        fixture.coordinator.enable("charger-greeting")
        fixture.registrar.fire("charger-connected")

        assertEquals(RuntimeLifecycleResult.Disabled, fixture.coordinator.disable("charger-greeting"))
        dispatcher.runAll()

        assertFalse(fixture.coordinator.isEnabled("charger-greeting"))
        assertTrue(fixture.conditions.evaluatedBlockIds.isEmpty())
        assertTrue(fixture.actions.executedBlockIds.isEmpty())
        assertTrue(fixture.registrar.cancelledBlockIds.contains("charger-connected"))
    }

    @Test
    fun overlappingTriggerIsIgnoredWhileMacroIsRunning() {
        val fixture = Fixture()
        fixture.actions.onExecute = {
            fixture.registrar.fire("charger-connected")
        }
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertEquals(listOf("show-message"), fixture.actions.executedBlockIds)
        assertTrue(
            fixture.diagnostics.snapshot()
                .any { it.kind == RuntimeDiagnosticKind.TRIGGER_IGNORED_BUSY },
        )
    }

    @Test
    fun disableDuringRunStopsBeforeTheNextAction() {
        val fixture = Fixture(plan = planWithTwoActions())
        fixture.actions.onExecute = { action ->
            if (action.blockId == "first-action") {
                fixture.coordinator.disable("charger-greeting")
            }
        }
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertEquals(listOf("first-action"), fixture.actions.executedBlockIds)
        assertEquals(
            RuntimeDiagnosticKind.RUN_CANCELLED,
            fixture.diagnostics.snapshot().last().kind,
        )
    }

    @Test
    fun dispatcherFailureIsContainedAndDiagnosed() {
        val fixture = Fixture(
            dispatcher = RuntimeTaskDispatcher {
                throw IllegalStateException("Executor is closed.")
            },
        )
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertEquals(
            RuntimeDiagnosticKind.TRIGGER_DISPATCH_FAILED,
            fixture.diagnostics.snapshot().last().kind,
        )
        assertTrue(fixture.actions.executedBlockIds.isEmpty())
    }

    @Test
    fun failedReenableKeepsExistingSessionAlive() {
        val fixture = Fixture()
        fixture.coordinator.enable("charger-greeting")
        fixture.registrar.failSubscriptions = true

        val result = fixture.coordinator.enable("charger-greeting")
        fixture.registrar.fire("charger-connected")

        assertTrue(result is RuntimeLifecycleResult.EnableFailed)
        assertTrue(fixture.coordinator.isEnabled("charger-greeting"))
        assertEquals(listOf("show-message"), fixture.actions.executedBlockIds)
    }

    @Test
    fun successfulReenableReplacesOldSubscriptionsWithoutCancellingNewOnes() {
        val fixture = Fixture()
        fixture.coordinator.enable("charger-greeting")

        val result = fixture.coordinator.enable("charger-greeting")
        fixture.registrar.fire("charger-connected")

        assertTrue(result is RuntimeLifecycleResult.Enabled)
        assertEquals(listOf("show-message"), fixture.actions.executedBlockIds)
        assertEquals(1, fixture.registrar.callbacks.size)
        assertEquals(listOf("charger-connected"), fixture.registrar.cancelledBlockIds)
    }

    @Test
    fun partialEnableFailureCancelsOnlyNewSubscriptions() {
        val fixture = Fixture(plan = planWithTwoTriggers())
        fixture.registrar.failOnBlockId = "second-trigger"

        val result = fixture.coordinator.enable("charger-greeting")

        assertTrue(result is RuntimeLifecycleResult.EnableFailed)
        assertFalse(fixture.coordinator.isEnabled("charger-greeting"))
        assertTrue(fixture.registrar.callbacks.isEmpty())
        assertEquals(listOf("first-trigger"), fixture.registrar.cancelledBlockIds)
    }

    @Test
    fun diagnosticsAreBoundedAndMessagesAreTruncated() {
        val diagnostics = BoundedRuntimeDiagnostics(
            capacity = 2,
            clock = RuntimeClock { 123L },
        )
        repeat(3) { index ->
            diagnostics.record(
                macroId = "macro",
                kind = RuntimeDiagnosticKind.ENABLE_FAILED,
                message = if (index == 2) "x".repeat(600) else "event-$index",
            )
        }

        val events = diagnostics.snapshot()

        assertEquals(2, events.size)
        assertEquals(listOf(2L, 3L), events.map { it.sequence })
        assertEquals(BoundedRuntimeDiagnostics.MAX_MESSAGE_LENGTH, events.last().message.length)
    }

    @Test
    fun runtimeOwnerCancelsAllSubscriptionsAndOwnedResources() {
        val fixture = Fixture()
        fixture.coordinator.enable("charger-greeting")
        var resourceClosed = false
        val owner = RuntimeOwner(
            coordinator = fixture.coordinator,
            ownedResources = listOf(java.io.Closeable { resourceClosed = true }),
        )

        owner.close()
        owner.close()

        assertTrue(resourceClosed)
        assertTrue(fixture.coordinator.enabledMacroIds().isEmpty())
        assertEquals(listOf("charger-connected"), fixture.registrar.cancelledBlockIds)
    }

    @Test
    fun initializesVariablesOnEnableAndProvidesThemToExecutor() {
        val varDeclaration = com.vibhor1102.zerobit.openmacro.model.MacroVariable(
            name = "my_var",
            type = com.vibhor1102.zerobit.openmacro.model.MacroVariableType.TEXT,
            initialValue = com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("init_val")
        )
        val plan = validPlan().copy(variables = listOf(varDeclaration))
        val fixture = Fixture(plan = plan)

        var contextCaptured: RuntimeContext? = null
        fixture.actions.onExecuteWithContext = { action, context ->
            contextCaptured = context
        }

        fixture.coordinator.enable("charger-greeting")
        assertEquals(
            com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("init_val"),
            fixture.variables.getValue("charger-greeting", "my_var")
        )

        fixture.registrar.fire("charger-connected")

        val captured = checkNotNull(contextCaptured)
        assertEquals("charger-greeting", captured.macroId)
        assertEquals(
            com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("init_val"),
            captured.variables.getValue("charger-greeting", "my_var")
        )
    }

    private class Fixture(
        plan: RuntimePlan = validPlan(),
        planResult: ApprovedPlanResult = ApprovedPlanResult.Success("revision-1", plan),
        missingPermissions: Set<AndroidPermission> = emptySet(),
        dispatcher: RuntimeTaskDispatcher = RuntimeTaskDispatcher { it() },
    ) {
        val registrar = FakeTriggerRegistrar()
        val conditions = FakeConditionEvaluator()
        val actions = FakeActionExecutor()
        val variables = com.vibhor1102.zerobit.openmacro.storage.InMemoryVariableStore()
        val secrets = com.vibhor1102.zerobit.openmacro.storage.FakeSecretStore()
        val diagnostics = BoundedRuntimeDiagnostics(clock = RuntimeClock { 1_000L })
        val coordinator = RuntimeCoordinator(
            approvedPlans = ApprovedPlanProvider { planResult },
            triggerRegistrar = registrar,
            conditionEvaluator = conditions,
            actionExecutor = actions,
            permissionChecker = RuntimePermissionChecker { missingPermissions },
            dispatcher = dispatcher,
            variables = variables,
            secrets = secrets,
            diagnostics = diagnostics,
        )
    }

    private class FakeTriggerRegistrar : RuntimeTriggerRegistrar {
        val callbacks = linkedMapOf<String, () -> Unit>()
        val cancelledBlockIds = mutableListOf<String>()
        var failSubscriptions = false
        var failOnBlockId: String? = null

        override fun subscribe(
            macroId: String,
            trigger: RuntimeStep,
            onTriggered: () -> Unit,
        ): TriggerSubscriptionResult {
            if (failSubscriptions || failOnBlockId == trigger.blockId) {
                return TriggerSubscriptionResult.Failure("Receiver registration failed.")
            }
            callbacks[trigger.blockId] = onTriggered
            return TriggerSubscriptionResult.Success(
                RuntimeCancellation {
                    callbacks.remove(trigger.blockId, onTriggered)
                    cancelledBlockIds += trigger.blockId
                },
            )
        }

        fun fire(blockId: String) {
            callbacks.getValue(blockId).invoke()
        }
    }

    private class FakeConditionEvaluator : RuntimeConditionEvaluator {
        val evaluatedBlockIds = mutableListOf<String>()
        var result: ConditionResult = ConditionResult.Passed

        override fun evaluate(condition: RuntimeStep, context: RuntimeContext): ConditionResult {
            evaluatedBlockIds += condition.blockId
            return result
        }
    }

    private class FakeActionExecutor : RuntimeActionExecutor {
        val executedBlockIds = mutableListOf<String>()
        val results = mutableMapOf<String, ActionResult>()
        var onExecute: ((RuntimeStep) -> Unit)? = null
        var onExecuteWithContext: ((RuntimeStep, RuntimeContext) -> Unit)? = null

        override fun execute(action: RuntimeStep, context: RuntimeContext): ActionResult {
            executedBlockIds += action.blockId
            onExecute?.invoke(action)
            onExecuteWithContext?.invoke(action, context)
            return results[action.blockId] ?: ActionResult.Succeeded
        }
    }


    private class ManualDispatcher : RuntimeTaskDispatcher {
        private val tasks = ArrayDeque<() -> Unit>()

        override fun dispatch(task: () -> Unit) {
            tasks += task
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().invoke()
            }
        }
    }

    companion object {
        private fun validPlan() = RuntimePlan(
            macroId = "charger-greeting",
            sourceFingerprint = "sha256:test",
            triggers = listOf(
                RuntimeStep.ObservePowerConnected("charger-connected"),
            ),
            conditions = listOf(
                RuntimeStep.CheckDeviceUnlocked("device-unlocked"),
            ),
            actions = listOf(
                RuntimeStep.ShowNotification(
                    blockId = "show-message",
                    title = "Charging",
                    message = "Connected",
                ),
            ),
            requiredPermissions = setOf(AndroidPermission.POST_NOTIFICATIONS),
        )

        private fun planWithTwoActions() = validPlan().copy(
            actions = listOf(
                RuntimeStep.ShowNotification(
                    blockId = "first-action",
                    title = "First",
                    message = "First",
                ),
                RuntimeStep.ShowNotification(
                    blockId = "second-action",
                    title = "Second",
                    message = "Second",
                ),
            ),
        )

        private fun planWithTwoTriggers() = validPlan().copy(
            triggers = listOf(
                RuntimeStep.ObservePowerConnected("first-trigger"),
                RuntimeStep.ObservePowerConnected("second-trigger"),
            ),
        )
    }
}
