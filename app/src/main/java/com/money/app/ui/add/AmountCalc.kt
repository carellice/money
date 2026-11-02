package com.money.app.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.money.app.domain.AmountOp

/**
 * The keypad's amount, POS style, with operations: "1100" then × and "2" gives 22,00 €, worked
 * out while typing. Euros added or subtracted are typed like the amount (last two digits are the
 * cents); multiplying and dividing take whole numbers.
 */
class AmountCalc {
    /** Digits of the first amount. */
    private var first by mutableStateOf("")
    /** Every operation typed after it, with its digits. */
    private var steps by mutableStateOf(listOf<Pair<AmountOp, String>>())

    val cents: Long
        get() = steps.fold(first.toLongOrNull() ?: 0L) { acc, (op, digits) ->
            when {
                digits.isEmpty() -> acc
                op.money -> op.apply(acc, digits.toBigDecimal().movePointLeft(2), operandIsMoney = true)
                else -> op.apply(acc, digits.toBigDecimal(), operandIsMoney = false)
            }
        }

    /** "12,50", empty before the first digit. */
    val text: String
        get() = if (first.isEmpty() && steps.isEmpty()) "" else pos(cents)

    /** "11,00 × 2", only once there is an operation. */
    val expression: String?
        get() = if (steps.isEmpty()) null else buildString {
            append(pos(first.toLongOrNull() ?: 0L))
            steps.forEach { (op, digits) ->
                append(' ').append(op.sign)
                if (digits.isNotEmpty()) append(' ').append(if (op.money) pos(digits.toLong()) else digits)
            }
        }

    /** The operation waiting for its number, if any. */
    val pending: AmountOp? get() = steps.lastOrNull()?.takeIf { it.second.isEmpty() }?.first

    fun set(cents: Long) {
        first = if (cents > 0) cents.toString() else ""
        steps = emptyList()
    }

    /** A digit (or "00") enters from the right, as on a card terminal. */
    fun press(key: String) {
        if (steps.isEmpty()) {
            val next = (first + key).trimStart('0')
            if (next.length <= MAX_DIGITS) first = next
            return
        }
        val (op, digits) = steps.last()
        val next = (digits + key).trimStart('0')
        if (next.length <= if (op.money) MAX_DIGITS else MAX_FACTOR) steps = steps.dropLast(1) + (op to next)
    }

    /** A second operation in a row replaces the first one. */
    fun operation(op: AmountOp) {
        if (first.isEmpty() && steps.isEmpty()) return
        steps = if (steps.lastOrNull()?.second?.isEmpty() == true) steps.dropLast(1) + (op to "") else steps + (op to "")
    }

    fun backspace() {
        val last = steps.lastOrNull()
        when {
            last == null -> first = first.dropLast(1)
            last.second.isEmpty() -> steps = steps.dropLast(1)
            else -> steps = steps.dropLast(1) + (last.first to last.second.dropLast(1))
        }
    }

    fun clear() {
        first = ""
        steps = emptyList()
    }

    private fun pos(cents: Long) = "%d,%02d".format(cents / 100, cents % 100)

    private val AmountOp.money: Boolean get() = this == AmountOp.PLUS || this == AmountOp.MINUS

    private companion object {
        /** Up to 9.999.999,99 €. */
        const val MAX_DIGITS = 9
        /** Multiply or divide by up to 9999. */
        const val MAX_FACTOR = 4
    }
}
