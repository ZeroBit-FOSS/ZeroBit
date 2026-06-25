/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import android.content.Context
import android.util.Base64
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import java.math.BigDecimal
import java.security.MessageDigest

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

/**
 * Durable app-private storage for non-secret primitive macro variables.
 *
 * Each macro gets its own preference file, so clearing one macro does not scan
 * or rewrite unrelated runtime state.
 */
class AndroidPreferencesVariableStore(
    context: Context,
    private val filePrefix: String = "zerobit_variables_",
) : VariableStore {
    private val appContext = context.applicationContext

    override fun getValue(macroId: String, name: String): MacroValue? {
        val encoded = preferences(macroId).getString(name, null) ?: return null
        return decode(encoded)
    }

    override fun setValue(macroId: String, name: String, value: MacroValue) {
        val encoded = encode(value)
        preferences(macroId).edit().putString(name, encoded).apply()
    }

    override fun clear(macroId: String) {
        preferences(macroId).edit().clear().apply()
    }

    private fun preferences(macroId: String) =
        appContext.getSharedPreferences(filePrefix + sha256(macroId), Context.MODE_PRIVATE)

    private fun encode(value: MacroValue): String = when (value) {
        is MacroValue.Text ->
            "text:" + Base64.encodeToString(
                value.value.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP,
            )
        is MacroValue.Number -> "number:${value.value.toPlainString()}"
        is MacroValue.Boolean -> "boolean:${value.value}"
        is MacroValue.ListValue,
        is MacroValue.ObjectValue,
        MacroValue.Null -> throw IllegalArgumentException(
            "Runtime variables support only text, number, and boolean values.",
        )
    }

    private fun decode(encoded: String): MacroValue? {
        val separator = encoded.indexOf(':')
        if (separator <= 0) {
            return null
        }
        val type = encoded.substring(0, separator)
        val value = encoded.substring(separator + 1)
        return try {
            when (type) {
                "text" -> MacroValue.Text(
                    String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8),
                )
                "number" -> MacroValue.Number(BigDecimal(value))
                "boolean" -> MacroValue.Boolean(value.toBooleanStrict())
                else -> null
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
