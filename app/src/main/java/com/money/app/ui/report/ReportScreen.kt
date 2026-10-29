package com.money.app.ui.report

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.ai.Commentary
import com.money.app.ui.ai.ThinkingDots
import com.money.app.ui.ai.aiText
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import com.money.app.data.Palette
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.domain.MonthReport
import com.money.app.domain.Pagella
import com.money.app.domain.ReportBuilder
import com.money.app.domain.Slice
import com.money.app.domain.percent
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AnimatedAmount
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.CategoryBadge
import com.money.app.ui.components.DonutChart
import com.money.app.ui.components.DonutSegment
import com.money.app.ui.components.EmptyState
import com.money.app.ui.components.MonthSwitcher
import com.money.app.ui.components.Pill
import com.money.app.ui.components.SectionTitle
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.SoftProgress
import com.money.app.ui.components.SpendingCalendar
import com.money.app.ui.components.TransactionRow
import com.money.app.ui.components.TrendBars
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.components.staggered
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(private val container: AppContainer) : ViewModel() {
    val month: StateFlow<YearMonth> = container.month

    val report: StateFlow<MonthReport?> = container.month.flatMapLatest { m ->
        combine(
            container.repository.transactionsBetween(m.minusMonths(5).atDay(1), m.atEndOfMonth()),
            container.repository.categories,
        ) { txs, cats -> ReportBuilder.build(m, txs, cats) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMonth(m: YearMonth) {
        container.month.value = m
    }

    private val ai = container.ai
    val aiReady: StateFlow<Boolean> = ai.store.config.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ai.store.config.value != null)
    var commentary by mutableStateOf<Commentary?>(null)
        private set
    var aiBusy by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set

    fun loadCommentary(m: YearMonth) = viewModelScope.launch {
        aiError = null
        commentary = ai.cachedCommentary(m)
    }

    fun generateCommentary(m: YearMonth) = viewModelScope.launch {
        aiBusy = true
        aiError = null
        runCatching { ai.monthCommentary(m) }
            .onSuccess { commentary = Commentary(it, upToDate = true) }
            .onFailure { aiError = it.message }
        aiBusy = false
    }
}

@Composable
fun ReportScreen(onOpenTransaction: (Long) -> Unit, onOpenChat: () -> Unit, onOpenAiSettings: () -> Unit) {
    val vm = appViewModel { c, _ -> ReportViewModel(c) }
    val month by vm.month.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { ReportExport(context) }
    val aiReady by vm.aiReady.collectAsStateWithLifecycle()
    val c = Ui.colors

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val r = report ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            context.contentResolver.openOutputStream(uri)?.use { exporter.writePdf(r, it) }
            Toast.makeText(context, "PDF salvato", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 156.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                Text("Report", style = Ui.type.display, color = c.ink)
                Spacer(Modifier.height(8.dp))
                MonthSwitcher(month, vm::setMonth)
            }
        }
        val r = report ?: return@LazyColumn
        if (r.isEmpty) {
            item { EmptyState("Mese vuoto", "Nessun movimento a ${Dates.monthName(r.month).lowercase()}: niente da raccontare.", icon = "book") }
            return@LazyColumn
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Pill("Condividi immagine", false, {
                        scope.launch { exporter.share(exporter.cardFile(r), "image/png", "Il mio mese con Money") }
                    }, icon = Icons.Rounded.Image)
                }
                item {
                    Pill("Condividi PDF", false, {
                        scope.launch { exporter.share(exporter.pdfFile(r), "application/pdf", "Report ${Dates.monthYear(r.month)}") }
                    }, icon = Icons.Rounded.PictureAsPdf)
                }
                item { Pill("Salva PDF", false, { savePdf.launch("Money-${r.month}.pdf") }, icon = Icons.Rounded.Download) }
            }
        }
        r.pagella?.let { p -> item(key = "pagella_${r.month}") { PagellaCard(p, Modifier.staggered(0)) } }
        item(key = "ai_${r.month}") {
            LaunchedEffect(r.month, r.transactions.size) { vm.loadCommentary(r.month) }
            AiCommentaryCard(
                ready = aiReady,
                commentary = vm.commentary,
                busy = vm.aiBusy,
                error = vm.aiError,
                onGenerate = { vm.generateCommentary(r.month) },
                onConfigure = onOpenAiSettings,
                onChat = onOpenChat,
                modifier = Modifier.staggered(1),
            )
        }
        item { StatGrid(r, Modifier.staggered(1)) }
        if (r.insights.isNotEmpty()) {
            item {
                Column(Modifier.staggered(2)) {
                    SectionTitle("Cosa è successo")
                    SoftCard {
                        r.insights.forEachIndexed { i, insight ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            Row {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .size(14.dp)
                                        .blob(
                                            when (insight.positive) {
                                                true -> c.income
                                                false -> c.expense
                                                null -> c.accent
                                            },
                                            seed = i,
                                        )
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(insight.text, style = Ui.type.body, color = c.ink)
                            }
                        }
                    }
                }
            }
        }
        if (r.expenseSlices.isNotEmpty()) {
            item(key = "donut_${r.month}") { CategoryBreakdown(r.expenseSlices, r.expense, "Spese per categoria", Modifier.staggered(3)) }
        }
        item {
            Column(Modifier.staggered(4)) {
                SectionTitle("Calendario delle spese")
                SoftCard {
                    SpendingCalendar(r.month, r.daily, c.expense)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Più il cerchio è grande, più hai speso quel giorno.",
                        style = Ui.type.caption,
                        color = c.inkFaint,
                    )
                }
            }
        }
        item {
            Column(Modifier.staggered(5)) {
                SectionTitle("Ultimi ${r.trend.size} mesi")
                SoftCard {
                    TrendBars(r.trend, highlight = r.month)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend("Entrate", c.income)
                        Legend("Uscite", c.expense)
                    }
                }
            }
        }
        val compared = r.expenseSlices.filter { it.prevCents > 0 || it.cents > 0 }
        if (r.prevExpense > 0 && compared.isNotEmpty()) {
            item {
                Column(Modifier.staggered(6)) {
                    SectionTitle("Rispetto a ${Dates.monthName(r.month.minusMonths(1)).lowercase()}")
                    SoftCard {
                        compared.sortedByDescending { kotlin.math.abs(it.cents - it.prevCents) }.take(6).forEachIndexed { i, s ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            DeltaRow(s)
                        }
                    }
                }
            }
        }
        if (r.budgets.isNotEmpty()) {
            item {
                Column(Modifier.staggered(7)) {
                    SectionTitle("Budget")
                    SoftCard {
                        r.budgets.forEachIndexed { i, b ->
                            if (i > 0) Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(b.category.name, style = Ui.type.label, color = c.ink, modifier = Modifier.weight(1f))
                                Text("${Money.short(b.spent)} / ${Money.short(b.budget)}", style = Ui.type.caption, color = c.inkSoft)
                            }
                            Spacer(Modifier.height(6.dp))
                            SoftProgress(
                                b.ratio,
                                if (b.ratio > 1f) c.expense else if (b.ratio > 0.8f) Color(Palette.BUTTER) else c.income,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.staggered(8)) {
                SectionTitle("Fisse e variabili")
                SoftCard {
                    val fixedShare = if (r.expense > 0) r.fixedExpense.toFloat() / r.expense else 0f
                    SplitBar(fixedShare, c.accent, Color(Palette.PEACH))
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Legend("Fisse ${Money.short(r.fixedExpense)}", c.accent)
                        Spacer(Modifier.weight(1f))
                        Legend("Variabili ${Money.short(r.variableExpense)}", Color(Palette.PEACH))
                    }
                }
            }
        }
        if (r.payments.isNotEmpty()) {
            item {
                Column(Modifier.staggered(9)) {
                    SectionTitle("Come hai pagato")
                    SoftCard {
                        val max = r.payments.maxOf { it.second }.coerceAtLeast(1)
                        r.payments.forEachIndexed { i, (method, cents) ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons[method.icon], null, tint = c.inkSoft, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(method.label, style = Ui.type.label, color = c.ink, modifier = Modifier.width(110.dp))
                                SoftProgress(cents.toFloat() / max, Color(Palette.all[i % Palette.all.size]), Modifier.weight(1f), height = 8.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(Money.short(cents), style = Ui.type.caption, color = c.inkSoft)
                            }
                        }
                    }
                }
            }
        }
        if (r.incomeSlices.isNotEmpty()) {
            item(key = "income_${r.month}") { CategoryBreakdown(r.incomeSlices, r.income, "Entrate per fonte", Modifier.staggered(10)) }
        }
        if (r.top.isNotEmpty()) {
            item { SectionTitle("Le spese più grandi", Modifier.staggered(11)) }
            items(r.top, key = { "top_${it.id}" }) { tx ->
                TransactionRow(
                    tx, r.categories, { onOpenTransaction(tx.id) },
                    Modifier.staggered(11),
                    showDate = Dates.short(LocalDate.ofEpochDay(tx.date)),
                )
            }
        }
    }
}

