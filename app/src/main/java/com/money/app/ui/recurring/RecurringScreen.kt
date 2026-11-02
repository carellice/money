package com.money.app.ui.recurring

import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.money.app.data.PaymentChoice
import com.money.app.data.Payments
import com.money.app.data.RecurringSort
import com.money.app.data.paymentChoice
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.LocalPayments
import com.money.app.ui.components.SectionTitle
import com.money.app.ui.components.SoftCard
import com.money.app.ui.settings.PaymentPills
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.ui.components.AmountField
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.Palette
import com.money.app.data.RecurringEntity
import com.money.app.data.RecurringKind
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AnimatedAmount
import com.money.app.ui.components.EmptyState
import com.money.app.ui.components.RecurringLogo
import com.money.app.ui.components.RoundIconButton
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.components.staggered
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class RecurringViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val sort: StateFlow<RecurringSort> = container.settings.settings.map { it.recurringSort }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecurringSort.NEXT)

    val items: StateFlow<List<RecurringEntity>?> = combine(repo.recurring, repo.customPayments, sort) { list, custom, sort ->
        val payments = Payments(custom)
        val order: Comparator<RecurringEntity> = when (sort) {
            RecurringSort.NEXT -> compareBy { it.nextDate }
            RecurringSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            RecurringSort.COST_HIGH -> compareByDescending { it.frequency.monthlyCents(it.amountCents) }
            RecurringSort.COST_LOW -> compareBy { it.frequency.monthlyCents(it.amountCents) }
            RecurringSort.PAYMENT -> compareBy<RecurringEntity, String>(String.CASE_INSENSITIVE_ORDER) {
                payments.label(it.paymentChoice)
            }.thenBy { it.nextDate }
        }
        list.sortedWith(compareByDescending<RecurringEntity> { it.active }.then(order))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(sort: RecurringSort) = viewModelScope.launch { container.settings.setRecurringSort(sort) }

    /** Records one payment of [item] now, without touching its schedule. */
    fun registerNow(item: RecurringEntity, cents: Long, date: LocalDate, payment: PaymentChoice, onDone: () -> Unit) = viewModelScope.launch {
        val savedId = repo.saveTransaction(
            TransactionEntity(
                type = TxType.EXPENSE,
                amountCents = cents,
                date = date.toEpochDay(),
                categoryId = item.categoryId,
                subcategoryId = item.subcategoryId,
                payment = payment.method,
                paymentId = payment.customId,
                note = item.name,
                recurringId = item.id,
            )
        )
        if (savedId >= 0) onDone()
    }
}

