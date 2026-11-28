package com.money.app.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import com.money.app.data.AccountEntity
import com.money.app.data.TransferEntity
import com.money.app.ui.components.AppIcons
import com.money.app.ui.components.LocalAccounts
import com.money.app.ui.components.Pill
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.domain.SettleUp
import com.money.app.ui.components.PrimaryButton
import com.money.app.ui.components.SectionTitle
import com.money.app.ui.components.SoftCard
import com.money.app.ui.components.hideable
import com.money.app.ui.settings.MemberAvatar
import com.money.app.ui.theme.Ui
import java.time.YearMonth

/**
 * Who owes whom to even out the month: the people split the expenses
 * equally, and once the money has been given the expenses counted so far are marked as settled.
 * Settling up can also register each payment as a transfer between the accounts of the two people.
 */
@Composable
fun SettleUpCard(settle: SettleUp, month: YearMonth, onSettled: (List<Long>, List<TransferEntity>) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    var confirm by remember { mutableStateOf(false) }
    val accounts = LocalAccounts.current
    Column(modifier) {
        SectionTitle("Para i conti")
        SoftCard {
            if (settle.ids.isEmpty()) {
                Text(
                    if (settle.settledBefore) "Conti parati per ${Dates.monthName(month).lowercase()}."
                    else "Nessuna spesa da dividere a ${Dates.monthName(month).lowercase()}.",
                    style = Ui.type.bodyStrong,
                    color = c.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (settle.settledBefore) "Le prossime spese del mese compariranno qui."
                    else "Qui vedi chi deve dare quanto a chi, dividendo le spese in parti uguali.",
                    style = Ui.type.caption,
                    color = c.inkSoft,
                )
                return@SoftCard
            }
            settle.paid.forEachIndexed { i, (member, cents) ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberAvatar(member, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(member.name, style = Ui.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                    Text("ha pagato ", style = Ui.type.caption, color = c.inkSoft)
                    Text(hideable(Money.format(cents)), style = Ui.type.bodyStrong, color = c.ink)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (settle.transfers.isEmpty()) {
                Text("Siete pari: nessuno deve niente.", style = Ui.type.bodyStrong, color = c.ink)
            } else {
                settle.transfers.forEachIndexed { i, t ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(t.from, size = 28.dp)
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward, null, tint = c.inkFaint,
                            modifier = Modifier.padding(horizontal = 4.dp).size(16.dp),
                        )
                        MemberAvatar(t.to, size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.from.name) }
                                append(" deve dare ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(hideable(Money.format(t.cents))) }
                                append(" a ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.to.name) }
                            },
                            style = Ui.type.body,
                            color = c.ink,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${hideable(Money.format(settle.total))} divisi in parti uguali tra ${settle.paid.size} persone. " +
                    "Le spese escluse dal pagante non contano.",
                style = Ui.type.caption,
                color = c.inkFaint,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Conti parati", { confirm = true }, icon = Icons.Rounded.Handshake)
        }
    }

    if (confirm) {
        // Each payment leaves an account of who gives and lands on one of who receives: theirs if they have any, otherwise any.
        fun choicesFor(memberId: Long) = accounts.filter { it.memberId == memberId }.ifEmpty { accounts }
        val from = remember { mutableStateListOf(*settle.transfers.map { t -> accounts.firstOrNull { it.memberId == t.from.id }?.id }.toTypedArray()) }
        val to = remember { mutableStateListOf(*settle.transfers.map { t -> accounts.firstOrNull { it.memberId == t.to.id }?.id }.toTypedArray()) }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Conti parati?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Le ${settle.ids.size} spese contate finora non verranno più considerate. " +
                            "Se aggiungi altre spese a ${Dates.monthName(month).lowercase()}, conteranno solo quelle."
                    )
                    if (accounts.size >= 2 && settle.transfers.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Scegli da quale conto partono i soldi e su quale arrivano: il trasferimento si registra da solo. " +
                                "Senza conti, si segnano solo come parati.",
                            style = Ui.type.caption,
                            color = c.inkSoft,
                        )
                        settle.transfers.forEachIndexed { i, t ->
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "${t.from.name} dà ${hideable(Money.format(t.cents))} a ${t.to.name}",
                                style = Ui.type.bodyStrong,
                                color = c.ink,
                            )
                            AccountChoice("Da", choicesFor(t.from.id), from[i]) { from[i] = it }
                            AccountChoice("A", choicesFor(t.to.id), to[i]) { to[i] = it }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    confirm = false
                    val note = "Para i conti · ${Dates.monthName(month).lowercase()}"
                    val transfers = settle.transfers.indices.mapNotNull { i ->
                        val a = from[i] ?: return@mapNotNull null
                        val b = to[i]?.takeIf { it != a } ?: return@mapNotNull null
                        TransferEntity(fromId = a, toId = b, amountCents = settle.transfers[i].cents, date = java.time.LocalDate.now().toEpochDay(), note = note)
                    }
                    onSettled(settle.ids, transfers)
                }) { Text("Sì, parati") }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("Annulla") } },
        )
    }
}

/** Accounts to pick for one side of a payment; touching the picked one again leaves it out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChoice(label: String, accounts: List<AccountEntity>, selected: Long?, onSelect: (Long?) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(label, style = Ui.type.caption, color = Ui.colors.inkSoft)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { a ->
            Pill(a.name, selected == a.id, { onSelect(if (selected == a.id) null else a.id) }, color = Color(a.color), icon = AppIcons[a.icon])
        }
    }
}
