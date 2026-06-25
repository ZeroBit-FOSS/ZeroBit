/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.model.MacroValue

interface VariableStore {
    fun getValue(macroId: String, name: String): MacroValue?
    fun setValue(macroId: String, name: String, value: MacroValue)
    fun clear(macroId: String)
}

class InMemoryVariableStore : VariableStore {
    private val lock = Any()
    private val store = mutableMapOf<String, MutableMap<String, MacroValue>>()

    override fun getValue(macroId: String, name: String): MacroValue? = synchronized(lock) {
        store[macroId]?.get(name)
    }

    override fun setValue(macroId: String, name: String, value: MacroValue) {
        synchronized(lock) {
            store.getOrPut(macroId) { mutableMapOf() }[name] = value
        }
    }

    override fun clear(macroId: String) {
        synchronized(lock) {
            store.remove(macroId)
        }
    }
}
