package com.money.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AiException(message: String) : Exception(message)

data class ChatMessage(val role: String, val content: String)

/** Cloudflare Workers AI through its OpenAI compatible chat completions endpoint. */
class WorkersAiClient {

    suspend fun complete(
        config: AiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 900,
        temperature: Double = 0.4,
    ): String = withContext(Dispatchers.IO) {
        if (!config.accountId.matches(Regex("[A-Za-z0-9]{8,64}"))) throw AiException("Account ID non valido")
        val url = URL("https://api.cloudflare.com/client/v4/accounts/${config.accountId}/ai/v1/chat/completions")
        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)
            .put("messages", JSONArray(messages.map { JSONObject().put("role", it.role).put("content", it.content) }))

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
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
            val message = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = message.optString("content").takeIf { it.isNotBlank() && it != "null" }
                ?: throw AiException("Il modello non ha restituito testo")
            content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
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
}
