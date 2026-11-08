package com.money.app.ui.settings

import com.money.app.ui.theme.linen
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.ServerClient
import com.money.app.data.ServerConfig
import com.money.app.ui.ai.Field
import com.money.app.ui.appViewModel
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ui
import com.money.app.work.RecurringWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ServerSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val sync = repo.server
    val config = sync.store.config
    val status = sync.status

    var url by mutableStateOf(sync.store.lastUrl)
    var username by mutableStateOf(sync.store.lastUsername)
    var password by mutableStateOf("")
    var register by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var ok by mutableStateOf<Boolean?>(null)
        private set

    /** Logged in, with data on both sides: the user picks which to keep. */
    var choice by mutableStateOf<Choice?>(null)
        private set
    var confirmOff by mutableStateOf(false)

    data class Choice(val config: ServerConfig, val serverMovements: Int, val phoneMovements: Int)

    val canConnect: Boolean get() = url.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && !busy
    val hasLocalCopy: Boolean get() = repo.localCopy.exists()

    fun connect() = viewModelScope.launch {
        busy = true
        message = null
        ok = null
        runCatching {
            val base = ServerClient.normalizeUrl(url)
            val name = username.trim()
            val token = sync.client.login(base, name, password, register)
            val config = ServerConfig(base, name, token)
            val phone = repo.snapshot()
            val phoneHasData = phone.transactions.isNotEmpty() || phone.recurring.isNotEmpty() || phone.members.isNotEmpty()
            config to if (phoneHasData) Choice(config, sync.serverMovements(config), phone.transactions.size) else null
        }.onSuccess { (config, pending) ->
            password = ""
            url = config.url
            if (pending != null) choice = pending else start(config, upload = false)
        }.onFailure { fail(it) }
        busy = false
    }

    fun dismissChoice() {
        val config = choice?.config ?: return
        choice = null
        container.scope.launch { sync.client.logout(config) }
        message = "Nessuna modifica: i dati restano sul telefono."
        ok = null
    }

    /** Turns server mode on, sending the phone's data ([upload]) or taking the server's. */
    fun start(config: ServerConfig, upload: Boolean, context: Context? = null) = viewModelScope.launch {
        choice = null
        busy = true
        runCatching {
            val phone = repo.snapshot()
            withContext(Dispatchers.IO) { repo.localCopy.writeText(sync.encode(phone)) }
            sync.forget()
            sync.store.save(config.url, config.username, config.token)
            try {
                if (upload) sync.upload(phone) else sync.pull(force = true)
            } catch (e: Exception) {
                sync.store.clear()
                throw e
            }
        }.onSuccess {
            message = if (upload) "Collegato: i dati del telefono ora sono sul server." else "Collegato: il telefono usa i dati del server."
            ok = true
            context?.let { container.scope.launch { RecurringWorker.runNow(it.applicationContext, container) } }
        }.onFailure { fail(it) }
        busy = false
    }

    fun syncNow() = viewModelScope.launch {
        runCatching { sync.pull(force = true) }
            .onSuccess { message = "Dati aggiornati dal server."; ok = true }
            .onFailure { fail(it) }
    }

    /** Back to data on the phone only: keeps the server's copy, or goes back to the data from before. */
    fun turnOff(restore: Boolean) = viewModelScope.launch {
        confirmOff = false
        busy = true
        config.value?.let { config -> container.scope.launch { sync.client.logout(config) } }
        sync.store.clear()
        sync.forget()
        runCatching {
            withContext(Dispatchers.IO) {
                if (restore && repo.localCopy.exists()) repo.replaceLocally(sync.decode(repo.localCopy.readText()))
                repo.localCopy.delete()
            }
        }.onSuccess {
            message = if (restore) "Server scollegato: sono tornati i dati di prima." else "Server scollegato: i dati restano sul telefono."
            ok = null
        }.onFailure { fail(it) }
        busy = false
    }

    private fun fail(e: Throwable) {
        message = e.message ?: "Errore sconosciuto"
        ok = false
    }
}

