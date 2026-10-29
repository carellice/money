package com.money.app.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

/** [voice]: the user dictated it; only changes how the bubble looks, never what is sent. */
data class ChatMessage(val role: String, val content: String, val voice: Boolean = false)

/** Cloudflare Workers AI: chat through the OpenAI compatible endpoint, speech through Whisper. */
class WorkersAiClient {

    suspend fun complete(
        config: AiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 900,
        temperature: Double = 0.4,
    ): String {
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)
            .put("messages", JSONArray(messages.map { JSONObject().put("role", it.role).put("content", it.content) }))
        val json = post(config, "ai/v1/chat/completions", body, readTimeout = 90_000)
        val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val content = message.optString("content").takeIf { it.isNotBlank() && it != "null" }
            ?: throw AiException("Il modello non ha restituito testo")
        return content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    }

    /** Speech to text of a WAV recording with Whisper. */
    suspend fun transcribe(config: AiConfig, wav: ByteArray, language: String = "it"): String {
        val body = JSONObject()
            .put("audio", Base64.encodeToString(wav, Base64.NO_WRAP))
            .put("language", language)
        val json = post(config, "ai/run/$WHISPER", body, readTimeout = 120_000)
        return json.optJSONObject("result")?.optString("text").orEmpty().trim()
            .ifEmpty { throw AiException("Non ho sentito niente, riprova") }
    }

    private suspend fun post(config: AiConfig, path: String, body: JSONObject, readTimeout: Int): JSONObject = withContext(Dispatchers.IO) {
        if (!config.accountId.matches(Regex("[A-Za-z0-9]{8,64}"))) throw AiException("Account ID non valido")
        val url = URL("https://api.cloudflare.com/client/v4/accounts/${config.accountId}/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            this.readTimeout = readTimeout
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.token}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiException(errorMessage(code, text))
            JSONObject(text)
        } catch (e: IOException) {
            throw AiException("Connessione non riuscita: controlla internet")
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                ?: json.optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return when (code) {
            401, 403 -> "Token non valido o senza permesso Workers AI"
            404 -> "Account ID o modello non trovati"
            429 -> "Troppe richieste o limite gratuito raggiunto, riprova più tardi"
            else -> "Errore Cloudflare $code" + (detail?.let { ": $it" } ?: "")
        }
    }

    private companion object {
        const val WHISPER = "@cf/openai/whisper-large-v3-turbo"
    }
}
