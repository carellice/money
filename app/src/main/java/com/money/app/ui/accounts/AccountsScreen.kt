package com.money.app.ui.accounts

import com.money.app.ui.theme.skin
import com.money.app.ui.theme.tile
import com.money.app.ui.theme.linen
import com.money.app.ui.theme.paper
import com.money.app.ui.theme.well
import com.money.app.ui.theme.gel
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.money.app.ui.theme.CircleShape
import com.money.app.ui.theme.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.AccountEntity
import com.money.app.data.MoneyRepository
import com.money.app.data.Palette
import com.money.app.data.TransactionEntity
import com.money.app.data.TransferEntity
import com.money.app.data.balanceOf
import com.money.app.data.methodKey
import com.money.app.data.movementsOf
import com.money.app.data.of
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.ui.add.NoteField
import com.money.app.ui.appContainer
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AmountField
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.ColorSwatches
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.FullScreenPage
import com.money.app.ui.components.IconPicker
import com.money.app.ui.components.LocalClosePage
import com.money.app.ui.components.LocalPayments
import com.money.app.ui.components.Masked
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.SectionTitle
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.TopBar
import com.money.app.ui.components.TransactionRow
import com.money.app.ui.components.balancesHidden
import com.money.app.ui.components.rememberHideBalancesToggle
import com.money.app.ui.components.bouncyClick
import com.money.app.ui.components.hideable
import com.money.app.ui.settings.allChoices
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import com.money.app.ui.tour.LocalGuide
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Accounts with what moved them: everything needed to know each balance. */
data class AccountsState(
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val transfers: List<TransferEntity>,
) {
    fun balance(account: AccountEntity) = accounts.balanceOf(account, transactions, transfers)
    val total: Long get() = accounts.sumOf { balance(it) }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun MoneyRepository.accountsState(): Flow<AccountsState> = accounts.flatMapLatest { list ->
    // Only movements registered after the oldest stated balance can move one.
    val since = list.minOfOrNull { it.balanceAt } ?: Long.MAX_VALUE
    combine(transactionsCreatedAfter(since), transfers) { txs, transfers -> AccountsState(list, txs, transfers) }
}

private fun newAccount(count: Int) =
    AccountEntity(name = "", icon = "bank", color = Palette.all[(count + 5) % Palette.all.size])

/** Rounded badge with the account's icon on its color. */
@Composable
fun AccountBadge(account: AccountEntity, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).skin({ gel(Color(account.color), RoundedCornerShape(size * 0.26f)) }) { clip(RoundedCornerShape(size * 0.36f)).background(Color(account.color).tile()) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[account.icon], null, tint = Ink, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun AccountRow(account: AccountEntity, balance: Long, onClick: () -> Unit) {
    val c = Ui.colors
    Row(Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.98f, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        AccountBadge(account, size = 36.dp)
        Spacer(Modifier.size(12.dp))
        Text(account.name, style = Ui.type.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(if (balancesHidden()) "••••" else Money.format(balance), style = Ui.type.label, color = if (balance < 0) c.expenseText else c.ink)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
    }
}

/** Home: how much money there is, in total and account by account. */
@Composable
fun AccountsCard(onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val state by remember(repo) { repo.accountsState() }.collectAsStateWithLifecycle(null)
    val s = state ?: return
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    // The tour asks for the first account: the editor opens here, and the tour goes on once it closes.
    val guide = LocalGuide.current
    if (guide.addAccountRequest) LaunchedEffect(Unit) {
        guide.addAccountRequest = false
        editing = newAccount(s.accounts.size)
    }

    Column(modifier) {
        SectionTitle("Disponibile", action = if (s.accounts.isEmpty()) null else "Nuovo conto", onAction = { editing = newAccount(s.accounts.size) })
        SoftCard {
            if (s.accounts.isEmpty()) {
                Text("Aggiungi i tuoi conti per sapere sempre quanti soldi hai a disposizione.", style = Ui.type.body, color = c.inkSoft)
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Aggiungi un conto", { editing = newAccount(0) }, icon = Icons.Rounded.Add)
            } else {
                val total = s.total
                val hidden = balancesHidden()
                // One tap hides or shows just the balances; the eye only says so.
                Row(Modifier.bouncyClick(pressedScale = 0.97f, onClick = rememberHideBalancesToggle()), verticalAlignment = Alignment.CenterVertically) {
                    Masked(Ui.type.display, c.ink, hidden = hidden) {
                        Text(Money.format(total), style = Ui.type.display, color = if (total < 0) c.expenseText else c.ink)
                    }
                    Spacer(Modifier.size(10.dp))
                    Icon(
                        if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (hidden) "Mostra saldi" else "Nascondi saldi",
                        tint = c.inkFaint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                s.accounts.forEach { account ->
                    Spacer(Modifier.height(14.dp))
                    AccountRow(account, s.balance(account)) { onOpen(account.id) }
                }
            }
        }
    }

    editing?.let { account ->
        AccountEditor(
            account, s.accounts,
            onDismiss = {
                editing = null
                guide.resume()
            },
            onSave = {
                editing = null
                scope.launch { repo.saveAccount(it) }
                guide.resume()
            },
        )
    }
}

/** Settings: the accounts, with the button to add one. */
@Composable
fun AccountsSection(onOpen: (Long) -> Unit) {
    val c = Ui.colors
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val accounts by repo.accounts.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<AccountEntity?>(null) }

    accounts.forEach { account ->
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyClick(pressedScale = 0.98f) { onOpen(account.id) }
                .skin({ paper(c.surface, RoundedCornerShape(16.dp), elevation = 3.dp, dark = c.isDark) }) { clip(RoundedCornerShape(22.dp)).background(c.surface) }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountBadge(account)
            Spacer(Modifier.size(14.dp))
            Text(account.name, style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkFaint)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.98f) { editing = newAccount(accounts.size) }
            .skin({ paper(c.surface, RoundedCornerShape(16.dp), elevation = 3.dp, dark = c.isDark) }) { clip(RoundedCornerShape(22.dp)).background(c.surface) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accentText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Aggiungi conto", style = Ui.type.bodyStrong, color = c.ink)
            Text("Il conto in banca, i contanti... con i metodi di pagamento che li usano", style = Ui.type.caption, color = c.inkSoft)
        }
    }

    editing?.let { account ->
        AccountEditor(
            account, accounts,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                scope.launch { repo.saveAccount(it) }
            },
        )
    }
}

// --- one account -------------------------------------------------------------------------------

class AccountViewModel(container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repo = container.repository
    val id: Long = handle.get<Long>("id") ?: 0L
    val state = repo.accountsState().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val categories = repo.categories.map { list -> list.associateBy { it.id } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun save(account: AccountEntity) = viewModelScope.launch { repo.saveAccount(account) }
    fun saveTransfer(transfer: TransferEntity) = viewModelScope.launch { repo.saveTransfer(transfer) }
    fun deleteTransfer(transfer: TransferEntity) = viewModelScope.launch { repo.deleteTransfer(transfer) }

    /** [onDone] closes the screen once the account is gone, not before (that would stop the deletion). */
    fun delete(account: AccountEntity, onDone: () -> Unit) = viewModelScope.launch {
        repo.deleteAccount(account)
        onDone()
    }
}

private enum class AccountPage { EDIT, TRANSFER, BALANCE }

/** One account: its balance, transfers, balance correction and what moved it. */
@Composable
fun AccountScreen(onBack: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val vm = appViewModel { c, h -> AccountViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Ui.colors
    val payments = LocalPayments.current
    var page by remember { mutableStateOf<AccountPage?>(null) }
    var removing by remember { mutableStateOf<TransferEntity?>(null) }
    val s = state
    val account = s?.accounts?.firstOrNull { it.id == vm.id }

    Column(Modifier.fillMaxSize().linen(c.isDark)) {
        TopBar(account?.name ?: "Conto", onBack = onBack)
        if (s == null || account == null) return@Column
        val balance = s.balance(account)
        val nameOf = { id: Long -> s.accounts.firstOrNull { it.id == id }?.name ?: "conto eliminato" }
        // Movements and transfers together, newest first.
        val history: List<Any> = (s.accounts.movementsOf(account, s.transactions) + s.transfers.of(account))
            .sortedWith(compareByDescending<Any> { if (it is TransactionEntity) it.date else (it as TransferEntity).date }
                .thenByDescending { if (it is TransactionEntity) it.createdAt else (it as TransferEntity).createdAt })
        val names = account.methodKeys.mapNotNull { key -> allChoices(payments).firstOrNull { it.methodKey == key }?.let(payments::label) }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            item {
                SoftCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        AccountBadge(account, size = 56.dp)
                        Spacer(Modifier.height(6.dp))
                        Masked(Ui.type.display, c.ink, Modifier.bouncyClick(pressedScale = 0.97f, onClick = rememberHideBalancesToggle()), hidden = balancesHidden()) {
                            Text(Money.format(balance), style = Ui.type.display, color = if (balance < 0) c.expenseText else c.ink)
                        }
                        Text(
                            if (names.isEmpty()) "Nessun metodo di pagamento: tocca Modifica per sceglierli" else names.joinToString(" · "),
                            style = Ui.type.caption,
                            color = c.inkSoft,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Trasferisci", false, {
                        if (s.accounts.size < 2) Toast.makeText(context, "Per un trasferimento serve almeno un altro conto", Toast.LENGTH_SHORT).show()
                        else page = AccountPage.TRANSFER
                    }, icon = Icons.Rounded.SwapVert)
                    Pill("Correggi saldo", false, { page = AccountPage.BALANCE }, icon = Icons.Rounded.Refresh)
                    Pill("Modifica", false, { page = AccountPage.EDIT }, icon = Icons.Rounded.Edit)
                }
                FormLabel("Dal ${Dates.long(Instant.ofEpochMilli(account.balanceAt).atZone(ZoneId.systemDefault()).toLocalDate())}")
                if (history.isEmpty()) {
                    Text("Ancora nessun movimento su questo conto.", style = Ui.type.body, color = c.inkSoft, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(history, key = { if (it is TransactionEntity) "t${it.id}" else "x${(it as TransferEntity).id}" }) { item ->
                if (item is TransactionEntity) {
                    TransactionRow(item, categories, { onOpenTransaction(item.id) }, showDate = Dates.short(LocalDate.ofEpochDay(item.date)))
                } else {
                    val transfer = item as TransferEntity
                    val incoming = transfer.toId == account.id
                    Row(
                        Modifier.fillMaxWidth().bouncyClick(pressedScale = 0.97f) { removing = transfer }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SwapVert, null, tint = c.ink)
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (incoming) "Da ${nameOf(transfer.fromId)}" else "Verso ${nameOf(transfer.toId)}",
                                style = Ui.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf("Trasferimento", transfer.note.trim(), Dates.short(LocalDate.ofEpochDay(transfer.date)))
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                style = Ui.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            hideable(Money.format(if (incoming) transfer.amountCents else -transfer.amountCents, signed = true)),
                            style = Ui.type.amount,
                            color = if (incoming) c.incomeText else c.ink,
                        )
                    }
                }
            }
        }

        when (page) {
            AccountPage.EDIT -> AccountEditor(
                account, s.accounts,
                onDismiss = { page = null },
                onSave = {
                    page = null
                    vm.save(it)
                },
                onDelete = {
                    page = null
                    vm.delete(it, onBack)
                },
            )
            AccountPage.TRANSFER -> TransferEditor(account, s.accounts, onDismiss = { page = null }) {
                page = null
                vm.saveTransfer(it)
            }
            AccountPage.BALANCE -> BalanceEditor(account, balance, onDismiss = { page = null }) {
                page = null
                vm.save(it)
            }
            null -> {}
        }
    }

    removing?.let { transfer ->
        val accounts = s?.accounts.orEmpty()
        val name = { id: Long -> accounts.firstOrNull { it.id == id }?.name ?: "conto eliminato" }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Eliminare il trasferimento?") },
            text = { Text("${Money.format(transfer.amountCents)} da ${name(transfer.fromId)} a ${name(transfer.toId)}.") },
            confirmButton = {
                TextButton({
                    removing = null
                    vm.deleteTransfer(transfer)
                }) { Text("Elimina", color = c.expenseText) }
            },
            dismissButton = { TextButton({ removing = null }) { Text("Annulla") } },
        )
    }
}

// --- editors -----------------------------------------------------------------------------------

@Composable
private fun AccountEditor(
    original: AccountEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
    onDelete: (AccountEntity) -> Unit = {},
) {
    val c = Ui.colors
    val context = LocalContext.current
    val payments = LocalPayments.current
    val isNew = original.id == 0L
    var name by remember { mutableStateOf(original.name) }
    var icon by remember { mutableStateOf(original.icon) }
    var color by remember { mutableLongStateOf(original.color) }
    var methods by remember { mutableStateOf(original.methodKeys) }
    var balance by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    // A method moves one account only: the ones already taken by another account can't be picked here.
    val taken = accounts.filter { it.id != original.id }.flatMap { a -> a.methodKeys.map { it to a.name } }.toMap()
    val preview = original.copy(name = name, icon = icon, color = color)

    FullScreenPage(if (isNew) "Nuovo conto" else "Modifica conto", onDismiss) {
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
                AccountBadge(preview, size = 96.dp)
            }
            FormLabel("Nome")
            NoteField(name, { name = it }, "Es. Conto Intesa")
            if (isNew) {
                FormLabel("Quanto c'è adesso")
                AmountField(balance, { balance = it })
            }
            FormLabel("Metodi di pagamento")
            Text(
                "Le spese e le entrate pagate così, registrate da adesso, aggiornano il saldo.",
                style = Ui.type.caption,
                color = c.inkSoft,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allChoices(payments).forEach { choice ->
                    val key = choice.methodKey
                    val owner = taken[key]
                    val label = payments.label(choice)
                    Pill(
                        if (owner != null) "$label · $owner" else label,
                        key in methods,
                        {
                            if (owner != null) Toast.makeText(context, "$label è già nel conto $owner", Toast.LENGTH_SHORT).show()
                            else methods = if (key in methods) methods - key else methods + key
                        },
                        modifier = if (owner != null) Modifier.alpha(0.45f) else Modifier,
                        color = Color(color),
                        icon = AppIcons[payments.icon(choice)],
                    )
                }
            }
            FormLabel("Icona")
            IconPicker(icon, Color(color)) { icon = it }
            FormLabel("Colore")
            ColorSwatches(Palette.all, color) { color = it }
            Spacer(Modifier.size(24.dp))
            val closePage = LocalClosePage.current
            PrimaryButton(
                "Salva",
                {
                    var saved = preview.copy(name = name.trim(), methods = methods.joinToString(","))
                    if (isNew) saved = saved.copy(balanceCents = Money.parse(balance) ?: 0L, balanceAt = System.currentTimeMillis())
                    closePage { onSave(saved) }
                },
                enabled = name.isNotBlank(),
                color = Color(color),
                icon = Icons.Rounded.Check,
            )
            if (!isNew) {
                Text(
                    "Elimina conto",
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
            text = { Text("I movimenti restano; si cancellano i trasferimenti di questo conto.") },
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

@Composable
private fun TransferEditor(
    initial: AccountEntity,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (TransferEntity) -> Unit,
) {
    val c = Ui.colors
    val today = LocalDate.now()
    var fromId by remember { mutableLongStateOf(initial.id) }
    var toId by remember { mutableLongStateOf(accounts.firstOrNull { it.id != initial.id }?.id ?: 0L) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val cents = Money.parse(amount) ?: 0L

    @Composable
    fun Picker(selected: Long, other: Long, onSelect: (Long) -> Unit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a ->
                Pill(
                    a.name,
                    selected == a.id,
                    {
                        if (a.id != other) onSelect(a.id)
                        else {
                            // Picking the other side's account swaps the two.
                            val from = fromId
                            fromId = toId
                            toId = from
                        }
                    },
                    modifier = if (a.id == other) Modifier.alpha(0.45f) else Modifier,
                    color = Color(a.color),
                    icon = AppIcons[a.icon],
                )
            }
        }
    }

    FullScreenPage("Trasferimento", onDismiss) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FormLabel("Da")
            Picker(fromId, toId) { fromId = it }
            FormLabel("A")
            Picker(toId, fromId) { toId = it }
            FormLabel("Importo")
            AmountField(amount, { amount = it })
            FormLabel("Data")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Oggi", date == today, { date = today })
                Pill("Ieri", date == today.minusDays(1), { date = today.minusDays(1) })
                val other = date != today && date != today.minusDays(1)
                Pill(if (other) Dates.long(date) else "Altra data", other, { picking = true }, icon = Icons.Rounded.CalendarMonth)
            }
            FormLabel("Nota")
            NoteField(note, { note = it }, "Es. prelievo al bancomat")
            Text(
                "Non è né una spesa né un'entrata: sposta solo i soldi da un conto all'altro.",
                style = Ui.type.caption,
                color = c.inkFaint,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.size(24.dp))
            val closePage = LocalClosePage.current
            PrimaryButton(
                "Trasferisci ${Money.format(cents)}",
                { closePage { onSave(TransferEntity(fromId = fromId, toId = toId, amountCents = cents, date = date.toEpochDay(), note = note.trim())) } },
                enabled = cents > 0 && fromId != toId && toId != 0L,
                icon = Icons.Rounded.Check,
            )
        }
    }

    if (picking) {
        val state = rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Annulla") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun BalanceEditor(account: AccountEntity, balance: Long, onDismiss: () -> Unit, onSave: (AccountEntity) -> Unit) {
    val c = Ui.colors
    var amount by remember { mutableStateOf(if (balance > 0) Money.toInput(balance) else "") }
    FullScreenPage("Correggi saldo", onDismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FormLabel("Quanto c'è davvero su ${account.name}")
            AmountField(amount, { amount = it })
            Text(
                "Il saldo riparte da qui: contano solo i movimenti e i trasferimenti registrati da adesso.",
                style = Ui.type.caption,
                color = c.inkFaint,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.size(24.dp))
            val closePage = LocalClosePage.current
            PrimaryButton(
                "Salva",
                { closePage { onSave(account.copy(balanceCents = Money.parse(amount) ?: 0L, balanceAt = System.currentTimeMillis())) } },
                color = Color(account.color),
                icon = Icons.Rounded.Check,
            )
        }
    }
}