@Composable
fun ServerSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> ServerSettingsViewModel(c) }
    val config by vm.config.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Ui.colors

    Column(Modifier.fillMaxSize().linen(c.isDark).imePadding()) {
        TopBar("Server CasaOS", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            val current = config
            SoftCard(color = c.surfaceHigh) {
                Text(
                    if (current != null) "I dati sono sul tuo server." else "Usa i dati del tuo server CasaOS, come la PWA.",
                    style = Ui.type.bodyStrong,
                    color = c.ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (current != null) {
                        "${current.username} su ${current.url}. Ogni modifica va al server e le modifiche fatte dalla PWA o da altri telefoni arrivano qui. " +
                            "Senza connessione le modifiche restano sul telefono e partono da sole appena il server risponde."
                    } else {
                        "Se hai installato Money sul tuo server CasaOS, collega l'app al tuo account: telefono e PWA vedono gli stessi dati. " +
                            "Spento, tutto resta solo sul telefono come sempre."
                    },
                    style = Ui.type.caption,
                    color = c.inkSoft,
                )
            }

            if (current == null) {
                FormLabel("Indirizzo del server")
                Field(vm.url, { vm.url = it.trim() }, "es. https://casaos.tailXXXX.ts.net", keyboard = KeyboardType.Uri)
                Text(
                    "Lo stesso indirizzo con cui apri la PWA. Con il certificato locale installa prima il certificato sul telefono.",
                    style = Ui.type.caption,
                    color = c.inkSoft,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (vm.url.startsWith("http://", ignoreCase = true)) {
                    Text(
                        "Con http la password viaggia in chiaro: usalo solo sulla rete di casa.",
                        style = Ui.type.caption,
                        color = c.expenseText,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                FormLabel("Account")
                Segmented(listOf("Ho già un account", "Creane uno"), if (vm.register) 1 else 0, { vm.register = it == 1 })
                Spacer(Modifier.height(10.dp))
                Field(vm.username, { vm.username = it.trim() }, "Nome utente")
                Spacer(Modifier.height(10.dp))
                Field(vm.password, { vm.password = it }, "Password", secret = true)

                Spacer(Modifier.height(20.dp))
                PrimaryButton(
                    if (vm.busy) "Collego…" else "Collega il server",
                    { vm.connect() },
                    enabled = vm.canConnect,
                    icon = Icons.Rounded.Dns,
                )
            } else {
                FormLabel("Sincronizzazione")
                Text(
                    when {
                        status.syncing -> "Aggiorno…"
                        status.error != null -> status.error.orEmpty()
                        status.lastSync > 0 -> "Aggiornato alle " + TIME.format(Instant.ofEpochMilli(status.lastSync).atZone(ZoneId.systemDefault()))
                        else -> "In attesa del primo aggiornamento"
                    } + pendingText(status.pending),
                    style = Ui.type.label,
                    color = if (status.error != null && !status.syncing) c.expenseText else c.inkSoft,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Aggiorna ora", { vm.syncNow() }, enabled = !vm.busy, icon = Icons.Rounded.Sync)
                Text(
                    "Scollega il server",
                    style = Ui.type.bodyStrong,
                    color = c.expenseText,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .bouncyClick { vm.confirmOff = true }
                        .padding(vertical = 8.dp),
                )
            }

            vm.message?.let {
                Text(
                    it,
                    style = Ui.type.label,
                    color = when (vm.ok) {
                        true -> c.incomeText
                        false -> c.expenseText
                        null -> c.inkSoft
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Spese fisse e promemoria li gestisce il server. Il backup in file JSON continua a funzionare: " +
                    "con il server collegato, importarlo sostituisce i dati dell'account sul server.",
                style = Ui.type.caption,
                color = c.inkFaint,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    vm.choice?.let { choice ->
        AlertDialog(
            onDismissRequest = { vm.dismissChoice() },
            title = { Text("Quali dati vuoi usare?") },
            text = {
                Text(
                    "Sul telefono ci sono ${choice.phoneMovements} movimenti, sul server ${choice.serverMovements}. " +
                        "I dati scelti sostituiscono gli altri. Una copia di quelli del telefono resta salvata: scollegando il server puoi tornarci."
                )
            },
            confirmButton = {
                TextButton({ vm.start(choice.config, upload = true, context) }) { Text("Quelli del telefono") }
            },
            dismissButton = {
                TextButton({ vm.start(choice.config, upload = false, context) }) { Text("Quelli del server") }
            },
        )
    }

    if (vm.confirmOff) {
        AlertDialog(
            onDismissRequest = { vm.confirmOff = false },
            title = { Text("Scollegare il server?") },
            text = {
                Text(
                    (
                        if (vm.hasLocalCopy) {
                            "Puoi tenere sul telefono i dati attuali del server oppure tornare a quelli che avevi prima di collegarlo. " +
                                "I dati sul server restano dove sono."
                        } else {
                            "I dati attuali restano sul telefono e da qui in poi le modifiche restano solo qui. I dati sul server restano dove sono."
                        }
                    ) + if (status.pending > 0) {
                        "\n\n${pendingText(status.pending).trim()}: scollegando non arriveranno più al server."
                    } else {
                        ""
                    }
                )
            },
            confirmButton = { TextButton({ vm.turnOff(restore = false) }) { Text("Tieni i dati attuali") } },
            dismissButton = {
                if (vm.hasLocalCopy) TextButton({ vm.turnOff(restore = true) }) { Text("Torna a quelli di prima") }
                else TextButton({ vm.confirmOff = false }) { Text("Annulla") }
            },
        )
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** Changes made offline still waiting for the server, as a short sentence (empty when there are none). */
private fun pendingText(count: Int) = when (count) {
    0 -> ""
    1 -> "\n1 modifica fatta senza connessione da inviare"
    else -> "\n$count modifiche fatte senza connessione da inviare"
}