@Composable
private fun AiCommentaryCard(
    ready: Boolean,
    commentary: Commentary?,
    busy: Boolean,
    error: String?,
    onGenerate: () -> Unit,
    onConfigure: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ui.colors
    SoftCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).blob(c.accent, seed = 61, amplitude = 0.1f, animated = busy), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Il commento di Money AI", style = Ui.type.titleSmall, color = c.ink)
        }
        Spacer(Modifier.height(12.dp))
        when {
            !ready -> {
                Text("Collega Cloudflare Workers AI per un commento scritto sul mese e consigli su misura.", style = Ui.type.body, color = c.inkSoft)
                Spacer(Modifier.height(12.dp))
                Pill("Configura", false, onConfigure, icon = Icons.Rounded.Settings)
            }
            busy -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThinkingDots(c.accentText)
                    Spacer(Modifier.width(12.dp))
                    Text("Sto leggendo i tuoi movimenti…", style = Ui.type.body, color = c.inkSoft)
                }
            }
            commentary != null -> {
                Text(aiText(commentary.text), style = Ui.type.body, color = c.ink)
                Spacer(Modifier.height(12.dp))
                if (!commentary.upToDate) {
                    Text("I movimenti sono cambiati dopo questo commento.", style = Ui.type.caption, color = c.inkFaint)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Rigenera", false, onGenerate, icon = Icons.Rounded.Refresh)
                    Pill("Chiedi a Money", false, onChat, icon = Icons.AutoMirrored.Rounded.Chat)
                }
            }
            else -> {
                Text("Fatti raccontare com'è andato il mese e cosa migliorare.", style = Ui.type.body, color = c.inkSoft)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Genera commento", true, onGenerate, icon = Icons.Rounded.AutoAwesome)
                    Pill("Chiedi a Money", false, onChat, icon = Icons.AutoMirrored.Rounded.Chat)
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Ui.type.caption, color = c.expenseText)
        }
    }
}

