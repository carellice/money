package com.money.app.ui.home

import com.money.app.ui.theme.blob
import com.money.app.ui.theme.LocalSkeuo
import com.money.app.ui.theme.LocalMetro
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.money.app.ui.accounts.AccountsCard
import com.money.app.ui.appViewModel
import com.money.app.ui.update.UpdateBanner
import com.money.app.ui.components.AnimatedAmount
import com.money.app.ui.components.CategoryBadge
import com.money.app.ui.components.EmptyState
import com.money.app.ui.components.LocalHideAmounts
import com.money.app.ui.components.Masked
import com.money.app.ui.components.MonthSwitcher
import com.money.app.ui.components.RecurringLogo
import com.money.app.ui.components.RoundIconButton
import com.money.app.ui.components.SectionTitle
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.SoftProgress
import com.money.app.ui.components.TransactionRow
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.components.hideable
import com.money.app.ui.components.rememberHideAmountsToggle
import com.money.app.ui.components.staggered
import com.money.app.ui.theme.Ink
import com.money.app.ui.tour.LocalGuide
import com.money.app.ui.tour.tourSteps
import com.money.app.ui.tour.tourTarget
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.backdropBlobs
import com.money.app.ui.theme.Thread
import com.money.app.ui.theme.gel
import com.money.app.ui.theme.leather
import com.money.app.ui.components.onGel
import com.money.app.ui.theme.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
    onOpenReport: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenRecurringItem: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    /** Changes when the Home tab is selected again, to jump back to the top. */
    scrollToTop: Int = 0,
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
        val list = rememberLazyListState()
        LaunchedEffect(scrollToTop) { if (scrollToTop > 0) list.scrollToItem(0) }
        // The tour brings what it is talking about into view: the hero on top, then the accounts card.
        val guide = LocalGuide.current
        val tourTargetKey = if (guide.active) tourSteps[guide.step].target else null
        LaunchedEffect(tourTargetKey) {
            when (tourTargetKey) {
                "hero" -> list.animateScrollToItem(0)
                "accounts" -> list.animateScrollToItem(3, scrollOffset = -24)
            }
        }
        // The divider under the fixed bar shows up only once content scrolls beneath it.
        val dividerAlpha by animateFloatAsState(if (list.canScrollBackward) 1f else 0f, label = "barDivider")
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthSwitcher(month, vm::setMonth, Modifier.weight(1f))
                RoundIconButton(Icons.Rounded.AutoAwesome, onOpenChat, Modifier.tourTarget("chat"), contentDescription = "Chiedi a Money")
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.Settings, onOpenSettings, Modifier.tourTarget("settings"), contentDescription = "Impostazioni")
            }
            Box(Modifier.fillMaxWidth().height(1.dp).graphicsLayer { alpha = dividerAlpha }.background(c.line))
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 156.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "update") { UpdateBanner(updateState, updates, Modifier.animateItem()) }
                val s = state ?: return@LazyColumn
                val r = s.report

                item { Hero(r, onAdd, Modifier.staggered(0).tourTarget("hero")) }

                item { SpendGauge(r, Modifier.staggered(1)) }

                item { AccountsCard(onOpenAccount, Modifier.staggered(2).tourTarget("accounts")) }

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
                                            "${hideable(Money.short(line.spent))} di ${hideable(Money.short(line.budget))}",
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
                                                Text(hideable(Money.format(slice.cents)), style = Ui.type.label, color = c.ink)
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
            }
        }
    }
}

@Composable
private fun Hero(r: MonthReport, onAdd: (TxType) -> Unit, modifier: Modifier = Modifier) {
    if (LocalMetro.current) return HeroMetro(r = r, onAdd = onAdd, modifier = modifier)
    if (LocalSkeuo.current) return HeroSkeuo(r = r, onAdd = onAdd, modifier = modifier)
    val c = Ui.colors
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(
        -6f, 6f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val toggleHidden = rememberHideAmountsToggle()
    Box(modifier.fillMaxWidth().height(310.dp)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(250.dp)
                .bouncyClick(pressedScale = 0.96f, onClick = toggleHidden)
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
                // The whole bubble toggles; the eye only says so.
                val hidden = LocalHideAmounts.current
                Spacer(Modifier.height(6.dp))
                Icon(
                    if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (hidden) "Mostra importi" else "Nascondi importi",
                    tint = Ink.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
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

/** A stitched leather wallet: the month's balance stamped in, entrate and uscite as glossy buttons. */
@Composable
private fun HeroSkeuo(r: MonthReport, onAdd: (TxType) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val toggleHidden = rememberHideAmountsToggle()
    val stamp = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, -1.5f), 0f)
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .leather(shape, corner = 22.dp, shade = 0.05f)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.97f, onClick = toggleHidden),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("SALDO DEL MESE", style = Ui.type.caption.copy(letterSpacing = 0.25.em, shadow = stamp), color = Thread)
            Spacer(Modifier.height(4.dp))
            AnimatedAmount(
                r.balance,
                Ui.type.hero.copy(fontSize = 52.sp, lineHeight = 56.sp, shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 2.5f), 3f)),
                color = Color(0xFFFFF4DE),
                decimalsScale = 0.45f,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.savingsRate != null) {
                    Text(
                        if (r.balance >= 0) "risparmi ${percent(r.savingsRate)}" else "in rosso",
                        style = Ui.type.label.copy(shadow = stamp),
                        color = Thread.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                // The whole wallet toggles; the eye only says so.
                val hidden = LocalHideAmounts.current
                Icon(
                    if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (hidden) "Mostra importi" else "Nascondi importi",
                    tint = Thread.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroButton("entrate", r.income, c.income, Modifier.weight(1f)) { onAdd(TxType.INCOME) }
            HeroButton("uscite", r.expense, c.expense, Modifier.weight(1f)) { onAdd(TxType.EXPENSE) }
        }
    }
}

