/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import android.content.Context

interface SecretStore {
    fun getSecret(key: String): String?
    fun setSecret(key: String, value: String)
    fun deleteSecret(key: String)
}

class AndroidSharedPrefsSecretStore(
    context: Context,
    prefsName: String = "zerobit_secrets"
) : SecretStore {
    private val sharedPrefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun getSecret(key: String): String? {
        return sharedPrefs.getString(key, null)
    }

    override fun setSecret(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    override fun deleteSecret(key: String) {
        sharedPrefs.edit().remove(key).apply()
    }
}

class FakeSecretStore : SecretStore {
    private val secrets = mutableMapOf<String, String>()

    override fun getSecret(key: String): String? = secrets[key]

    override fun setSecret(key: String, value: String) {
        secrets[key] = value
    }

    override fun deleteSecret(key: String) {
        secrets.remove(key)
    }
}
