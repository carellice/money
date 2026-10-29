package com.money.app.ui.settings

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.Backup
import com.money.app.data.Settings
import com.money.app.data.ThemeMode
import com.money.app.ui.appContainer
import com.money.app.ui.appViewModel
import com.money.app.ui.update.UpdateBanner
import com.money.app.update.UpdateState
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Widgets
import com.money.app.widget.RenewalsWidgetReceiver
import com.money.app.data.Palette
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.SwitchRow
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.lock.Biometric
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }
    fun setLock(enabled: Boolean) = viewModelScope.launch { container.settings.setLock(enabled) }
    fun setReminders(enabled: Boolean) = viewModelScope.launch { container.settings.setReminders(enabled) }
    fun setAccent(color: Long) = viewModelScope.launch { container.settings.setAccent(color) }

    fun export(context: android.content.Context, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val message = runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { Backup.export(container.repository, it) }
            }
            "Backup esportato"
        }.getOrElse { "Esportazione non riuscita: ${it.message}" }
        onResult(message)
    }

    fun import(context: android.content.Context, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val message = runCatching {
            val count = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { Backup.import(container.repository, it) } ?: 0
            }
            "Backup importato: $count movimenti"
        }.getOrElse { "Importazione non riuscita: ${it.message}" }
        onResult(message)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCategories: () -> Unit, onOpenAi: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Ui.colors
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.export(context, it, toast) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReminders(granted)
        if (!granted) toast("Senza permesso per le notifiche i promemoria non possono arrivare.")
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Impostazioni", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormLabel("Aspetto")
            Segmented(
                ThemeMode.entries.map { it.label },
                ThemeMode.entries.indexOf(settings.theme),
                { vm.setTheme(ThemeMode.entries[it]) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                FormLabel("Colore principale", Modifier.weight(1f))
                if (settings.accent != Palette.LILAC) {
                    Text(
                        "Ripristina",
                        style = Ui.type.label,
                        color = c.accentText,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(50))
                            .bouncyClick { vm.setAccent(Palette.LILAC) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            ColorSwatches(Palette.all, settings.accent, vm::setAccent)

            FormLabel("Privacy")
            SwitchRow(
                "Blocco con impronta",
                if (Biometric.available(context)) "Chiede impronta, volto o PIN all'apertura." else "Configura impronta o PIN nelle impostazioni del telefono.",
                settings.lockEnabled,
                { enable ->
                    val activity = context as? FragmentActivity
                    when {
                        !enable -> vm.setLock(false)
                        !Biometric.available(context) || activity == null -> toast("Nessun metodo di sblocco disponibile sul telefono.")
                        else -> Biometric.authenticate(activity, "Attiva il blocco", onSuccess = { vm.setLock(true) })
                    }
                },
            )

            FormLabel("Notifiche")
            SwitchRow(
                "Promemoria rinnovi",
                "Avviso prima di abbonamenti e spese fisse.",
                settings.remindersEnabled,
                { enable ->
                    val missing = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    if (enable && missing) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setReminders(enable)
                },
            )

            FormLabel("Organizza")
            NavRow(Icons.Rounded.Category, "Categorie e budget", "Sottocategorie, icone, colori e limiti mensili", onOpenCategories)
            val widgets = remember { AppWidgetManager.getInstance(context) }
            if (widgets.isRequestPinAppWidgetSupported) {
                NavRow(Icons.Rounded.Widgets, "Aggiungi il widget", "I prossimi rinnovi sulla schermata home") {
                    widgets.requestPinAppWidget(ComponentName(context, RenewalsWidgetReceiver::class.java), null, null)
                }
            }

            FormLabel("Backup")
            NavRow(Icons.Rounded.CloudUpload, "Esporta backup", "Salva tutti i dati in un file JSON") {
                exportLauncher.launch("money-backup-${LocalDate.now()}.json")
            }
            NavRow(Icons.Rounded.CloudDownload, "Importa backup", "Sostituisce i dati con quelli del file") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            Text(
                "Le foto degli scontrini e i loghi personalizzati non sono inclusi nel backup.",
                style = Ui.type.caption,
                color = c.inkFaint,
            )

            FormLabel("Intelligenza artificiale")
            NavRow(Icons.Rounded.AutoAwesome, "Assistente AI", "Cloudflare Workers AI: commento del mese, chat, categorie automatiche", onOpenAi)

            FormLabel("Aggiornamenti")
            val updates = appContainer().updates
            val updateState by updates.state.collectAsStateWithLifecycle()
            NavRow(
                Icons.Rounded.SystemUpdate,
                "Controlla aggiornamenti",
                when (val st = updateState) {
                    UpdateState.Checking -> "Controllo in corso…"
                    UpdateState.UpToDate -> "Hai l'ultima versione (${updates.currentVersion})"
                    is UpdateState.Failed -> st.message
                    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> "Nuova versione disponibile"
                    UpdateState.Idle -> "Versione installata: ${updates.currentVersion}"
                },
            ) { updates.check() }
            UpdateBanner(updateState, updates)

            Spacer(Modifier.height(16.dp))
            Text(
                "Money ${updates.currentVersion} · Font Bricolage Grotesque (OFL) · Loghi Simple Icons (CC0), marchi dei rispettivi proprietari.",
                style = Ui.type.caption,
                color = c.inkFaint,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Importare il backup?") },
            text = { Text("Tutti i movimenti, le categorie e le spese fisse attuali verranno sostituiti.") },
            confirmButton = {
                TextButton({
                    vm.import(context, uri, toast)
                    pendingImport = null
                }) { Text("Importa", color = c.expenseText) }
            },
            dismissButton = { TextButton({ pendingImport = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    val c = Ui.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncyClick(pressedScale = 0.98f, onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (onClick != null) c.accentText else c.inkFaint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Ui.type.bodyStrong, color = if (onClick != null) c.ink else c.inkSoft)
            Text(subtitle, style = Ui.type.caption, color = c.inkSoft)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
    }
}
