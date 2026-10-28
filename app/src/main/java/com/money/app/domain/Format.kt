package com.money.app.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

val ItalianLocale: Locale = Locale.ITALY

object Money {
    private val currency = NumberFormat.getCurrencyInstance(ItalianLocale)
    private val integer = NumberFormat.getIntegerInstance(ItalianLocale)

    /** "1.240,50 €", with "+"/"−" when [signed]. */
    fun format(cents: Long, signed: Boolean = false): String {
        val text = currency.format(abs(cents) / 100.0)
        return when {
            !signed || cents == 0L -> if (cents < 0) "−$text" else text
            cents > 0 -> "+$text"
            else -> "−$text"
        }
    }

    /** Whole euros, no decimals: "1.240 €". */
    fun short(cents: Long): String {
        val sign = if (cents < 0) "−" else ""
        return "$sign${integer.format(abs(cents) / 100)} €"
    }

    /** Integer part ("1.240") and decimal part (",50") for big displays. */
    fun parts(cents: Long): Pair<String, String> {
        val value = abs(cents)
        return integer.format(value / 100) to ",%02d".format(value % 100)
    }

    /** Parses user input such as "12", "12,5", "1.240,50" into cents. */
    fun parse(text: String): Long? {
        val clean = text.trim().replace(".", "").replace(" ", "").replace(',', '.')
        if (clean.isEmpty()) return null
        val value = clean.toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** Cents to an editable string: 1250 -> "12,50", 1200 -> "12". */
    fun toInput(cents: Long): String =
        if (cents % 100 == 0L) (cents / 100).toString() else "%d,%02d".format(cents / 100, cents % 100)
}

object Dates {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", ItalianLocale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", ItalianLocale)
    private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", ItalianLocale)
    private val numericFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", ItalianLocale)

    fun monthName(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, ItalianLocale).cap()

    fun monthYear(month: YearMonth): String = "${monthName(month)} ${month.year}"

    fun monthShort(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.SHORT_STANDALONE, ItalianLocale).cap()

    fun weekday(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.FULL, ItalianLocale)

    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> "${weekday(date).cap()} ${date.format(dayMonth)}"
    }

    fun long(date: LocalDate): String =
        if (date.year == LocalDate.now().year) date.format(dayMonth) else date.format(dayMonthYear)

    fun short(date: LocalDate): String = date.format(shortDay)
    fun numeric(date: LocalDate): String = date.format(numericFormat)

    /** "oggi", "domani", "tra 5 giorni", "3 giorni fa". */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = date.toEpochDay() - today.toEpochDay()
        return when {
            days == 0L -> "oggi"
            days == 1L -> "domani"
            days == -1L -> "ieri"
            days > 1 -> "tra $days giorni"
            else -> "${-days} giorni fa"
        }
    }

    fun String.cap(): String = replaceFirstChar { it.titlecase(ItalianLocale) }
}

fun percent(value: Float): String = "${(value * 100).toInt()}%"