/** Live tiles: a wide accent tile that flips now and then to show its back, and two square ones to add money in or out. */
@Composable
private fun HeroMetro(r: MonthReport, onAdd: (TxType) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val toggleHidden = rememberHideAmountsToggle()
    val flip = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Like a live tile: rests a while, then turns over on its horizontal axis.
        while (true) {
            delay(6000)
            flip.animateTo(if (flip.value < 90f) 180f else 0f, tween(700, easing = LinearOutSlowInEasing))
        }
    }
    Column(modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .bouncyClick(onClick = toggleHidden)
                .graphicsLayer {
                    rotationX = flip.value
                    cameraDistance = 14f * density
                }
                .background(c.accent),
        ) {
            val back = flip.value > 90f
            // The back face is drawn mirrored back so its text reads the right way.
            Column(
                Modifier.fillMaxSize().graphicsLayer { if (back) rotationX = 180f }.padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!back) {
                    AnimatedAmount(r.balance, Ui.type.hero.copy(fontSize = 54.sp, lineHeight = 58.sp), color = Color.White, decimalsScale = 0.45f)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("saldo del mese", style = Ui.type.label.copy(fontWeight = FontWeight.Normal), color = Color.White, modifier = Modifier.weight(1f))
                        val hidden = LocalHideAmounts.current
                        Icon(
                            if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (hidden) "Mostra importi" else "Nascondi importi",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Column {
                        Text(
                            when {
                                r.savingsRate == null -> "nessuna entrata"
                                r.balance >= 0 -> "risparmi ${percent(r.savingsRate)}"
                                else -> "in rosso"
                            },
                            style = Ui.type.title.copy(fontSize = 34.sp, lineHeight = 38.sp),
                            color = Color.White,
                        )
                        Text("media ${hideable(Money.short(r.avgDailyExpense))} al giorno", style = Ui.type.body, color = Color.White.copy(alpha = 0.85f))
                        if (r.fixedExpense > 0) Text("fisse ${hideable(Money.short(r.fixedExpense))}", style = Ui.type.body, color = Color.White.copy(alpha = 0.85f))
                    }
                    Text("questo mese", style = Ui.type.label.copy(fontWeight = FontWeight.Normal), color = Color.White)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroTile("entrate", r.income, c.income, Icons.Rounded.Add, Modifier.weight(1f)) { onAdd(TxType.INCOME) }
            MetroTile("uscite", r.expense, c.expense, Icons.Rounded.Remove, Modifier.weight(1f)) { onAdd(TxType.EXPENSE) }
        }
    }
}

@Composable
private fun MetroTile(label: String, cents: Long, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .aspectRatio(1f)
            .bouncyClick(onClick = onClick)
            .background(color)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = "Aggiungi $label", tint = Color.White, modifier = Modifier.size(34.dp))
        Column {
            Masked(Ui.type.title, Color.White) {
                Text(Money.format(cents), style = Ui.type.title, color = Color.White, maxLines = 1)
            }
            Text(label, style = Ui.type.label.copy(fontWeight = FontWeight.Normal), color = Color.White)
        }
    }
}

@Composable
private fun HeroButton(label: String, cents: Long, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .bouncyClick(onClick = onClick)
            .gel(color, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = Ui.type.caption.onGel(), color = Ink.copy(alpha = 0.8f))
        Masked(Ui.type.titleSmall.onGel(), Ink) {
            Text(Money.format(cents), style = Ui.type.titleSmall.onGel(), color = Ink, maxLines = 1)
        }
        Text("+ aggiungi", style = Ui.type.caption.onGel(), color = Ink.copy(alpha = 0.75f))
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
            Text("media ${hideable(Money.short(r.avgDailyExpense))} al giorno", style = Ui.type.caption, color = c.inkSoft, modifier = Modifier.weight(1f))
            if (r.fixedExpense > 0) Text("fisse ${hideable(Money.short(r.fixedExpense))}", style = Ui.type.caption, color = c.inkSoft)
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
            Masked(Ui.type.titleSmall, Ink) {
                Text(Money.format(cents), style = Ui.type.titleSmall, color = Ink, maxLines = 1)
            }
            Text("+ aggiungi", style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
        }
    }
}

