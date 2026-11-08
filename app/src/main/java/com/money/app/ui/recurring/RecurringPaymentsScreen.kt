package com.money.app.ui.recurring

import com.money.app.ui.theme.linen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.money.app.data.RecurringEntity
import com.money.app.data.RecurringKind
import com.money.app.data.paymentChoice
import com.money.app.domain.Money
import com.money.app.ui.appViewModel
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.EmptyState
import com.money.app.ui.components.LocalPayments
import com.money.app.ui.components.Segmented
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.SoftProgress
import com.money.app.ui.components.TopBar
import com.money.app.ui.theme.Ui

/** What the active fixed expenses cost on each payment method, on a page of its own. */
@Composable
fun RecurringPaymentsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> RecurringViewModel(c) }
    val all by vm.items.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf<RecurringKind?>(null) }
    var yearly by rememberSaveable { mutableStateOf(false) }
    val c = Ui.colors
    Column(Modifier.fillMaxSize().linen(c.isDark)) {
        TopBar("Metodi di pagamento", onBack = onBack)
        val active = all?.filter { it.active } ?: return@Column
        val visible = if (kind == null) active else active.filter { it.kind == kind }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            KindChips(active, kind, { kind = it })
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("Al mese", "All'anno"), if (yearly) 1 else 0, { yearly = it == 1 })
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) EmptyState("Niente qui", "Nessuna voce attiva di questo tipo.", icon = "wallet")
            else PaymentTotals(visible, yearly)
        }
    }
}

@Composable
private fun PaymentTotals(items: List<RecurringEntity>, yearly: Boolean) {
    val c = Ui.colors
    val payments = LocalPayments.current
    fun cost(it: RecurringEntity) = if (yearly) it.frequency.yearlyCents(it.amountCents) else it.frequency.monthlyCents(it.amountCents)
    val totals = items.groupBy { it.paymentChoice }
        .map { (choice, list) -> Triple(choice, list.size, list.sumOf(::cost)) }
        .sortedByDescending { it.third }
    val total = totals.sumOf { it.third }.coerceAtLeast(1)
    val unit = if (yearly) "/anno" else "/mese"
    Column {
        SoftCard(padding = 0.dp) {
            totals.forEachIndexed { i, (choice, count, cents) ->
                if (i > 0) Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(c.surfaceHigh))
                val color = choice.customId?.let { payments.custom[it] }?.let { Color(it.color) } ?: c.accent
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(AppIcons[payments.icon(choice)], null, tint = c.ink, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(payments.label(choice), style = Ui.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (count == 1) "1 voce" else "$count voci") + " · ${cents * 100 / total}%",
                                style = Ui.type.caption,
                                color = c.inkSoft,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(cents), style = Ui.type.bodyStrong, color = c.ink)
                            Text(unit, style = Ui.type.caption, color = c.inkFaint)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SoftProgress(cents.toFloat() / total, color)
                }
            }
            if (totals.size > 1) {
                Row(
                    Modifier.fillMaxWidth().background(c.surfaceHigh.copy(alpha = 0.5f)).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Totale", style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                    Text(Money.format(totals.sumOf { it.third }), style = Ui.type.bodyStrong, color = c.ink)
                    Text(unit, style = Ui.type.caption, color = c.inkFaint, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
