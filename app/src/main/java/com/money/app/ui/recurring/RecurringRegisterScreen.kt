package com.money.app.ui.recurring

import com.money.app.data.afterPayment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.money.app.AppContainer
import com.money.app.data.Frequency
import com.money.app.data.MemberEntity
import com.money.app.data.PaymentChoice
import com.money.app.data.RecurringEntity
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.data.paymentChoice
import com.money.app.domain.AmountMath
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.domain.Recurrence
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AmountField
import com.money.app.ui.components.FormLabel
import com.money.app.ui.components.OperationKeys
import com.money.app.ui.components.Pill
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.RecurringLogo
import com.money.app.ui.components.TopBar
import com.money.app.ui.settings.MemberAvatar
import com.money.app.ui.settings.PaymentPills
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RecurringRegisterViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val repo = container.repository
    private val id: Long = handle.get<Long>("id") ?: 0L

    var item by mutableStateOf<RecurringEntity?>(null)
        private set
    var amount by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    // Defaults to the usual method; this one payment may have gone another way.
    var payment by mutableStateOf<PaymentChoice?>(null)
    var memberId by mutableStateOf<Long?>(null)
    /** Billing date to set once the expense is added; null leaves the schedule as it is. */
    var nextDate by mutableStateOf<LocalDate?>(null)

    val members: StateFlow<List<MemberEntity>> =
        repo.members.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // The amount can be worked out on the spot, such as "11,00×2".
    val cents: Long get() = AmountMath.evaluate(amount) ?: 0L

    init {
        viewModelScope.launch {
            val found = repo.recurringItem(id) ?: return@launch
            amount = Money.toInput(found.amountCents)
            payment = found.paymentChoice
            memberId = repo.members.first().firstOrNull()?.id
            // Manual ones wait for the payment to move on; automatic ones already move by themselves.
            nextDate = if (found.autoRegister) null else renewal(found, found.frequency)
            item = found
        }
    }

    /** The current billing date moved forward by [period]. */
    fun renewal(item: RecurringEntity, period: Frequency): LocalDate =
        Recurrence.occurrence(LocalDate.ofEpochDay(item.nextDate), period, 1)

    fun save(onDone: (RecurringEntity) -> Unit) = viewModelScope.launch {
        val found = item ?: return@launch
        val cents = cents.takeIf { it > 0 } ?: return@launch
        val method = payment ?: found.paymentChoice
        val savedId = repo.saveTransaction(
            TransactionEntity(
                type = TxType.EXPENSE,
                amountCents = cents,
                date = date.toEpochDay(),
                categoryId = found.categoryId,
                subcategoryId = found.subcategoryId,
                payment = method.method,
                paymentId = method.customId,
                note = found.name,
                recurringId = found.id,
                memberId = memberId?.takeIf { id -> members.value.any { it.id == id } },
            )
        )
        if (savedId < 0) return@launch
        var updated = found.afterPayment()
        nextDate?.toEpochDay()?.takeIf { it != found.nextDate }?.let { epoch ->
            // The new date becomes the start of the schedule, so later renewals follow from it.
            updated = updated.copy(startDate = epoch, nextDate = epoch, notifiedFor = null)
        }
        if (updated != found) repo.saveRecurring(updated)
        onDone(found)
    }
}

/** "+1 mese", "+3 mesi", "+1 anno": how far the next renewal moves. */
private val Frequency.step: String
    get() = when {
        weeks > 0 -> if (weeks == 1) "+1 settimana" else "+$weeks settimane"
        months % 12 == 0 -> if (months == 12) "+1 anno" else "+${months / 12} anni"
        months == 1 -> "+1 mese"
        else -> "+$months mesi"
    }

