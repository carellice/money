package com.money.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.Palette
import com.money.app.data.RecurringEntity
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.domain.MonthReport
import com.money.app.domain.ReportBuilder
import com.money.app.domain.percent
import com.money.app.ui.appContainer
import com.money.app.ui.appViewModel
import com.money.app.ui.update.UpdateBanner
import com.money.app.ui.components.AnimatedAmount
import com.money.app.ui.components.CategoryBadge
import com.money.app.ui.components.EmptyState
import com.money.app.ui.components.MonthSwitcher
import com.money.app.ui.components.RecurringLogo
import com.money.app.ui.components.RoundIconButton
import com.money.app.ui.components.SectionTitle
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.SoftProgress
import com.money.app.ui.components.TransactionRow
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.components.staggered
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.backdropBlobs
import com.money.app.ui.theme.blob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class HomeState(
    val report: MonthReport,
    val recent: List<TransactionEntity>,
    val upcoming: List<RecurringEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val month: StateFlow<YearMonth> = container.month

    val state: StateFlow<HomeState?> = container.month.flatMapLatest { m ->
        combine(
            repo.transactionsBetween(m.minusMonths(1).atDay(1), m.atEndOfMonth()),
            repo.categories,
            repo.recurring,
        ) { txs, cats, recurring ->
            val report = ReportBuilder.build(m, txs, cats, trendMonths = 2)
            HomeState(
                report = report,
                recent = report.transactions.take(5),
                upcoming = recurring.filter { it.active }.sortedBy { it.nextDate }.take(10),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMonth(m: YearMonth) {
        container.month.value = m
    }
}

@Composable
fun HomeScreen(
    onAdd: (TxType) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenRecurringItem: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val month by vm.month.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val updates = appContainer().updates
    val updateState by updates.state.collectAsStateWithLifecycle()
    val c = Ui.colors

    Box(
        Modifier
            .fillMaxSize()
            .backdropBlobs(listOf(c.accent, c.income, Color(Palette.ROSE)), alpha = if (c.isDark) 0.10f else 0.22f)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Money", style = Ui.type.label, color = c.accentText)
                        MonthSwitcher(month, vm::setMonth)
                    }
                    RoundIconButton(Icons.Rounded.AutoAwesome, onOpenChat, contentDescription = "Chiedi a Money")
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(Icons.Rounded.Settings, onOpenSettings, contentDescription = "Impostazioni")
                }
            }
            item(key = "update") { UpdateBanner(updateState, updates, Modifier.animateItem()) }
            val s = state ?: return@LazyColumn
            val r = s.report

            item { Hero(r, onAdd, Modifier.staggered(0)) }

            item { SpendGauge(r, Modifier.staggered(1)) }

            if (r.budgets.isNotEmpty()) {
                item {
                    Column(Modifier.staggered(2)) {
                        SectionTitle("Budget")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(r.budgets, key = { it.category.id }) { line ->
                                SoftCard(Modifier.width(150.dp), padding = 16.dp) {
                                    CategoryBadge(line.category.icon, Color(line.category.color), size = 36.dp)
                                    Spacer(Modifier.height(10.dp))
                                    Text(line.category.name, style = Ui.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${Money.short(line.spent)} di ${Money.short(line.budget)}",
                                        style = Ui.type.caption, color = c.inkSoft,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    SoftProgress(
                                        line.ratio,
                                        when {
                                            line.ratio > 1f -> c.expense
                                            line.ratio > 0.8f -> Color(Palette.BUTTER)
                                            else -> c.income
                                        },
                                        height = 8.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.staggered(3)) {
                    SectionTitle("Dove vanno i soldi", action = "Report", onAction = onOpenReport)
                    if (r.expenseSlices.isEmpty()) {
                        SoftCard {
                            Text("Ancora nessuna spesa questo mese.", style = Ui.type.body, color = c.inkSoft)
                        }
                    } else {
                        SoftCard {
                            r.expenseSlices.take(5).forEachIndexed { i, slice ->
                                if (i > 0) Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryBadge(slice.category.icon, Color(slice.category.color), size = 34.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row {
                                            Text(slice.category.name, style = Ui.type.label, color = c.ink, modifier = Modifier.weight(1f))
                                            Text(Money.format(slice.cents), style = Ui.type.label, color = c.ink)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        SoftProgress(slice.share, Color(slice.category.color), height = 8.dp)
                                    }
                                    Text(
                                        percent(slice.share),
                                        style = Ui.type.caption,
                                        color = c.inkSoft,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(44.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (s.upcoming.isNotEmpty()) {
                item {
                    Column(Modifier.staggered(4)) {
                        SectionTitle("Prossimi addebiti", action = "Tutti", onAction = onOpenRecurring)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(s.upcoming, key = { it.id }) { item ->
                                UpcomingBubble(item) { onOpenRecurringItem(item.id) }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    "Ultimi movimenti",
                    Modifier.staggered(5),
                    action = "Vedi tutti",
                    onAction = onOpenTransactions,
                )
            }
            if (s.recent.isEmpty()) {
                item {
                    EmptyState(
                        "Tutto tranquillo",
                        "Tocca il + per registrare la prima spesa o entrata del mese.",
                        modifier = Modifier.staggered(6),
                    )
                }
            }
            items(s.recent, key = { it.id }) { tx ->
                TransactionRow(
                    tx, r.categories, { onOpenTransaction(tx.id) },
                    modifier = Modifier.animateItem().staggered(6),
                    showDate = Dates.dayHeader(LocalDate.ofEpochDay(tx.date)),
                )
            }
        }
    }
}

@Composable
private fun Hero(r: MonthReport, onAdd: (TxType) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(
        -6f, 6f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Box(modifier.fillMaxWidth().height(310.dp)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(250.dp)
                .blob(
                    Brush.linearGradient(listOf(c.accent, Color(Palette.ROSE), Color(Palette.PEACH))),
                    seed = 11,
                    amplitude = 0.08f,
                    speed = 0.8f,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("saldo del mese", style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
                AnimatedAmount(r.balance, Ui.type.display, color = Ink, decimalsScale = 0.45f)
                if (r.savingsRate != null) {
                    Text(
                        if (r.balance >= 0) "risparmi ${percent(r.savingsRate)}" else "in rosso",
                        style = Ui.type.label,
                        color = Ink.copy(alpha = 0.8f),
                    )
                }
            }
        }
        MiniBlob(
            label = "entrate", cents = r.income, color = c.income, seed = 21,
            modifier = Modifier.align(Alignment.TopEnd).offset(y = bob.dp).graphicsLayer { rotationZ = bob * 0.4f },
            onClick = { onAdd(TxType.INCOME) },
        )
        MiniBlob(
            label = "uscite", cents = r.expense, color = c.expense, seed = 33,
            modifier = Modifier.align(Alignment.BottomStart).offset(y = (-bob).dp),
            onClick = { onAdd(TxType.EXPENSE) },
        )
    }
}

@Composable
private fun MiniBlob(label: String, cents: Long, color: Color, seed: Int, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(118.dp)
            .bouncyClick(onClick = onClick)
            .blob(color, seed = seed, amplitude = 0.1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
            Text(Money.short(cents), style = Ui.type.titleSmall, color = Ink, maxLines = 1)
            Text("+ aggiungi", style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SpendGauge(r: MonthReport, modifier: Modifier = Modifier) {
    val c = Ui.colors
    SoftCard(modifier) {
        val ratio = if (r.income > 0) r.expense.toFloat() / r.income else if (r.expense > 0) 1f else 0f
        val text = when {
            r.income == 0L && r.expense == 0L -> "Il mese è appena iniziato: nessun movimento."
            r.income == 0L -> "Nessuna entrata registrata finora."
            else -> "Hai speso il ${percent(ratio)} di quello che è entrato"
        }
        Text(text, style = Ui.type.bodyStrong, color = c.ink)
        Spacer(Modifier.height(12.dp))
        SoftProgress(
            ratio,
            when {
                ratio > 1f -> c.expense
                ratio > 0.8f -> Color(Palette.BUTTER)
                else -> c.income
            },
            height = 14.dp,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            Text("media ${Money.short(r.avgDailyExpense)} al giorno", style = Ui.type.caption, color = c.inkSoft, modifier = Modifier.weight(1f))
            if (r.fixedExpense > 0) Text("fisse ${Money.short(r.fixedExpense)}", style = Ui.type.caption, color = c.inkSoft)
        }
    }
}

@Composable
private fun UpcomingBubble(item: RecurringEntity, onClick: () -> Unit) {
    val c = Ui.colors
    val date = LocalDate.ofEpochDay(item.nextDate)
    Column(
        Modifier.width(92.dp).bouncyClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecurringLogo(item.brandKey, item.imagePath, item.icon, Color(item.color), size = 64.dp, animated = true)
        Spacer(Modifier.height(8.dp))
        Text(item.name, style = Ui.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Money.format(item.amountCents), style = Ui.type.caption, color = c.inkSoft, maxLines = 1)
        Text(
            Dates.relative(date),
            style = Ui.type.caption,
            color = if (date.toEpochDay() - LocalDate.now().toEpochDay() <= 3) c.expenseText else c.accentText,
            maxLines = 1,
        )
    }
}
