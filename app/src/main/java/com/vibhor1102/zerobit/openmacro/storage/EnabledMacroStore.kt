/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import android.content.Context

interface EnabledMacroStore {
    fun enabledMacroIds(): Set<String>
    fun setEnabled(macroId: String, enabled: Boolean)
}

class InMemoryEnabledMacroStore(
    initialIds: Set<String> = emptySet(),
) : EnabledMacroStore {
    private val lock = Any()
    private val ids = initialIds.toMutableSet()

    override fun enabledMacroIds(): Set<String> = synchronized(lock) {
        ids.toSet()
    }

    override fun setEnabled(macroId: String, enabled: Boolean) {
        synchronized(lock) {
            if (enabled) {
                ids += macroId
            } else {
                ids -= macroId
            }
        }
    }
}

class AndroidEnabledMacroStore(
    context: Context,
    preferencesName: String = "zerobit_runtime_state",
) : EnabledMacroStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    override fun enabledMacroIds(): Set<String> = synchronized(lock) {
        preferences.getStringSet(KEY_ENABLED_MACROS, emptySet()).orEmpty().toSet()
    }

    override fun setEnabled(macroId: String, enabled: Boolean) {
        synchronized(lock) {
            val updated = preferences
                .getStringSet(KEY_ENABLED_MACROS, emptySet())
                .orEmpty()
                .toMutableSet()
                .apply {
                    if (enabled) add(macroId) else remove(macroId)
                }
            preferences.edit().putStringSet(KEY_ENABLED_MACROS, updated).apply()
        }
    }

    private companion object {
        const val KEY_ENABLED_MACROS = "enabled_macro_ids"
    }
}
