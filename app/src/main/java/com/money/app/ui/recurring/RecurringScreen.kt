package com.money.app.ui.recurring

import androidx.compose.foundation.background
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class RecurringViewModel(container: AppContainer) : ViewModel() {
    val items: StateFlow<List<RecurringEntity>?> = container.repository.recurring
        .map { list -> list.sortedWith(compareByDescending<RecurringEntity> { it.active }.thenBy { it.nextDate }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun RecurringScreen(onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val vm = appViewModel { c, _ -> RecurringViewModel(c) }
    val all by vm.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
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
            RecurringCard(item, Modifier.animateItem().staggered(2)) { onOpen(item.id) }
        }
    }
}

@Composable
private fun RecurringCard(item: RecurringEntity, modifier: Modifier, onClick: () -> Unit) {
    val c = Ui.colors
    val date = LocalDate.ofEpochDay(item.nextDate)
    val days = item.nextDate - LocalDate.now().toEpochDay()
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, onClick = onClick)
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
