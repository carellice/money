package com.money.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.money.app.data.CategoryEntity
import com.money.app.data.MemberEntity
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import com.money.app.domain.Money
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.delay

/** Fades and lifts content in once, staggered by [index]. Survives scrolling in lazy lists. */
@Composable
fun Modifier.staggered(index: Int, step: Long = 55L): Modifier {
    var played by rememberSaveable { mutableStateOf(false) }
    val anim = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(index.coerceAtMost(12) * step)
            anim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
            played = true
        }
    }
    return graphicsLayer {
        alpha = anim.value.coerceIn(0f, 1f)
        translationY = (1f - anim.value) * 48.dp.toPx()
    }
}

/** People of the household by id, provided once at the root for every movement row. */
val LocalMembers = compositionLocalOf<Map<Long, MemberEntity>> { emptyMap() }

@Composable
fun TransactionRow(
    tx: TransactionEntity,
    categories: Map<Long, CategoryEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: String? = null,
) {
    val c = Ui.colors
    val category = categories[tx.categoryId]
    val sub = tx.subcategoryId?.let { categories[it] }
    val title = tx.note.ifBlank { sub?.name ?: category?.name ?: "Movimento" }
    val member = tx.memberId?.let { LocalMembers.current[it] }
    val subtitle = buildList {
        member?.let { add(it.name) }
        if (tx.note.isNotBlank()) add(sub?.name ?: category?.name ?: "")
        else if (sub != null) add(category?.name ?: "")
        add(tx.payment.label)
        showDate?.let { add(it) }
    }.filter { it.isNotBlank() }.joinToString(" · ")
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.97f, haptic = false, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CategoryBadge(
            category?.icon ?: "more",
            category?.let { Color(it.color) } ?: c.surfaceHigh,
            size = 46.dp,
            seed = tx.id.toInt(),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = Ui.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tx.recurringId != null) {
                    Icon(Icons.Rounded.Autorenew, null, tint = c.inkFaint, modifier = Modifier.size(13.dp).padding(end = 2.dp))
                }
                if (tx.receiptPath != null) {
                    Icon(Icons.Rounded.Receipt, null, tint = c.inkFaint, modifier = Modifier.size(13.dp).padding(end = 2.dp))
                }
                Text(subtitle, style = Ui.type.caption, color = c.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val income = tx.type == TxType.INCOME
        Text(
            Money.format(if (income) tx.amountCents else -tx.amountCents, signed = true),
            style = Ui.type.amount,
            color = if (income) c.incomeText else c.ink,
        )
    }
}