@Composable
private fun PagellaCard(p: Pagella, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(p) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 140f))
    }
    SoftCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        scaleX = reveal.value
                        scaleY = reveal.value
                        rotationZ = (1f - reveal.value) * -40f
                    }
                    .blob(Color(p.grade.color), seed = 5, amplitude = 0.1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(p.grade.letter, style = Ui.type.hero.copy(fontSize = 72.sp, lineHeight = 72.sp), color = Ink)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("PAGELLA DEL MESE", style = Ui.type.caption, color = c.inkFaint)
                Text(p.grade.title, style = Ui.type.title, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(p.summary, style = Ui.type.body, color = c.inkSoft)
                Spacer(Modifier.height(8.dp))
                Text("${p.score}/100", style = Ui.type.label, color = c.accentText)
            }
        }
    }
}

@Composable
private fun StatGrid(r: MonthReport, modifier: Modifier = Modifier) {
    val c = Ui.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Entrate", r.income, r.prevIncome, c.income, higherIsGood = true, Modifier.weight(1f))
            StatTile("Uscite", r.expense, r.prevExpense, c.expense, higherIsGood = false, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Risparmiato", r.balance, r.prevIncome - r.prevExpense, c.accent, higherIsGood = true, Modifier.weight(1f))
            SoftCard(Modifier.weight(1f), color = Color(Palette.BUTTER), padding = 16.dp) {
                Text("Media al giorno", style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
                AnimatedAmount(r.avgDailyExpense, Ui.type.title, color = Ink)
                Text(
                    r.savingsRate?.let { "risparmio ${percent(it)}" } ?: "nessuna entrata",
                    style = Ui.type.caption,
                    color = Ink.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, cents: Long, previous: Long, color: Color, higherIsGood: Boolean, modifier: Modifier) {
    SoftCard(modifier, color = color, padding = 16.dp) {
        Text(label, style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
        AnimatedAmount(cents, Ui.type.title, color = Ink)
        if (previous != 0L) {
            val delta = (cents - previous).toFloat() / kotlin.math.abs(previous)
            val up = delta >= 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                    null,
                    tint = Ink.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    " ${if (up) "+" else "−"}${percent(kotlin.math.abs(delta))} · ${if (up == higherIsGood) "bene" else "occhio"}",
                    style = Ui.type.caption,
                    color = Ink.copy(alpha = 0.8f),
                )
            }
        } else {
            Text("primo mese", style = Ui.type.caption, color = Ink.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun CategoryBreakdown(slices: List<Slice>, total: Long, title: String, modifier: Modifier = Modifier) {
    val c = Ui.colors
    var selected by remember { mutableIntStateOf(-1) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    Column(modifier) {
        SectionTitle(title)
        SoftCard {
            val current = slices.getOrNull(selected)
            DonutChart(
                slices.map { DonutSegment(it.share, Color(it.category.color)) },
                modifier = Modifier.fillMaxWidth(0.78f).align(Alignment.CenterHorizontally),
                selected = selected,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current?.category?.name ?: "Totale", style = Ui.type.caption, color = c.inkSoft)
                    AnimatedAmount(current?.cents ?: total, Ui.type.title, color = c.ink)
                    current?.let { Text(percent(it.share), style = Ui.type.label, color = c.inkSoft) }
                }
            }
            Spacer(Modifier.height(16.dp))
            slices.forEachIndexed { i, s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .background(if (i == selected) c.surfaceHigh else Color.Transparent)
                        .bouncyClick(pressedScale = 0.98f, haptic = false) {
                            selected = if (selected == i) -1 else i
                            expanded = if (expanded == s.category.id) null else s.category.id
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(s.category.icon, Color(s.category.color), size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.category.name, style = Ui.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.count} movimenti", style = Ui.type.caption, color = c.inkFaint)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(s.cents), style = Ui.type.label, color = c.ink)
                            Text(percent(s.share), style = Ui.type.caption, color = c.inkSoft)
                        }
                    }
                    AnimatedVisibility(expanded == s.category.id && s.subs.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(start = 46.dp, top = 8.dp)) {
                            s.subs.forEach { sub ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text(sub.name, style = Ui.type.caption, color = c.inkSoft, modifier = Modifier.weight(1f))
                                    Text(Money.format(sub.cents), style = Ui.type.caption, color = c.ink)
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
private fun DeltaRow(s: Slice) {
    val c = Ui.colors
    val delta = s.cents - s.prevCents
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(s.category.icon, Color(s.category.color), size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Text(s.category.name, style = Ui.type.label, color = c.ink, modifier = Modifier.weight(1f))
        Text("${Money.short(s.prevCents)} → ", style = Ui.type.caption, color = c.inkFaint)
        Text(Money.short(s.cents), style = Ui.type.caption, color = c.ink)
        Spacer(Modifier.width(8.dp))
        Text(
            (if (delta > 0) "+" else "") + Money.short(delta),
            style = Ui.type.label,
            color = Ink,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (delta > 0) c.expense else c.income)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SplitBar(share: Float, left: Color, right: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(share) { anim.animateTo(share, spring(dampingRatio = 0.7f, stiffness = 80f)) }
    Row(Modifier.fillMaxWidth().height(22.dp).clip(CircleShape)) {
        if (anim.value > 0.001f) Box(Modifier.weight(anim.value).fillMaxHeight().background(left))
        if (anim.value < 0.999f) Box(Modifier.weight(1f - anim.value).fillMaxHeight().background(right))
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Ui.type.caption, color = Ui.colors.inkSoft)
    }
}
