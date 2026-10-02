package com.trilingual.ai.data

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API key stays in Android Keystore-backed encrypted storage; no key in source code. */
class UserSettings(context: Context) {
    private val pref = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    var onDevice: Boolean
        get() = pref.getBoolean("onDevice", false)
        set(v) { pref.edit().putBoolean("onDevice", v).apply() }
    var onlineAi: Boolean
        get() = pref.getBoolean("onlineAi", false)
        set(v) { pref.edit().putBoolean("onlineAi", v).apply() }
    var onlineUrl: String
        get() = pref.getString("onlineUrl", "https://api.openai.com/v1/chat/completions") ?: ""
        set(v) { pref.edit().putString("onlineUrl", v.trim()).apply() }
    var onlineModel: String
        get() = pref.getString("onlineModel", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(v) { pref.edit().putString("onlineModel", v.trim()).apply() }
    var glossary: String
        get() = pref.getString("glossary", "") ?: ""
        set(v) { pref.edit().putString("glossary", v).apply() }
    var overlayEnabled: Boolean
        get() = pref.getBoolean("overlay", false)
        set(v) { pref.edit().putBoolean("overlay", v).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("trilingual-api", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("trilingual-api", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    fun saveApiKey(value: String) {
        if (value.isBlank()) { pref.edit().remove("secret").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val blob = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        pref.edit().putString("secret", Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }
    fun apiKey(): String {
        val raw = pref.getString("secret", null) ?: return ""
        return try {
            val blob = Base64.decode(raw, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            String(cipher.doFinal(blob.copyOfRange(12, blob.size)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }
}
