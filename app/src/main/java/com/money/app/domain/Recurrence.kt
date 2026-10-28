package com.money.app.domain

import com.money.app.data.Frequency
import com.money.app.data.RecurringEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Recurrence {

    fun occurrence(start: LocalDate, frequency: Frequency, n: Long): LocalDate =
        if (frequency.weeks > 0) start.plusWeeks(n * frequency.weeks)
        // Always computed from start so a 31st keeps landing on month end.
        else start.plusMonths(n * frequency.months)

    /** First occurrence strictly after [after] (epoch days). */
    fun nextAfter(startEpoch: Long, frequency: Frequency, after: Long): Long {
        val start = LocalDate.ofEpochDay(startEpoch)
        val limit = LocalDate.ofEpochDay(after)
        if (start.isAfter(limit)) return startEpoch
        var n = if (frequency.weeks > 0) ChronoUnit.WEEKS.between(start, limit) / frequency.weeks
        else ChronoUnit.MONTHS.between(start, limit) / frequency.months
        n = maxOf(0, n - 1)
        while (!occurrence(start, frequency, n).isAfter(limit)) n++
        return occurrence(start, frequency, n).toEpochDay()
    }

    /** Billing dates of [item] falling in [from]..[to], starting from its next unprocessed date. */
    fun occurrencesBetween(item: RecurringEntity, from: LocalDate, to: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var current = item.nextDate
        var guard = 0
        while (current <= to.toEpochDay() && guard++ < 500) {
            if (current >= from.toEpochDay()) result += LocalDate.ofEpochDay(current)
            current = nextAfter(item.startDate, item.frequency, current)
        }
        return result
    }
}
