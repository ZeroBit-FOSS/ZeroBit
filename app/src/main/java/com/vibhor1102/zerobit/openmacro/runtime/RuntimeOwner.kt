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
    override fun close() {
        if (closed) return
        closed = true
        coordinator.disableAll()
        ownedResources.asReversed().forEach { resource ->
            runCatching(resource::close)
        }
    }
}
