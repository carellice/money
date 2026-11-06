package com.money.app.ui.recurring

import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.lazy.LazyRow
import com.money.app.data.installmentsLeft
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.money.app.data.Payments
import com.money.app.data.RecurringSort
import com.money.app.data.paymentChoice
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.LocalPayments
import androidx.compose.foundation.layout.width
import com.money.app.ui.components.Pill
import kotlinx.coroutines.launch
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
}

@Composable
fun RecurringScreen(onOpen: (Long) -> Unit, onRegister: (Long) -> Unit, onAdd: (RecurringKind?) -> Unit, onOpenPayments: () -> Unit) {
    val vm = appViewModel { c, _ -> RecurringViewModel(c) }
    val all by vm.items.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var tabKind by rememberSaveable { mutableStateOf<RecurringKind?>(null) }
    val c = Ui.colors
    val list = all ?: return
    val visible = if (tabKind == null) list else list.filter { it.kind == tabKind }
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
                RoundIconButton(Icons.Rounded.Add, { onAdd(tabKind) }, background = c.accent, tint = Ink, contentDescription = "Aggiungi")
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
                    AnimatedAmount(monthly, Ui.type.display, color = Ink, decimalsScale = 0.45f, masked = false)
                    Text(
                        "${Money.short(monthly * 12)} l'anno · abbonamenti ${Money.short(subscriptions)}/mese",
                        style = Ui.type.caption,
                        color = Ink.copy(alpha = 0.8f),
                    )
                }
            }
        }
        if (active.isNotEmpty()) {
            item { PaymentsEntry(active, onOpenPayments, Modifier.staggered(1)) }
        }
        item { KindChips(list, tabKind, { tabKind = it }, Modifier.staggered(1)) }
        if (visible.size > 1) {
            item { SortRow(sort, vm::setSort, Modifier.staggered(1)) }
        }
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    "Niente qui",
                    if (tabKind == RecurringKind.FINANCING) "Aggiungi un acquisto a rate, come Klarna: Money conta le rate pagate e quelle che mancano."
                    else "Aggiungi Netflix, l'affitto o la palestra: Money li registrerà da solo e ti avviserà prima del rinnovo.",
                    icon = "repeat",
                )
            }
        }
        items(visible, key = { it.id }) { item ->
            RecurringCard(item, Modifier.animateItem().staggered(2), onLongClick = { onRegister(item.id) }) { onOpen(item.id) }
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

}

@Composable
private fun RecurringCard(item: RecurringEntity, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    val c = Ui.colors
    val date = LocalDate.ofEpochDay(item.nextDate)
    val days = item.nextDate - LocalDate.now().toEpochDay()
    val left = item.installmentsLeft
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
                    when {
                        item.active -> " ${Dates.long(date)} · ${Dates.relative(date)}"
                        left == 0 -> " Saldato"
                        else -> " In pausa"
                    },
                    style = Ui.type.caption,
                    color = c.inkSoft,
                    maxLines = 1,
                )
            }
            if (item.installments != null && left != 0) {
                Text(
                    (if (left == 1) "Rimane 1 rata" else "Rimangono $left rate") + " su ${item.installments}",
                    style = Ui.type.caption,
                    color = c.inkSoft,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(item.amountCents), style = Ui.type.amount, color = c.ink)
            Text(item.frequency.short, style = Ui.type.caption, color = c.inkFaint)
            if (item.active && days <= 3) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (days < 0) "da pagare" else "presto",
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

/** Filters the list by type, with how many items each one has; long names never get cut. */
@Composable
internal fun KindChips(items: List<RecurringEntity>, selected: RecurringKind?, onSelect: (RecurringKind?) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(null to "Tutte") + RecurringKind.entries.map { it to it.plural }
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.second }) { (kind, label) ->
            val count = if (kind == null) items.size else items.count { it.kind == kind }
            Pill("$label $count", selected == kind, { onSelect(kind) })
        }
    }
}

internal val RecurringKind.plural: String
    get() = when (this) {
        RecurringKind.SUBSCRIPTION -> "Abbonamenti"
        RecurringKind.FIXED -> "Spese fisse"
        RecurringKind.FINANCING -> "Rate"
    }

/** Opens the split by payment method, with a hint of what is inside. */
@Composable
private fun PaymentsEntry(active: List<RecurringEntity>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val payments = LocalPayments.current
    val totals = active.groupBy { it.paymentChoice }.mapValues { (_, list) -> list.sumOf { it.frequency.monthlyCents(it.amountCents) } }
    val top = totals.maxByOrNull { it.value }?.key
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(c.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(AppIcons["wallet"], null, tint = c.ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Per metodo di pagamento", style = Ui.type.bodyStrong, color = c.ink)
            Text(
                (if (totals.size == 1) "1 metodo" else "${totals.size} metodi") + (top?.let { " · il più usato è ${payments.label(it)}" } ?: ""),
                style = Ui.type.caption,
                color = c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.inkFaint)
    }
}
