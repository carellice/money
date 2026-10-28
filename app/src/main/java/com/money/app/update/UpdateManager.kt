package com.money.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.money.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Ready(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * Checks the latest GitHub release of the app and installs its APK.
 * Android itself refuses an APK not signed with the same key, so a tampered file can't replace the app.
 */
class UpdateManager(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME
    val repoUrl = "https://github.com/${BuildConfig.GITHUB_REPO}"

    /** Silent check at most once a day; only surfaces something when an update exists. */
    fun checkIfDue() {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0) < DAY_MS) return
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        scope.launch {
            if (!silent) _state.value = UpdateState.Checking
            runCatching { fetchLatest() }
                .onSuccess { release ->
                    prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                    _state.value = when {
                        release != null && isNewer(release.version, currentVersion) -> UpdateState.Available(release)
                        silent -> UpdateState.Idle
                        else -> UpdateState.UpToDate
                    }
                }
                .onFailure { if (!silent) _state.value = UpdateState.Failed(it.message ?: "Controllo non riuscito") }
        }
    }

    fun download(release: AppRelease) {
        if (_state.value is UpdateState.Downloading) return
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            runCatching { fetchApk(release) }
                .onSuccess {
                    _state.value = UpdateState.Ready(release, it)
                    install(it)
                }
                .onFailure { _state.value = UpdateState.Failed(it.message ?: "Download non riuscito", release) }
        }
    }

    /** Opens the system installer, or first the "install unknown apps" permission page. */
    fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Money-Android")
        }
        try {
            when (val code = connection.responseCode) {
                404 -> return@withContext null // no release published yet
                in 200..299 -> Unit
                else -> throw IOException("GitHub ha risposto $code")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            AppRelease(
                version = json.getString("tag_name").removePrefix("v"),
                notes = json.optString("body").trim(),
                apkUrl = apk.getString("browser_download_url"),
                size = apk.optLong("size"),
                pageUrl = json.optString("html_url"),
            )
        } catch (e: IOException) {
            throw IOException("Impossibile contattare GitHub: controlla internet")
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchApk(release: AppRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Money-${release.version}.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Money-Android")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("Download non riuscito (${connection.responseCode})")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
            var read = 0L
            var lastEmit = 0f
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val progress = if (total > 0) read.toFloat() / total else 0f
                        if (progress - lastEmit >= 0.02f) {
                            lastEmit = progress
                            _state.value = UpdateState.Downloading(release, progress)
                        }
                    }
                }
            }
            file
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Compares dotted versions numerically: 1.0.10 > 1.0.9. */
        fun isNewer(remote: String, current: String): Boolean {
            val a = remote.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