@Composable
fun RecurringScreen(onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val vm = appViewModel { c, _ -> RecurringViewModel(c) }
    val all by vm.items.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var yearly by rememberSaveable { mutableStateOf(false) }
    var registering by remember { mutableStateOf<RecurringEntity?>(null) }
    val context = LocalContext.current
    val c = Ui.colors
    val list = all ?: return
    val visible = when (tab) {
        1 -> list.filter { it.kind == RecurringKind.SUBSCRIPTION }
        2 -> list.filter { it.kind == RecurringKind.FIXED }
        else -> list
    }
    val active = list.filter { it.active }
    val monthly = active.sumOf { it.frequency.monthlyCents(it.amountCents) }
    val subscriptions = active.filter { it.kind == RecurringKind.SUBSCRIPTION }.sumOf { it.frequency.monthlyCents(it.amountCents) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Spese fisse", style = Ui.type.display, color = c.ink, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Rounded.Add, onAdd, background = c.accent, tint = Ink, contentDescription = "Aggiungi")
            }
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .staggered(0)
                    .blob(Brush.linearGradient(listOf(c.accent, Color(Palette.SKY))), seed = 404, amplitude = 0.05f, speed = 0.6f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ogni mese, in media", style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
                    AnimatedAmount(monthly, Ui.type.display, color = Ink, decimalsScale = 0.45f)
                    Text(
                        "${Money.short(monthly * 12)} l'anno · abbonamenti ${Money.short(subscriptions)}/mese",
                        style = Ui.type.caption,
                        color = Ink.copy(alpha = 0.8f),
                    )
                }
            }
        }
        item {
            Segmented(
                listOf("Tutte", "Abbonamenti", "Fisse"), tab, { tab = it },
                modifier = Modifier.staggered(1),
            )
        }
        if (visible.size > 1) {
            item { SortRow(sort, vm::setSort, Modifier.staggered(1)) }
        }
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    "Niente qui",
                    "Aggiungi Netflix, l'affitto o la palestra: Money li registrerà da solo e ti avviserà prima del rinnovo.",
                    icon = "repeat",
                )
            }
        }
        items(visible, key = { it.id }) { item ->
            RecurringCard(item, Modifier.animateItem().staggered(2), onLongClick = { registering = item }) { onOpen(item.id) }
        }
        if (visible.any { it.active }) {
            item(key = "payments") {
                PaymentTotals(visible.filter { it.active }, yearly, { yearly = it }, Modifier.animateItem().padding(top = 12.dp))
            }
        }
        if (visible.isNotEmpty()) {
            item {
                Text(
                    "Tieni premuto per aggiungere subito una spesa ai movimenti.",
                    style = Ui.type.caption,
                    color = c.inkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }

    registering?.let { item ->
        RegisterNowSheet(item, onDismiss = { registering = null }) { cents, date, payment ->
            vm.registerNow(item, cents, date, payment) {
                registering = null
                Toast.makeText(context, "${item.name} aggiunto ai movimenti del ${Dates.long(date)}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun RegisterNowSheet(item: RecurringEntity, onDismiss: () -> Unit, onConfirm: (Long, LocalDate, PaymentChoice) -> Unit) {
    val c = Ui.colors
    val today = LocalDate.now()
    var amount by remember { mutableStateOf(Money.toInput(item.amountCents)) }
    var date by remember { mutableStateOf(today) }
    // Defaults to the usual method; this one payment may have gone another way.
    var payment by remember { mutableStateOf(item.paymentChoice) }
    var showPicker by remember { mutableStateOf(false) }
    val cents = Money.parse(amount) ?: 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).imePadding().padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecurringLogo(item.brandKey, item.imagePath, item.icon, Color(item.color), size = 56.dp, animated = true)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("Aggiungi alle spese", style = Ui.type.caption, color = c.inkSoft)
                    Text(item.name, style = Ui.type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FormLabel("Importo")
            AmountField(amount, { amount = it })
            FormLabel("Data")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Oggi", date == today, { date = today })
                Pill("Ieri", date == today.minusDays(1), { date = today.minusDays(1) })
                val other = date != today && date != today.minusDays(1)
                Pill(if (other) Dates.long(date) else "Altra data", other, { showPicker = true }, icon = Icons.Rounded.CalendarMonth)
            }
            FormLabel("Metodo di pagamento")
            PaymentPills(payment, { payment = it })
            Text(
                "Il prossimo addebito programmato (${Dates.long(LocalDate.ofEpochDay(item.nextDate))}) resta invariato.",
                style = Ui.type.caption,
                color = c.inkFaint,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Aggiungi ${Money.format(cents)}", { onConfirm(cents, date, payment) }, enabled = cents > 0, icon = Icons.Rounded.Check)
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ showPicker = false }) { Text("Annulla") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun RecurringCard(item: RecurringEntity, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    val c = Ui.colors
    val date = LocalDate.ofEpochDay(item.nextDate)
    val days = item.nextDate - LocalDate.now().toEpochDay()
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, onLongClick = onLongClick, onClick = onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(c.surface)
            .padding(14.dp)
            .alpha(if (item.active) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecurringLogo(item.brandKey, item.imagePath, item.icon, Color(item.color), size = 56.dp)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = Ui.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val payments = LocalPayments.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons[payments.icon(item.paymentChoice)], null, tint = c.inkFaint, modifier = Modifier.size(13.dp))
                Text(
                    " " + payments.label(item.paymentChoice),
                    style = Ui.type.caption,
                    color = c.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.autoRegister) Icon(Icons.Rounded.Autorenew, null, tint = c.inkFaint, modifier = Modifier.size(13.dp))
                if (item.remindDaysBefore >= 0) Icon(Icons.Rounded.NotificationsActive, null, tint = c.inkFaint, modifier = Modifier.size(13.dp))
                Text(
                    if (item.active) " ${Dates.long(date)} · ${Dates.relative(date)}" else " In pausa",
                    style = Ui.type.caption,
                    color = c.inkSoft,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(item.amountCents), style = Ui.type.amount, color = c.ink)
            Text(item.frequency.short, style = Ui.type.caption, color = c.inkFaint)
            if (item.active && days in 0..3) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "presto",
                    style = Ui.type.caption,
                    color = Ink,
                    modifier = Modifier.clip(CircleShape).background(c.expense).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SortRow(sort: RecurringSort, onSort: (RecurringSort) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Ordina per", style = Ui.type.caption, color = c.inkSoft, modifier = Modifier.weight(1f))
        Box {
            Pill(sort.label, false, { open = true }, icon = Icons.Rounded.SwapVert)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
                RecurringSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, style = Ui.type.label, color = c.ink) },
                        trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null, tint = c.accentText) },
                        onClick = {
                            open = false
                            onSort(option)
                        },
                    )
                }
            }
        }
    }
}

/** What the active fixed expenses cost on each payment method, per month or per year. */
@Composable
private fun PaymentTotals(items: List<RecurringEntity>, yearly: Boolean, onYearly: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val payments = LocalPayments.current
    val totals = items.groupBy { it.paymentChoice }
        .map { (choice, list) ->
            choice to list.sumOf { if (yearly) it.frequency.yearlyCents(it.amountCents) else it.frequency.monthlyCents(it.amountCents) }
        }
        .sortedByDescending { it.second }
    Column(modifier) {
        SectionTitle("Per metodo di pagamento")
        SoftCard {
            Segmented(listOf("Al mese", "All'anno"), if (yearly) 1 else 0, { onYearly(it == 1) })
            totals.forEach { (choice, cents) ->
                val count = items.count { it.paymentChoice == choice }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons[payments.icon(choice)], null, tint = c.inkSoft, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(payments.label(choice), style = Ui.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (count == 1) "1 voce" else "$count voci", style = Ui.type.caption, color = c.inkFaint)
                    }
                    Text(Money.format(cents), style = Ui.type.amount, color = c.ink)
                    Text(if (yearly) "/anno" else "/mese", style = Ui.type.caption, color = c.inkFaint, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
