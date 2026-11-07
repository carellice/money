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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.money.app.data.Payments
import com.money.app.data.accountOf
import com.money.app.data.methodKey
import com.money.app.data.removable
import androidx.compose.ui.text.style.TextOverflow
import com.money.app.ui.add.NoteField
import com.money.app.ui.appContainer
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.IconPicker
import com.money.app.ui.components.LocalAccounts
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

/** Every payment method, built-in first (except the removed ones), as choices. */
fun allChoices(payments: Payments): List<PaymentChoice> =
    payments.builtIns.map { PaymentChoice(it) } +
        payments.custom.values.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { PaymentChoice.custom(it.id) }

/** Badge of any payment method: the custom ones keep their color. */
@Composable
private fun ChoiceBadge(choice: PaymentChoice, color: Color, size: Dp = 40.dp) {
    val payments = LocalPayments.current
    val own = choice.customId?.let { payments.custom[it] }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.36f)).background(own?.let { Color(it.color) } ?: color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[payments.icon(choice)], null, tint = Ink, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * The chosen payment method as one field; tapping it opens the list of every method, grouped
 * by the account it moves, plus "Nuovo metodo" to add one on the spot (selected once saved).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentPicker(selected: PaymentChoice, onSelect: (PaymentChoice) -> Unit, color: Color = Ui.colors.accent) {
    val c = Ui.colors
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val payments = LocalPayments.current
    val accounts = LocalAccounts.current
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf<CustomPaymentEntity?>(null) }
    val account = accounts.accountOf(selected)
    // A removed built-in method (the default "Carta", say) gives way to the first one left.
    LaunchedEffect(selected, payments) {
        if (selected.customId == null && selected.method in payments.hidden) onSelect(allChoices(payments).first())
    }

    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.98f) { open = true }
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceBadge(selected, color)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(payments.label(selected), style = Ui.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            account?.let { Text(it.name, style = Ui.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text("Cambia", style = Ui.type.label, color = c.inkSoft)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
    }

    if (open) {
        val choices = allChoices(payments)
        val groups = (accounts.map { a -> a.name to choices.filter { it.methodKey in a.methodKeys } } +
            ((if (accounts.isEmpty()) "" else "Senza conto") to choices.filter { accounts.accountOf(it) == null }))
            .filter { it.second.isNotEmpty() }
        FullScreenPage("Metodo di pagamento", { open = false }) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { (title, items) ->
                    if (title.isNotEmpty()) FormLabel(title)
                    items.forEach { choice ->
                        val isSelected = choice == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .bouncyClick(pressedScale = 0.98f) {
                                    onSelect(choice)
                                    open = false
                                }
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (isSelected) c.surfaceHigh else c.surface)
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChoiceBadge(choice, color, size = 36.dp)
                            Spacer(Modifier.size(14.dp))
                            Text(payments.label(choice), style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Rounded.Check, null, tint = c.ink)
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Pill("Nuovo metodo", false, { creating = newPayment(payments.custom.size) }, color = color, icon = Icons.Rounded.Add)
            }
        }
    }

    creating?.let { method ->
        PaymentEditor(
            method,
            onDismiss = { creating = null },
            onSave = {
                creating = null
                open = false
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

/** The payment methods in the settings: built-in ones can be removed, custom ones edited, plus the button to add one. */
@Composable
fun PaymentsSection() {
    val c = Ui.colors
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val payments = LocalPayments.current
    val custom = payments.custom.values.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    var editing by remember { mutableStateOf<CustomPaymentEntity?>(null) }
    var removing by remember { mutableStateOf<PaymentChoice?>(null) }

    payments.builtIns.filter { it.removable }.forEach { method ->
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyClick(pressedScale = 0.98f) { removing = PaymentChoice(method) }
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceBadge(PaymentChoice(method), c.surfaceHigh)
            Spacer(Modifier.size(14.dp))
            Text(method.label, style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Delete, "Elimina", tint = c.inkFaint)
        }
    }
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
                removing = PaymentChoice.custom(it.id)
            },
        )
    }
    removing?.let { RemovePayment(it) { removing = null } }
}

/**
 * Removes a payment method. When movements or fixed expenses are paid with it, first asks which
 * other method they move to.
 */
@Composable
private fun RemovePayment(from: PaymentChoice, onDone: () -> Unit) {
    val c = Ui.colors
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val payments = LocalPayments.current
    val name = payments.label(from)
    var uses by remember { mutableStateOf<Int?>(null) }
    var target by remember { mutableStateOf<PaymentChoice?>(null) }
    LaunchedEffect(from) { uses = repo.paymentUses(from) }
    fun remove(to: PaymentChoice?) {
        onDone()
        scope.launch { repo.removePayment(from, to) }
    }

    when (val count = uses) {
        null -> Unit
        0 -> AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Eliminare $name?") },
            text = { Text("Nessun movimento o spesa fissa è pagato così.") },
            confirmButton = { TextButton({ remove(null) }) { Text("Elimina", color = c.expenseText) } },
            dismissButton = { TextButton(onDone) { Text("Annulla") } },
        )
        else -> FullScreenPage("Elimina $name", onDone) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    (if (count == 1) "1 movimento o spesa fissa è pagato" else "$count movimenti e spese fisse sono pagati") +
                        " con $name: scegli il metodo su cui spostarli.",
                    style = Ui.type.body,
                    color = c.inkSoft,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                allChoices(payments).filter { it != from }.forEach { choice ->
                    val isSelected = choice == target
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bouncyClick(pressedScale = 0.98f) { target = choice }
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isSelected) c.surfaceHigh else c.surface)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChoiceBadge(choice, c.accent, size = 36.dp)
                        Spacer(Modifier.size(14.dp))
                        Text(payments.label(choice), style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                        if (isSelected) Icon(Icons.Rounded.Check, null, tint = c.ink)
                    }
                }
                Spacer(Modifier.size(16.dp))
                PrimaryButton("Sposta ed elimina", { target?.let { remove(it) } }, enabled = target != null, color = c.expense, icon = Icons.Rounded.Delete)
            }
        }
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
                        .bouncyClick { onDelete(original) }
                        .padding(14.dp),
                )
            }
        }
    }
}
