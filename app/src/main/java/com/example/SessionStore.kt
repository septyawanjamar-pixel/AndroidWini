package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Menyimpan preferensi "Ingat Saya" secara terenkripsi.
 * Password TIDAK pernah disimpan; sesi login dipertahankan oleh Firebase Auth.
 */
class SessionStore(private val context: Context) {

    private val prefs: SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // Keystore rusak / data korup -> reset
        context.deleteSharedPreferences(FILE)
        create()
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** true = tetap login saat aplikasi dibuka kembali. */
    var rememberMe: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, false)
        set(value) = prefs.edit { putBoolean(KEY_REMEMBER, value) }

    var savedIdentifier: String
        get() = prefs.getString(KEY_IDENTIFIER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_IDENTIFIER, value) }

    fun save(remember: Boolean, identifier: String) {
        rememberMe = remember
        savedIdentifier = if (remember) identifier else ""
    }

    fun onLogout() {
        rememberMe = false // username tetap tersimpan untuk prefill
    }

    private companion object {
        const val FILE = "secure_session"
        const val KEY_REMEMBER = "remember_me"
        const val KEY_IDENTIFIER = "saved_identifier"
    }
}
