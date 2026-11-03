package com.money.app.ui.settings

import com.money.app.ui.components.FullScreenPage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.money.app.data.CustomPaymentEntity
import com.money.app.data.Palette
import com.money.app.data.PaymentChoice
import com.money.app.data.PaymentMethod
import com.money.app.ui.add.NoteField
import com.money.app.ui.appContainer
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.IconPicker
import com.money.app.ui.components.LocalPayments
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.launch

/** Rounded badge with the method's icon on its color. */
@Composable
fun PaymentBadge(method: CustomPaymentEntity, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.36f)).background(Color(method.color)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[method.icon], null, tint = Ink, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Built-in and custom payment methods to pick from, plus "Nuovo" to add one on the spot:
 * the new method is selected as soon as it is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentPills(selected: PaymentChoice, onSelect: (PaymentChoice) -> Unit, color: Color = Ui.colors.accent) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val custom = LocalPayments.current.custom.values.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    var creating by remember { mutableStateOf<CustomPaymentEntity?>(null) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaymentMethod.entries.forEach { method ->
            val choice = PaymentChoice(method)
            Pill(method.label, selected == choice, { onSelect(choice) }, color = color, icon = AppIcons[method.icon])
        }
        custom.forEach { method ->
            val choice = PaymentChoice.custom(method.id)
            Pill(method.name, selected == choice, { onSelect(choice) }, color = Color(method.color), icon = AppIcons[method.icon])
        }
        Pill("Nuovo", false, { creating = newPayment(custom.size) }, color = color, icon = Icons.Rounded.Add)
    }

    creating?.let { method ->
        PaymentEditor(
            method,
            onDismiss = { creating = null },
            onSave = {
                creating = null
                scope.launch {
                    val id = repo.saveCustomPayment(it)
                    if (id >= 0) onSelect(PaymentChoice.custom(id))
                }
            },
            onDelete = {},
        )
    }
}

private fun newPayment(count: Int) =
    CustomPaymentEntity(name = "", icon = "card", color = Palette.all[(count + 3) % Palette.all.size])

/** The custom payment methods in the settings, with the button to add one. */
@Composable
fun PaymentsSection() {
    val c = Ui.colors
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val custom = LocalPayments.current.custom.values.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    var editing by remember { mutableStateOf<CustomPaymentEntity?>(null) }

    custom.forEach { method ->
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyClick(pressedScale = 0.98f) { editing = method }
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaymentBadge(method)
            Spacer(Modifier.size(14.dp))
            Text(method.name, style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.98f) { editing = newPayment(custom.size) }
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accentText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Aggiungi metodo di pagamento", style = Ui.type.bodyStrong, color = c.ink)
            Text(
                "Una carta specifica, i buoni pasto... oltre a carta, contanti e gli altri",
                style = Ui.type.caption,
                color = c.inkSoft,
            )
        }
    }

    editing?.let { method ->
        PaymentEditor(
            method,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                scope.launch { repo.saveCustomPayment(it) }
            },
            onDelete = {
                editing = null
                scope.launch { repo.deleteCustomPayment(it) }
            },
        )
    }
}

@Composable
private fun PaymentEditor(
    original: CustomPaymentEntity,
    onDismiss: () -> Unit,
    onSave: (CustomPaymentEntity) -> Unit,
    onDelete: (CustomPaymentEntity) -> Unit,
) {
    val c = Ui.colors
    var name by remember { mutableStateOf(original.name) }
    var icon by remember { mutableStateOf(original.icon) }
    var color by remember { mutableLongStateOf(original.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    val preview = original.copy(name = name, icon = icon, color = color)

    FullScreenPage(if (original.id == 0L) "Nuovo metodo" else "Modifica metodo", onDismiss) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                PaymentBadge(preview, size = 96.dp)
            }
            FormLabel("Nome")
            NoteField(name, { name = it }, "Es. Carta Revolut")
            FormLabel("Icona")
            IconPicker(icon, Color(color)) { icon = it }
            FormLabel("Colore")
            ColorSwatches(Palette.all, color) { color = it }
            Spacer(Modifier.size(24.dp))
            PrimaryButton(
                "Salva",
                { onSave(preview.copy(name = name.trim())) },
                enabled = name.isNotBlank(),
                color = Color(color),
                icon = Icons.Rounded.Check,
            )
            if (original.id != 0L) {
                Text(
                    "Elimina metodo di pagamento",
                    style = Ui.type.bodyStrong,
                    color = c.expenseText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .bouncyClick { confirmDelete = true }
                        .padding(14.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare ${original.name}?") },
            text = { Text("I movimenti e le spese fisse pagati così restano, con metodo di pagamento \"Altro\".") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    onDelete(original)
                }) { Text("Elimina", color = c.expenseText) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Annulla") } },
        )
    }
}
