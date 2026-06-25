/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.io.Closeable

/**
 * Gives the runtime one explicit lifecycle owner.
 */
class RuntimeOwner(
    val coordinator: RuntimeCoordinator,
    private val ownedResources: List<Closeable> = emptyList(),
) : Closeable {
    private var closed = false

    @Synchronized
    fun restoreEnabledMacros(): RuntimeRestoreSummary {
        check(!closed) { "Runtime owner is closed." }
        return coordinator.restoreEnabledMacros()
    }

    @Synchronized
    fun deliverScheduleAlarm(
        macroId: String,
        blockId: String,
        occurrence: java.time.Instant,
    ): Boolean {
        check(!closed) { "Runtime owner is closed." }
        return coordinator.deliverScheduleAlarm(macroId, blockId, occurrence)
    }

    @Synchronized
    fun enable(macroId: String): RuntimeLifecycleResult {
        check(!closed) { "Runtime owner is closed." }
        return coordinator.enable(macroId)
    }

    @Synchronized
    fun disable(macroId: String): RuntimeLifecycleResult {
        check(!closed) { "Runtime owner is closed." }
        return coordinator.disable(macroId)
    }

    @Synchronized
    fun isEnabled(macroId: String): Boolean {
        check(!closed) { "Runtime owner is closed." }
        return coordinator.isEnabled(macroId)
    }

    @Synchronized
    fun status(macroId: String): RuntimeMacroStatus? {
        check(!closed) { "Runtime owner is closed." }
        return coordinator.status(macroId)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        coordinator.disableAll()
        ownedResources.asReversed().forEach { resource ->
            runCatching(resource::close)
        }
    }
}