/** Adds one payment of a fixed expense to the movements and, if wanted, sets its next renewal. */
@Composable
fun RecurringRegisterScreen(onClose: () -> Unit) {
    val vm = appViewModel { c, h -> RecurringRegisterViewModel(c, h) }
    val members by vm.members.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Ui.colors
    val today = LocalDate.now()
    var pickingDate by remember { mutableStateOf(false) }
    var pickingNext by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar("Aggiungi alle spese", onBack = onClose)
        val item = vm.item ?: return@Column
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecurringLogo(item.brandKey, item.imagePath, item.icon, Color(item.color), size = 56.dp, animated = true)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(item.name, style = Ui.type.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val due = Dates.long(LocalDate.ofEpochDay(item.nextDate))
                    Text(
                        item.installments?.let { "Rata ${(item.installmentsPaid + 1).coerceAtMost(it)} di $it · prevista $due" }
                            ?: "Rinnovo previsto $due",
                        style = Ui.type.caption,
                        color = c.inkSoft,
                    )
                }
            }
            FormLabel("Importo")
            AmountField(vm.amount, { vm.amount = it }, operations = true)
            OperationKeys(vm.amount, { vm.amount = it })
            AnimatedVisibility(AmountMath.hasOperation(vm.amount)) {
                Text(
                    "= ${Money.format(vm.cents)}",
                    style = Ui.type.titleSmall,
                    color = c.ink,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }
            if (members.isNotEmpty()) {
                FormLabel("Chi ha pagato?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { member ->
                        Pill(
                            member.name,
                            vm.memberId == member.id,
                            { vm.memberId = member.id },
                            color = Color(member.color),
                            leading = { MemberAvatar(member, size = 24.dp) },
                        )
                    }
                }
            }
            FormLabel("Data")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Oggi", vm.date == today, { vm.date = today })
                Pill("Ieri", vm.date == today.minusDays(1), { vm.date = today.minusDays(1) })
                val other = vm.date != today && vm.date != today.minusDays(1)
                Pill(if (other) Dates.long(vm.date) else "Altra data", other, { pickingDate = true }, icon = Icons.Rounded.CalendarMonth)
            }
            FormLabel("Metodo di pagamento")
            PaymentPills(vm.payment ?: item.paymentChoice, { vm.payment = it })

            FormLabel(if (item.installments != null) "Prossima rata" else "Prossimo rinnovo")
            val presets = Frequency.entries.map { vm.renewal(item, it) to it.step }
            val next = vm.nextDate
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Non cambiarlo", next == null, { vm.nextDate = null })
                presets.forEach { (date, label) -> Pill(label, next == date, { vm.nextDate = date }) }
                val other = next != null && presets.none { it.first == next }
                Pill(if (other) Dates.long(next!!) else "Altra data", other, { pickingNext = true }, icon = Icons.Rounded.CalendarMonth)
            }
            val last = item.installments != null && item.installmentsPaid + 1 >= item.installments
            Text(
                when {
                    last -> "È l'ultima rata: il finanziamento risulterà saldato."
                    next == null -> "Il prossimo rinnovo resta il ${Dates.long(LocalDate.ofEpochDay(item.nextDate))}."
                    else -> "Il prossimo rinnovo sarà il ${Dates.long(next)}."
                },
                style = Ui.type.caption,
                color = c.inkFaint,
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                "Aggiungi ${Money.format(vm.cents)}",
                {
                    vm.save { saved ->
                        Toast.makeText(context, "${saved.name} aggiunto ai movimenti del ${Dates.long(vm.date)}", Toast.LENGTH_SHORT).show()
                        onClose()
                    }
                },
                enabled = vm.cents > 0 && (members.isEmpty() || members.any { it.id == vm.memberId }),
                icon = Icons.Rounded.Check,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickingDate || pickingNext) {
        val initial = if (pickingDate) vm.date else vm.nextDate ?: LocalDate.ofEpochDay(vm.item?.nextDate ?: today.toEpochDay())
        val state = rememberDatePickerState(initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val dismiss = { pickingDate = false; pickingNext = false }
        DatePickerDialog(
            onDismissRequest = dismiss,
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        if (pickingDate) vm.date = picked else vm.nextDate = picked
                    }
                    dismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(dismiss) { Text("Annulla") } },
        ) { DatePicker(state) }
    }
}
