package com.money.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** The four operations offered next to an amount, with the sign shown to the user. */
enum class AmountOp(val sign: String) {
    PLUS("+"), MINUS("−"), TIMES("×"), DIVIDE("÷");

    /** Applies the operation to [cents]; never below zero, and dividing by zero changes nothing. */
    fun apply(cents: Long, operand: BigDecimal, operandIsMoney: Boolean): Long {
        val right = if (operandIsMoney) operand.movePointRight(2) else operand
        val result = when (this) {
            PLUS -> BigDecimal(cents) + right
            MINUS -> BigDecimal(cents) - right
            TIMES -> BigDecimal(cents) * right
            DIVIDE -> if (right.signum() == 0) BigDecimal(cents) else BigDecimal(cents).divide(right, 2, RoundingMode.HALF_UP)
        }
        return result.setScale(0, RoundingMode.HALF_UP).toLong().coerceAtLeast(0)
    }

    companion object {
        fun of(ch: Char): AmountOp? = when (ch) {
            '+' -> PLUS
            '-', '−' -> MINUS
            '*', '×', 'x', 'X' -> TIMES
            '/', '÷', ':' -> DIVIDE
            else -> null
        }
    }
}

/**
 * Amounts typed with operations, such as "11,00×2" or "30-4,50", worked out from left to right
 * like a pocket calculator. Euros are added and subtracted; multiplying and dividing take plain numbers.
 */
object AmountMath {
    /** The result in cents, or null when [text] isn't an amount. */
    fun evaluate(text: String): Long? {
        val parts = split(text) ?: return null
        var cents = Money.parse(parts.first) ?: return null
        for ((op, number) in parts.second) {
            if (number.isEmpty()) continue
            val value = number.replace(',', '.').toBigDecimalOrNull() ?: return null
            cents = op.apply(cents, value, operandIsMoney = op == AmountOp.PLUS || op == AmountOp.MINUS)
        }
        return cents
    }

    /** Whether [text] has at least one operation, so the result is worth showing apart. */
    fun hasOperation(text: String): Boolean = text.drop(1).any { AmountOp.of(it) != null }

    private fun split(text: String): Pair<String, List<Pair<AmountOp, String>>>? {
        val clean = text.replace(" ", "")
        if (clean.isEmpty()) return null
        var first: String? = null
        val rest = mutableListOf<Pair<AmountOp, String>>()
        val current = StringBuilder()
        var op: AmountOp? = null
        clean.forEachIndexed { i, ch ->
            val found = if (i > 0) AmountOp.of(ch) else null
            if (found != null) {
                if (first == null) first = current.toString() else rest += op!! to current.toString()
                current.clear()
                op = found
            } else current.append(ch)
        }
        if (first == null) first = current.toString() else rest += op!! to current.toString()
        return first!! to rest
    }
}
