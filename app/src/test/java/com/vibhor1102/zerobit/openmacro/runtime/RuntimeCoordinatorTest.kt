/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.storage.InMemoryEnabledMacroStore
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
        assertEquals(
            RuntimeMacroStatus(
                macroId = "charger-greeting",
                revisionId = "revision-1",
                sourceFingerprint = "sha256:test",
                triggerCount = 1,
                executing = false,
            ),
            fixture.coordinator.status("charger-greeting"),
        )
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
    fun successfulEnableAndManualDisableUpdateDurableDesiredState() {
        val fixture = Fixture()

        fixture.coordinator.enable("charger-greeting")
        assertEquals(setOf("charger-greeting"), fixture.enabledState.enabledMacroIds())

        fixture.coordinator.disable("charger-greeting")
        assertTrue(fixture.enabledState.enabledMacroIds().isEmpty())
    }

    @Test
    fun restoresPersistedMacrosAndKeepsFailedRequestsForLaterRecovery() {
        val restoredState = InMemoryEnabledMacroStore(setOf("charger-greeting"))
        val restored = Fixture(enabledState = restoredState)

        val success = restored.coordinator.restoreEnabledMacros()

        assertEquals(setOf("charger-greeting"), success.restoredMacroIds)
        assertTrue(success.failedMacroIds.isEmpty())

        val failedState = InMemoryEnabledMacroStore(setOf("charger-greeting"))
        val failed = Fixture(
            planResult = ApprovedPlanResult.Missing,
            enabledState = failedState,
        )

        val failure = failed.coordinator.restoreEnabledMacros()

        assertEquals(setOf("charger-greeting"), failure.failedMacroIds)
        assertEquals(setOf("charger-greeting"), failedState.enabledMacroIds())
    }

    @Test
    fun runtimeOwnerShutdownDoesNotForgetDesiredEnabledState() {
        val fixture = Fixture()
        fixture.coordinator.enable("charger-greeting")
        val owner = RuntimeOwner(fixture.coordinator)

        owner.close()

        assertEquals(setOf("charger-greeting"), fixture.enabledState.enabledMacroIds())
        assertFalse(fixture.coordinator.isEnabled("charger-greeting"))
    }

    @Test
    fun runtimeOwnerProvidesTheProcessRestorationEntryPoint() {
        val fixture = Fixture(
            enabledState = InMemoryEnabledMacroStore(setOf("charger-greeting")),
        )
        val owner = RuntimeOwner(fixture.coordinator)

        val summary = owner.restoreEnabledMacros()

        assertEquals(setOf("charger-greeting"), summary.restoredMacroIds)
        owner.close()
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
            (captured.values.read("my_var") as RuntimeValueResult.Available).value
        )
    }

    @Test
    fun reenableDoesNotOverwriteAnExistingLocalVariable() {
        val declaration = com.vibhor1102.zerobit.openmacro.model.MacroVariable(
            name = "counter",
            type = com.vibhor1102.zerobit.openmacro.model.MacroVariableType.NUMBER,
            initialValue = MacroValue.Number(java.math.BigDecimal.ZERO),
        )
        val fixture = Fixture(plan = validPlan().copy(variables = listOf(declaration)))
        fixture.coordinator.enable("charger-greeting")
        fixture.variables.setValue(
            "charger-greeting",
            "counter",
            MacroValue.Number(java.math.BigDecimal.TEN),
        )

        fixture.coordinator.enable("charger-greeting")

        assertEquals(
            MacroValue.Number(java.math.BigDecimal.TEN),
            fixture.variables.getValue("charger-greeting", "counter"),
        )
    }

    @Test
    fun passesTriggerValuesToConditionsAndActions() {
        val fixture = Fixture()
        var conditionContext: RuntimeContext? = null
        var actionContext: RuntimeContext? = null
        fixture.conditions.onEvaluateWithContext = { _, context ->
            conditionContext = context
        }
        fixture.actions.onExecuteWithContext = { _, context ->
            actionContext = context
        }
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire(
            blockId = "charger-connected",
            event = RuntimeTriggerEvent(
                values = mapOf(
                    "battery.percentage" to MacroValue.Number(java.math.BigDecimal("42")),
                ),
            ),
        )

        val expected = MacroValue.Number(java.math.BigDecimal("42"))
        assertEquals(expected, conditionContext?.trigger?.values?.get("battery.percentage"))
        assertEquals(expected, actionContext?.trigger?.values?.get("battery.percentage"))
    }

    @Test
    fun executesTarget4CapabilitiesInPlan() {
        val plan = RuntimePlan(
            macroId = "charger-greeting",
            sourceFingerprint = "sha256:test",
            triggers = listOf(
                RuntimeStep.ObserveScreenOn("screen-on-trigger"),
                RuntimeStep.ObserveScreenOff("screen-off-trigger"),
                RuntimeStep.ObserveBatteryLevel("battery-level-trigger", 15, BatteryDirection.GOES_BELOW),
            ),
            conditions = listOf(
                RuntimeStep.CheckWifiConnected("wifi-condition", "MyWifi"),
            ),
            actions = listOf(
                RuntimeStep.WriteLog(
                    "write-log-action",
                    RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("Log message"),
                    ),
                ),
                RuntimeStep.SendSms(
                    "send-sms-action",
                    RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("+12345"),
                    ),
                    RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("Sms body"),
                    ),
                ),
            ),
            requiredPermissions = setOf(AndroidPermission.SEND_SMS, AndroidPermission.ACCESS_NETWORK_STATE),
        )

        val fixture = Fixture(plan = plan, missingPermissions = emptySet())
        fixture.coordinator.enable("charger-greeting")

        // Fire screen on
        fixture.registrar.fire("screen-on-trigger")

        // Assert condition and actions were run
        assertEquals(listOf("wifi-condition"), fixture.conditions.evaluatedBlockIds)
        assertEquals(listOf("write-log-action", "send-sms-action"), fixture.actions.executedBlockIds)
    }

    private class Fixture(
        plan: RuntimePlan = validPlan(),
        planResult: ApprovedPlanResult = ApprovedPlanResult.Success("revision-1", plan),
        missingPermissions: Set<AndroidPermission> = emptySet(),
        dispatcher: RuntimeTaskDispatcher = RuntimeTaskDispatcher { it() },
        val enabledState: InMemoryEnabledMacroStore = InMemoryEnabledMacroStore(),
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
            enabledState = enabledState,
        )
    }

    private class FakeTriggerRegistrar : RuntimeTriggerRegistrar {
        val callbacks = linkedMapOf<String, (RuntimeTriggerEvent) -> Unit>()
        val cancelledBlockIds = mutableListOf<String>()
        var failSubscriptions = false
        var failOnBlockId: String? = null

        override fun subscribe(
            macroId: String,
            trigger: RuntimeStep,
            onTriggered: (RuntimeTriggerEvent) -> Unit,
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

        fun fire(
            blockId: String,
            event: RuntimeTriggerEvent = RuntimeTriggerEvent(),
        ) {
            callbacks.getValue(blockId).invoke(event)
        }
    }

    @Test
    fun anyConditionGroupPassesWhenALaterBranchPasses() {
        val plan = validPlan().copy(
            conditions = emptyList(),
            conditionTree = RuntimeConditionNode.Any(
                listOf(
                    RuntimeConditionNode.Condition(
                        RuntimeStep.CheckWifiConnected("first-condition", "Missing"),
                    ),
                    RuntimeConditionNode.Condition(
                        RuntimeStep.CheckDeviceUnlocked("second-condition"),
                    ),
                ),
            ),
        )
        val fixture = Fixture(plan = plan)
        fixture.conditions.results["first-condition"] =
            ConditionResult.Blocked("First branch did not match.")
        fixture.conditions.results["second-condition"] = ConditionResult.Passed
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertEquals(
            listOf("first-condition", "second-condition"),
            fixture.conditions.evaluatedBlockIds,
        )
        assertEquals(listOf("show-message"), fixture.actions.executedBlockIds)
        assertTrue(
            fixture.diagnostics.snapshot().any {
                it.kind == RuntimeDiagnosticKind.CONDITION_GROUP_PASSED &&
                    it.message.contains("OR group")
            },
        )
    }

    @Test
    fun notConditionInvertsABlockedLeaf() {
        val plan = validPlan().copy(
            conditions = emptyList(),
            conditionTree = RuntimeConditionNode.Not(
                RuntimeConditionNode.Condition(
                    RuntimeStep.CheckWifiConnected("guest-wifi", "Guest"),
                ),
            ),
        )
        val fixture = Fixture(plan = plan)
        fixture.conditions.results["guest-wifi"] =
            ConditionResult.Blocked("Guest Wi-Fi is not connected.")
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertEquals(listOf("show-message"), fixture.actions.executedBlockIds)
        assertTrue(
            fixture.diagnostics.snapshot().any {
                it.kind == RuntimeDiagnosticKind.CONDITION_GROUP_PASSED &&
                    it.message.contains("NOT group")
            },
        )
    }

    @Test
    fun stopActionSkipsLaterActionsAndCompletesSuccessfully() {
        val plan = validPlan().copy(
            actions = listOf(
                RuntimeStep.WriteLog("first", "first"),
                RuntimeStep.StopActions("stop"),
                RuntimeStep.WriteLog("never", "never"),
            ),
        )
        val fixture = Fixture(plan = plan)
        fixture.coordinator.enable("charger-greeting")

        fixture.registrar.fire("charger-connected")

        assertEquals(listOf("first"), fixture.actions.executedBlockIds)
        assertTrue(
            fixture.diagnostics.snapshot().any {
                it.blockId == "stop" &&
                    it.kind == RuntimeDiagnosticKind.ACTION_SUCCEEDED
            },
        )
        assertEquals(
            RuntimeDiagnosticKind.RUN_SUCCEEDED,
            fixture.diagnostics.snapshot().last().kind,
        )
    }

    @Test
    fun disablingMacroWakesAndCancelsLongDelay() {
        val plan = validPlan().copy(
            actions = listOf(
                RuntimeStep.Delay("wait", 60_000),
                RuntimeStep.WriteLog("never", "never"),
            ),
        )
        val fixture = Fixture(plan = plan)
        fixture.coordinator.enable("charger-greeting")

        val run = Thread {
            fixture.registrar.fire("charger-connected")
        }
        run.start()
        while (
            fixture.diagnostics.snapshot().none {
                it.kind == RuntimeDiagnosticKind.TRIGGER_RECEIVED
            }
        ) {
            Thread.yield()
        }
        fixture.coordinator.disable("charger-greeting")
        run.join(2_000)

        assertFalse(run.isAlive)
        assertTrue(fixture.actions.executedBlockIds.isEmpty())
        assertEquals(
            RuntimeDiagnosticKind.RUN_CANCELLED,
            fixture.diagnostics.snapshot().last().kind,
        )
    }

    @Test
    fun stopIfSkipsLaterActionsOnlyWhenComparisonPasses() {
        val stopIf = RuntimeStep.StopIf(
            blockId = "stop-if",
            left = RuntimeValueSource.Trigger("screen.state"),
            operator = ValueComparisonOperator.EQUALS,
            right = RuntimeValueSource.Literal(MacroValue.Text("off")),
        )
        val plan = validPlan().copy(
            actions = listOf(
                stopIf,
                RuntimeStep.WriteLog("later", "later"),
            ),
        )
        val stopped = Fixture(plan = plan)
        stopped.coordinator.enable("charger-greeting")
        stopped.registrar.fire(
            "charger-connected",
            RuntimeTriggerEvent(
                mapOf("screen.state" to MacroValue.Text("off")),
            ),
        )
        assertTrue(stopped.actions.executedBlockIds.isEmpty())

        val continued = Fixture(plan = plan)
        continued.coordinator.enable("charger-greeting")
        continued.registrar.fire(
            "charger-connected",
            RuntimeTriggerEvent(
                mapOf("screen.state" to MacroValue.Text("on")),
            ),
        )
        assertEquals(listOf("later"), continued.actions.executedBlockIds)
    }

    @Test
    fun restoredScheduleAlarmEntersTheSameValidatedTriggerPath() {
        val schedule = ScheduleSpec(
            localTime = java.time.LocalTime.of(9, 0),
            zoneId = java.time.ZoneId.of("Asia/Kolkata"),
        )
        val plan = validPlan().copy(
            triggers = listOf(RuntimeStep.ObserveSchedule("morning", schedule)),
            conditions = emptyList(),
            actions = listOf(RuntimeStep.WriteLog("log", "scheduled")),
        )
        val fixture = Fixture(plan = plan)
        var context: RuntimeContext? = null
        fixture.actions.onExecuteWithContext = { _, seen -> context = seen }
        fixture.coordinator.enable("charger-greeting")

        val delivered = fixture.coordinator.deliverScheduleAlarm(
            macroId = "charger-greeting",
            blockId = "morning",
            occurrence = java.time.Instant.parse("2026-06-22T03:30:00Z"),
        )

        assertTrue(delivered)
        assertEquals("morning", context?.triggerBlockId)
        assertEquals(
            MacroValue.Text("2026-06-22T03:30:00Z"),
            context?.trigger?.values?.get("schedule.instant"),
        )
        assertEquals(
            MacroValue.Text("2026-06-22T09:00+05:30[Asia/Kolkata]"),
            context?.trigger?.values?.get("schedule.local_time"),
        )
        assertFalse(
            fixture.coordinator.deliverScheduleAlarm(
                "charger-greeting",
                "unknown",
                java.time.Instant.EPOCH,
            ),
        )
    }

    private class FakeConditionEvaluator : RuntimeConditionEvaluator {
        val evaluatedBlockIds = mutableListOf<String>()
        var result: ConditionResult = ConditionResult.Passed
        val results = mutableMapOf<String, ConditionResult>()
        var onEvaluateWithContext: ((RuntimeStep, RuntimeContext) -> Unit)? = null

        override fun evaluate(condition: RuntimeStep, context: RuntimeContext): ConditionResult {
            evaluatedBlockIds += condition.blockId
            onEvaluateWithContext?.invoke(condition, context)
            return results[condition.blockId] ?: result
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
                    title = RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("Charging"),
                    ),
                    message = RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("Connected"),
                    ),
                ),
            ),
            requiredPermissions = setOf(AndroidPermission.POST_NOTIFICATIONS),
        )

        private fun planWithTwoActions() = validPlan().copy(
            actions = listOf(
                RuntimeStep.ShowNotification(
                    blockId = "first-action",
                    title = RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("First"),
                    ),
                    message = RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("First"),
                    ),
                ),
                RuntimeStep.ShowNotification(
                    blockId = "second-action",
                    title = RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("Second"),
                    ),
                    message = RuntimeValueSource.Literal(
                        com.vibhor1102.zerobit.openmacro.model.MacroValue.Text("Second"),
                    ),
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
