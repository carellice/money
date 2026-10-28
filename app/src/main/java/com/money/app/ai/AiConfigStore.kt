package com.money.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiConfig(val accountId: String, val token: String, val model: String)

data class AiModel(val id: String, val label: String, val note: String)

val aiModels = listOf(
    AiModel("@cf/meta/llama-3.3-70b-instruct-fp8-fast", "Llama 3.3 70B", "Bilanciato, buon italiano"),
    AiModel("@cf/openai/gpt-oss-120b", "GPT-OSS 120B", "Più ragionato, più lento"),
    AiModel("@cf/mistralai/mistral-small-3.1-24b-instruct", "Mistral Small 3.1", "Ottimo con le lingue europee"),
    AiModel("@cf/meta/llama-3.1-8b-instruct-fp8", "Llama 3.1 8B", "Veloce ed economico"),
)

/** Encrypts small secrets with an AES key that never leaves the Android Keystore. */
private object SecretBox {
    private const val ALIAS = "money_ai_secret"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toByteArray())
        return b64(cipher.iv) + ":" + b64(data)
    }

    /** Null when the value can't be read back, e.g. restored from a backup on another phone. */
    fun decrypt(value: String): String? = runCatching {
        val (iv, data) = value.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data))
    }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}

class AiConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    /** Null until account ID and token are set. */
    val config: StateFlow<AiConfig?> = _config.asStateFlow()

    private val _monthlyNotice = MutableStateFlow(prefs.getBoolean(KEY_MONTHLY, true))
    val monthlyNotice: StateFlow<Boolean> = _monthlyNotice.asStateFlow()

    val accountId: String get() = prefs.getString(KEY_ACCOUNT, "").orEmpty()
    val model: String get() = prefs.getString(KEY_MODEL, null) ?: aiModels.first().id
    val hasToken: Boolean get() = prefs.contains(KEY_TOKEN)

    /** Last month (yyyy-MM) the end-of-month notice was sent for. */
    var lastNoticeMonth: String?
        get() = prefs.getString(KEY_LAST_NOTICE, null)
        set(value) = prefs.edit().putString(KEY_LAST_NOTICE, value).apply()

    /** [token] null keeps the stored one. */
    fun save(accountId: String, token: String?, model: String) {
        prefs.edit().apply {
            putString(KEY_ACCOUNT, accountId.trim())
            putString(KEY_MODEL, model.trim())
            if (!token.isNullOrBlank()) putString(KEY_TOKEN, SecretBox.encrypt(token.trim()))
        }.apply()
        _config.value = load()
    }

    fun setMonthlyNotice(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONTHLY, enabled).apply()
        _monthlyNotice.value = enabled
    }

    fun clear() {
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_TOKEN).apply()
        _config.value = null
    }

    private fun load(): AiConfig? {
        val account = prefs.getString(KEY_ACCOUNT, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_TOKEN, null)?.let { SecretBox.decrypt(it) } ?: return null
        return AiConfig(account, token, prefs.getString(KEY_MODEL, null) ?: aiModels.first().id)
    }

    private companion object {
        const val KEY_ACCOUNT = "account"
        const val KEY_TOKEN = "token"
        const val KEY_MODEL = "model"
        const val KEY_MONTHLY = "monthly_notice"
        const val KEY_LAST_NOTICE = "last_notice"
    }
}
