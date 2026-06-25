/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

class BoundedRuntimeDiagnostics(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: RuntimeClock = RuntimeClock.System,
) {
    private val events = ArrayDeque<RuntimeDiagnosticEvent>(capacity)
    private var nextSequence = 1L

    init {
        require(capacity > 0) { "Diagnostic capacity must be positive." }
    }

    @Synchronized
    fun record(
        macroId: String,
        kind: RuntimeDiagnosticKind,
        runId: Long? = null,
        blockId: String? = null,
        message: String,
    ) {
        if (events.size == capacity) {
            events.removeFirst()
        }
        events.addLast(
            RuntimeDiagnosticEvent(
                sequence = nextSequence++,
                timestampEpochMillis = clock.nowEpochMillis(),
                macroId = macroId,
                runId = runId,
                blockId = blockId,
                kind = kind,
                message = message.take(MAX_MESSAGE_LENGTH),
            ),
        )
    }

    @Synchronized
    fun snapshot(macroId: String? = null): List<RuntimeDiagnosticEvent> =
        events.filter { macroId == null || it.macroId == macroId }

    companion object {
        const val DEFAULT_CAPACITY = 500
        const val MAX_MESSAGE_LENGTH = 500
    }
}

data class RuntimeDiagnosticEvent(
    val sequence: Long,
    val timestampEpochMillis: Long,
    val macroId: String,
    val runId: Long?,
    val blockId: String?,
    val kind: RuntimeDiagnosticKind,
    val message: String,
)

enum class RuntimeDiagnosticKind {
    ENABLED,
    ENABLE_FAILED,
    DISABLED,
    TRIGGER_RECEIVED,
    TRIGGER_DISPATCH_FAILED,
    SCHEDULE_REARM_FAILED,
    TRIGGER_IGNORED_BUSY,
    CONDITION_PASSED,
    CONDITION_BLOCKED,
    CONDITION_FAILED,
    CONDITION_GROUP_PASSED,
    CONDITION_GROUP_BLOCKED,
    ACTION_SUCCEEDED,
    ACTION_FAILED,
    RUN_CANCELLED,
    RUN_SUCCEEDED,
}

fun interface RuntimeClock {
    fun nowEpochMillis(): Long

    data object System : RuntimeClock {
        override fun nowEpochMillis(): Long = java.lang.System.currentTimeMillis()
    }
}
