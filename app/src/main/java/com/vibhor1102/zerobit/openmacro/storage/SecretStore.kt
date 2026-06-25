/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun getSecret(key: String): String?
    fun setSecret(key: String, value: String)
    fun deleteSecret(key: String)
}

/**
 * Stores only encrypted ciphertext in app preferences. The encryption key is
 * non-exportable and remains in Android Keystore.
 */
class AndroidKeystoreSecretStore(
    context: Context,
    prefsName: String = "zerobit_encrypted_secrets",
    private val keyAlias: String = "zerobit.secret-store.v1",
) : SecretStore {
    private val sharedPrefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val lock = Any()
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    override fun getSecret(key: String): String? = synchronized(lock) {
        val encoded = sharedPrefs.getString(key, null) ?: return@synchronized null
        val parts = encoded.split(SEPARATOR, limit = 2)
        if (parts.size != 2) {
            return@synchronized null
        }

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun setSecret(key: String, value: String) {
        synchronized(lock) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encoded = buildString {
                append(Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                append(SEPARATOR)
                append(
                    Base64.encodeToString(
                        cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
                        Base64.NO_WRAP,
                    ),
                )
            }
            sharedPrefs.edit().putString(key, encoded).apply()
        }
    }

    override fun deleteSecret(key: String) {
        sharedPrefs.edit().remove(key).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(keyAlias, null)
        if (existing is SecretKey) {
            return existing
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val SEPARATOR = ":"
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
