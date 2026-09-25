package ru.starimg.ai.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API key only. Falls back to the old plain prefs file if the keystore is unavailable. */
class SecretStore(context: Context) {
    private val plain = context.getSharedPreferences("starchat", Context.MODE_PRIVATE)
    private val secure: SharedPreferences? = runCatching {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "starchat_secret", master, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrNull()

    var apiKey: String
        get() = read(KEY)
        set(value) { write(KEY, value) }

    /** Short session token sent as the tokens_access_key cookie to the telemetry endpoint. */
    var accessToken: String
        get() = read(ACCESS)
        set(value) { write(ACCESS, value) }

    private fun read(name: String) = secure?.getString(name, null) ?: plain.getString(name, "") ?: ""

    private fun write(name: String, value: String) {
        if (secure != null) secure.edit().putString(name, value).apply() else plain.edit().putString(name, value).apply()
        if (secure != null && plain.contains(name)) plain.edit().remove(name).apply()
    }

    companion object {
        private const val KEY = "key"
        private const val ACCESS = "access"
    }
}
