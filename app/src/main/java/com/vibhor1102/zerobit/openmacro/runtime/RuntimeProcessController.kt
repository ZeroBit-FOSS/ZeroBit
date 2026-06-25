/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.io.Closeable

/**
 * One process-level entry point for restoring and closing the local runtime.
 *
 * Android lifecycle code should own this controller rather than calling the
 * coordinator from activities, receivers, or services independently.
 */
class RuntimeProcessController(
    private val owner: RuntimeOwner,
) : Closeable {
    private val lock = Any()
    private var state: RuntimeProcessState = RuntimeProcessState.NotStarted

    fun start(): RuntimeProcessState = synchronized(lock) {
        when (val current = state) {
            RuntimeProcessState.NotStarted -> {
                val restored = owner.restoreEnabledMacros()
                RuntimeProcessState.Started(restored).also { state = it }
            }
            is RuntimeProcessState.Started -> current
            RuntimeProcessState.Closed ->
                throw IllegalStateException("Runtime process controller is closed.")
        }
    }

    fun currentState(): RuntimeProcessState = synchronized(lock) { state }

    fun retryDesiredMacros(): RuntimeProcessState.Started = synchronized(lock) {
        if (state == RuntimeProcessState.Closed) {
            throw IllegalStateException("Runtime process controller is closed.")
        }
        val restored = owner.restoreEnabledMacros()
        RuntimeProcessState.Started(restored).also { state = it }
    }

    fun enable(macroId: String): RuntimeLifecycleResult = synchronized(lock) {
        ensureStarted()
        owner.enable(macroId)
    }

    fun disable(macroId: String): RuntimeLifecycleResult = synchronized(lock) {
        ensureStarted()
        owner.disable(macroId)
    }

    fun isEnabled(macroId: String): Boolean = synchronized(lock) {
        ensureStarted()
        owner.isEnabled(macroId)
    }

    fun status(macroId: String): RuntimeMacroStatus? = synchronized(lock) {
        ensureStarted()
        owner.status(macroId)
    }

    fun deliverScheduleAlarm(
        macroId: String,
        blockId: String,
        occurrence: java.time.Instant,
    ): Boolean = synchronized(lock) {
        when (state) {
            RuntimeProcessState.NotStarted -> start()
            is RuntimeProcessState.Started -> Unit
            RuntimeProcessState.Closed ->
                throw IllegalStateException("Runtime process controller is closed.")
        }
        owner.deliverScheduleAlarm(macroId, blockId, occurrence)
    }

    override fun close() {
        synchronized(lock) {
            if (state == RuntimeProcessState.Closed) {
                return
            }
            owner.close()
            state = RuntimeProcessState.Closed
        }
    }

    private fun ensureStarted() {
        when (state) {
            RuntimeProcessState.NotStarted -> start()
            is RuntimeProcessState.Started -> Unit
            RuntimeProcessState.Closed ->
                throw IllegalStateException("Runtime process controller is closed.")
        }
    }
}

sealed interface RuntimeProcessState {
    data object NotStarted : RuntimeProcessState

    data class Started(
        val restoration: RuntimeRestoreSummary,
    ) : RuntimeProcessState

    data object Closed : RuntimeProcessState
}
