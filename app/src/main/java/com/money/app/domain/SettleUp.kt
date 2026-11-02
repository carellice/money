package com.money.app.domain

import com.money.app.data.MemberEntity
import com.money.app.data.Settle
import com.money.app.data.TransactionEntity
import com.money.app.data.TxType
import kotlin.math.min
import kotlin.math.roundToLong

/** One payment that evens out the month: [from] gives [cents] to [to]. */
data class SettleTransfer(val from: MemberEntity, val to: MemberEntity, val cents: Long)

data class SettleUp(
    /** What each person paid among the expenses still to settle, in the order of the people. */
    val paid: List<Pair<MemberEntity, Long>>,
    val total: Long,
    val transfers: List<SettleTransfer>,
    /** Expenses that count now: marked as settled once the money has been given. */
    val ids: List<Long>,
    /** Some expenses of the month were settled up already. */
    val settledBefore: Boolean,
)

/**
 * Splits the month's expenses equally among everyone in the household: whoever paid less than
 * their share gives the difference to whoever paid more. Expenses left out by the payer, or
 * already settled, don't count.
 */
object SettleUpBuilder {
    fun build(transactions: List<TransactionEntity>, members: List<MemberEntity>): SettleUp? {
        if (members.size < 2) return null
        val known = members.associateBy { it.id }
        val expenses = transactions.filter { it.type == TxType.EXPENSE && it.memberId in known }
        val open = expenses.filter { it.settle == Settle.OPEN }
        val paid = members.map { m -> m to open.filter { it.memberId == m.id }.sumOf { it.amountCents } }
        val total = paid.sumOf { it.second }
        // Balances times the number of people, so equal shares stay whole cents until the end.
        val n = members.size
        val balances = paid.map { (m, cents) -> m to cents * n - total }
        val creditors = balances.filter { it.second > 0 }.map { it.first to it.second }.toMutableList()
        val debtors = balances.filter { it.second < 0 }.map { it.first to -it.second }.toMutableList()
        val transfers = mutableListOf<SettleTransfer>()
        var c = 0
        var d = 0
        while (c < creditors.size && d < debtors.size) {
            val amount = min(creditors[c].second, debtors[d].second)
            val cents = (amount.toDouble() / n).roundToLong()
            if (cents > 0) transfers += SettleTransfer(debtors[d].first, creditors[c].first, cents)
            creditors[c] = creditors[c].first to creditors[c].second - amount
            debtors[d] = debtors[d].first to debtors[d].second - amount
            if (creditors[c].second == 0L) c++
            if (debtors[d].second == 0L) d++
        }
        return SettleUp(
            paid = paid,
            total = total,
            transfers = transfers,
            ids = open.map { it.id },
            settledBefore = expenses.any { it.settle == Settle.SETTLED },
        )
    }
}
