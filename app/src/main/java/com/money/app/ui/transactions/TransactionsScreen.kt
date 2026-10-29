package com.money.app.ui.transactions

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.CategoryEntity
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.ui.appViewModel
import com.money.app.ui.components.EmptyState
import com.money.app.ui.components.MonthSwitcher
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.TransactionRow
import com.money.app.ui.components.staggered
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(val date: LocalDate, val items: List<TransactionEntity>) {
    val net: Long get() = items.sumOf { if (it.type == TxType.INCOME) it.amountCents else -it.amountCents }
}

data class TransactionsState(
    val groups: List<DayGroup>,
    val categories: Map<Long, CategoryEntity>,
    val income: Long,
    val expense: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val month: StateFlow<YearMonth> = container.month
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(0) // 0 all, 1 expenses, 2 incomes

    val state: StateFlow<TransactionsState?> = container.month.flatMapLatest { m ->
        combine(repo.transactionsIn(m), repo.categories, query, filter) { txs, cats, q, f ->
            val byId = cats.associateBy { it.id }
            val needle = q.trim().lowercase()
            val visible = txs.filter { tx ->
                (f == 0 || (f == 1 && tx.type == TxType.EXPENSE) || (f == 2 && tx.type == TxType.INCOME)) &&
                    (needle.isEmpty() || listOfNotNull(
                        tx.note, byId[tx.categoryId]?.name, tx.subcategoryId?.let { byId[it]?.name }, tx.payment.label,
                    ).any { it.lowercase().contains(needle) })
            }
            TransactionsState(
                groups = visible.groupBy { it.date }.map { (d, items) -> DayGroup(LocalDate.ofEpochDay(d), items) },
                categories = byId,
                income = visible.filter { it.type == TxType.INCOME }.sumOf { it.amountCents },
                expense = visible.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMonth(m: YearMonth) {
        container.month.value = m
    }

    fun delete(tx: TransactionEntity) = viewModelScope.launch { repo.deleteTransaction(tx) }
    fun restore(tx: TransactionEntity) = viewModelScope.launch { repo.saveTransaction(tx) }
    fun forget(tx: TransactionEntity) = repo.discardReceipt(tx)
}

@Composable
fun TransactionsScreen(onOpen: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> TransactionsViewModel(c) }
    val month by vm.month.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val c = Ui.colors

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 156.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text("Movimenti", style = Ui.type.display, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    MonthSwitcher(month, vm::setMonth)
                    Spacer(Modifier.height(14.dp))
                    SearchField(query) { vm.query.value = it }
                    Spacer(Modifier.height(12.dp))
                    Segmented(
                        listOf("Tutti", "Spese", "Entrate"), filter, { vm.filter.value = it },
                        colors = listOf(c.accent, c.expense, c.income),
                    )
                    state?.let { s ->
                        Spacer(Modifier.height(14.dp))
                        Row {
                            Text("Entrate ", style = Ui.type.label, color = c.inkSoft)
                            Text(Money.format(s.income), style = Ui.type.label, color = c.incomeText)
                            Spacer(Modifier.weight(1f))
                            Text("Uscite ", style = Ui.type.label, color = c.inkSoft)
                            Text(Money.format(s.expense), style = Ui.type.label, color = c.ink)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            val s = state ?: return@LazyColumn
            if (s.groups.isEmpty()) {
                item {
                    EmptyState(
                        if (query.isBlank()) "Nessun movimento" else "Nessun risultato",
                        if (query.isBlank()) "Questo mese è ancora vuoto." else "Prova con un'altra parola.",
                        icon = if (query.isBlank()) "wallet" else "more",
                    )
                }
            }
            s.groups.forEachIndexed { gi, group ->
                item(key = "h${group.date}") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp).animateItem().staggered(gi),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Dates.dayHeader(group.date), style = Ui.type.label, color = c.inkSoft, modifier = Modifier.weight(1f))
                        Text(Money.format(group.net, signed = true), style = Ui.type.caption, color = c.inkFaint)
                    }
                }
                items(group.items, key = { it.id }) { tx ->
                    SwipeRow(
                        onDelete = {
                            vm.delete(tx)
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    "Movimento eliminato", actionLabel = "Annulla", duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) vm.restore(tx) else vm.forget(tx)
                            }
                        },
                        modifier = Modifier.animateItem().staggered(gi),
                    ) {
                        TransactionRow(tx, s.categories, { onOpen(tx.id) }, Modifier.background(c.bg))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
    }
}

@Composable
private fun SwipeRow(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Ui.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            val active = state.targetValue == SwipeToDismissBoxValue.EndToStart
            val bg by animateColorAsState(if (active) c.expense else c.surfaceHigh, label = "swipeBg")
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(bg).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, "Elimina", tint = if (active) Ink else c.inkSoft)
            }
        },
    ) { content() }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Ui.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Ui.type.body.copy(color = c.ink),
        cursorBrush = SolidColor(c.accentText),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(c.surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.inkFaint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Box {
                    if (value.isEmpty()) Text("Cerca per nota, categoria, metodo…", style = Ui.type.body, color = c.inkFaint)
                    inner()
                }
            }
        },
    )
}
