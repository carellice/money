package com.money.app.ui.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.ai.aiModels
import com.money.app.ui.appViewModel
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.SwitchRow
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.launch

class AiSettingsViewModel(container: AppContainer) : ViewModel() {
    private val ai = container.ai
    val store = ai.store

    var accountId by mutableStateOf(store.accountId)
    var token by mutableStateOf("")
    var model by mutableStateOf(store.model)
    var status by mutableStateOf<String?>(null)
        private set
    var ok by mutableStateOf<Boolean?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    val canSave: Boolean get() = accountId.isNotBlank() && (token.isNotBlank() || store.hasToken) && model.isNotBlank()

    /** Saves, then sends a tiny prompt to check account, token and model together. */
    fun saveAndTest() = viewModelScope.launch {
        store.save(accountId, token.ifBlank { null }, model)
        token = ""
        busy = true
        status = null
        ok = null
        runCatching { ai.testConnection() }
            .onSuccess { status = "Connesso. Risposta del modello: “${it.take(80)}”"; ok = true }
            .onFailure { status = it.message ?: "Errore sconosciuto"; ok = false }
        busy = false
    }

    fun clear() {
        store.clear()
        accountId = ""
        token = ""
        status = "Credenziali rimosse dal telefono."
        ok = null
    }
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> AiSettingsViewModel(c) }
    val config by vm.store.config.collectAsStateWithLifecycle()
    val monthly by vm.store.monthlyNotice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Ui.colors
    val custom = aiModels.none { it.id == vm.model }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar("Assistente AI", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            SoftCard(color = c.surfaceHigh) {
                Text(
                    if (config != null) "Cloudflare Workers AI è collegato." else "Collega Cloudflare Workers AI per commento del mese, chat, categorie automatiche e resoconto di fine mese.",
                    style = Ui.type.bodyStrong,
                    color = c.ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Serve un account Cloudflare: copia l'Account ID dalla dashboard e crea un API token con il permesso \"Workers AI\" (lettura e modifica).",
                    style = Ui.type.caption,
                    color = c.inkSoft,
                )
                Text(
                    "Apri la pagina dei token",
                    style = Ui.type.label,
                    color = c.accentText,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .bouncyClick {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dash.cloudflare.com/profile/api-tokens")))
                        }
                        .padding(vertical = 4.dp),
                )
            }

            FormLabel("Account ID")
            Field(vm.accountId, { vm.accountId = it.trim() }, "es. 1a2b3c…")

            FormLabel("API token")
            Field(
                vm.token, { vm.token = it.trim() },
                if (vm.store.hasToken) "Salvato e cifrato (lascia vuoto per non cambiarlo)" else "Incolla il token",
                secret = true,
            )

            FormLabel("Modello")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                aiModels.forEach { m -> Pill(m.label, vm.model == m.id, { vm.model = m.id }) }
                Pill("Altro", custom, { if (!custom) vm.model = "" })
            }
            Text(
                aiModels.firstOrNull { it.id == vm.model }?.note ?: "Scrivi l'ID completo, es. @cf/qwen/…",
                style = Ui.type.caption,
                color = c.inkSoft,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (custom) {
                Spacer(Modifier.height(8.dp))
                Field(vm.model, { vm.model = it.trim() }, "@cf/…")
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                if (vm.busy) "Verifico…" else "Salva e prova",
                { vm.saveAndTest() },
                enabled = vm.canSave && !vm.busy,
                icon = Icons.Rounded.Wifi,
            )
            vm.status?.let {
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

            FormLabel("Funzioni")
            SwitchRow(
                "Resoconto di fine mese",
                "Nei primi giorni del mese una notifica racconta il mese appena chiuso.",
                monthly,
                vm.store::setMonthlyNotice,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "Privacy: quando usi l'AI, Money invia a Cloudflare i totali per categoria e l'elenco dei movimenti (data, importo, categoria, nota, metodo). " +
                    "Le foto degli scontrini non vengono mai inviate. Il token resta cifrato sul telefono e non finisce nel backup.",
                style = Ui.type.caption,
                color = c.inkFaint,
            )
            if (config != null || vm.store.hasToken) {
                Text(
                    "Rimuovi credenziali",
                    style = Ui.type.bodyStrong,
                    color = c.expenseText,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(50))
                        .bouncyClick { vm.clear() }
                        .padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, secret: Boolean = false) {
    val c = Ui.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Ui.type.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accentText),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, style = Ui.type.body, color = c.inkFaint)
                inner()
            }
        },
    )
}
