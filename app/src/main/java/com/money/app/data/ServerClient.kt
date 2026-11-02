package com.money.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.money.app.ai.SecretBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Money server (the PWA hosted on CasaOS) the app keeps its data on, with the account logged in. */
data class ServerConfig(val url: String, val username: String, val token: String)

class ServerException(message: String, val status: Int = 0) : Exception(message) {
    /** Server not reachable (no connection, Tailscale off, server or proxy down): worth trying again later. */
    val offline: Boolean get() = status == 0 || status in 502..504
}

/** Whether the phone has a network that could reach the server at all. */
class Connectivity(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    /** A VPN alone (Tailscale with no Wi-Fi or mobile data under it) can't reach anything. */
    @Suppress("DEPRECATION")
    fun available(): Boolean = runCatching {
        manager.allNetworks.any { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@any false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }.getOrDefault(true)
}

class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("server", Context.MODE_PRIVATE)

    val connectivity = Connectivity(context)

    private val _config = MutableStateFlow(load())
    /** Null while the app keeps its data only on the phone. */
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    val lastUrl: String get() = prefs.getString(KEY_URL, "").orEmpty()
    val lastUsername: String get() = prefs.getString(KEY_USER, "").orEmpty()

    /** Server data revision the local copy matches. */
    var revision: Long
        get() = prefs.getLong(KEY_REVISION, -1)
        set(value) = prefs.edit().putLong(KEY_REVISION, value).apply()

    /** Renewal reminders already shown, as "id:billing day", so each one arrives once. */
    var shownReminders: Set<String>
        get() = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_SHOWN, value).apply()

    fun save(url: String, username: String, token: String) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_USER, username)
            .putString(KEY_TOKEN, SecretBox.encrypt(token))
            .putLong(KEY_REVISION, -1)
            .apply()
        _config.value = load()
    }

    /** Back to data on the phone only; address and username stay to fill the form next time. */
    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_REVISION).remove(KEY_SHOWN).apply()
        _config.value = null
    }

    private fun load(): ServerConfig? {
        val url = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_TOKEN, null)?.let { SecretBox.decrypt(it) } ?: return null
        return ServerConfig(url, prefs.getString(KEY_USER, "").orEmpty(), token)
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_USER = "username"
        const val KEY_TOKEN = "token"
        const val KEY_REVISION = "revision"
        const val KEY_SHOWN = "shown_reminders"
    }
}

/** The same HTTP API the PWA uses, with the session cookie of the account. */
class ServerClient(private val connectivity: Connectivity? = null) {
    /** Name lookups run here, so a lookup that hangs without a connection can be left behind. */
    private val lookups = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Fails at once without a network, and after a few seconds when the server's name can't be
     * resolved: without a connection the lookup can hang well past the connection timeout.
     */
    private suspend fun ensureReachable(url: String) {
        if (connectivity?.available() == false) throw ServerException(UNREACHABLE)
        val host = runCatching { URL(url).host }.getOrNull() ?: return
        if (host.isEmpty() || host.first().isDigit() || host.contains(':')) return
        val found = try {
            withTimeoutOrNull(LOOKUP_MS) { lookups.async { InetAddress.getByName(host) }.await() }
        } catch (e: IOException) {
            null
        }
        if (found == null) throw ServerException(UNREACHABLE)
    }

    /** Logs in (or creates the account) and returns the session token. */
    suspend fun login(url: String, username: String, password: String, register: Boolean): String = withContext(Dispatchers.IO) {
        ensureReachable(url)
        val body = JSONObject().put("username", username).put("password", password).toString().toByteArray()
        val connection = open(url, if (register) "/api/register" else "/api/login", "POST", null)
        try {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) throw ServerException(errorOf(connection, code), code)
            connection.headerFields.filterKeys { it.equals("Set-Cookie", ignoreCase = true) }.values.flatten()
                .firstNotNullOfOrNull { Regex("$SESSION=([^;]+)").find(it)?.groupValues?.get(1) }
                ?: throw ServerException("Il server non ha aperto la sessione: è davvero Money?")
        } catch (e: IOException) {
            throw unreachable(e)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun logout(config: ServerConfig) {
        runCatching { request(config, "POST", "/api/logout") }
    }

    /** Whole data of the account, or `unchanged` when it still matches [since]. */
    suspend fun state(config: ServerConfig, since: Long): JSONObject =
        JSONObject(request(config, "GET", "/api/state" + if (since >= 0) "?since=$since" else ""))

    suspend fun post(config: ServerConfig, path: String, body: JSONObject): JSONObject =
        JSONObject(request(config, "POST", path, body.toString().toByteArray(), "application/json"))

    suspend fun delete(config: ServerConfig, path: String) {
        request(config, "DELETE", path)
    }

    /** Replaces all data of the account with a backup in the app's JSON format. */
    suspend fun importBackup(config: ServerConfig, json: String) {
        request(config, "POST", "/api/backup", json.toByteArray(), "application/json")
    }

    /** Uploads a JPEG and returns its server path ("/images/<name>"). */
    suspend fun uploadImage(config: ServerConfig, file: File, prefix: String): String =
        JSONObject(request(config, "POST", "/api/images?prefix=$prefix", file.readBytes(), "image/jpeg")).getString("path")

    suspend fun deleteImage(config: ServerConfig, path: String) {
        request(config, "DELETE", "/api/images?path=" + URLEncoder.encode(path, "UTF-8"))
    }

    suspend fun downloadImage(config: ServerConfig, path: String, target: File) = withContext(Dispatchers.IO) {
        ensureReachable(config.url)
        val connection = open(config.url, path, "GET", config.token)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw ServerException(errorOf(connection, code), code)
            val temp = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            temp.renameTo(target)
        } catch (e: IOException) {
            throw unreachable(e)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun request(
        config: ServerConfig,
        method: String,
        path: String,
        body: ByteArray? = null,
        type: String? = null,
    ): String = withContext(Dispatchers.IO) {
        ensureReachable(config.url)
        val connection = open(config.url, path, method, config.token)
        try {
            if (body != null) {
                connection.setRequestProperty("Content-Type", type)
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw ServerException(errorOf(connection, code), code)
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw unreachable(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(base: String, path: String, method: String, token: String?): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Cookie", "$SESSION=$token")
        }

    private fun errorOf(connection: HttpURLConnection, code: Int): String {
        val text = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val message = runCatching { JSONObject(text).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
        return when {
            code == 401 && message == "Accesso richiesto" -> "Sessione scaduta: accedi di nuovo in Impostazioni → Server CasaOS"
            message != null -> message
            code in 300..399 -> "Il server risponde con un reindirizzamento: controlla l'indirizzo (http o https, porta)"
            else -> "Errore del server ($code)"
        }
    }

    private fun unreachable(e: IOException) = ServerException(
        when (e) {
            is javax.net.ssl.SSLException -> "Certificato HTTPS non valido: con il certificato locale installalo prima sul telefono"
            else -> UNREACHABLE
        }
    )

    companion object {
        private const val SESSION = "money_session"
        private const val LOOKUP_MS = 5_000L
        private const val UNREACHABLE = "Server non raggiungibile: controlla la connessione (e Tailscale, se lo usi)"

        /** "casaos.local:8095" → "https://casaos.local:8095", without the trailing slash. */
        fun normalizeUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        }
    }
}
