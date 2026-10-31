import { monthsBetween, plusMonths, weeksBetween } from './date.ts'
import { frequency, type Frequency, type Recurring } from './model.ts'

export function occurrence(start: number, freq: Frequency, n: number): number {
  const f = frequency(freq)
  // Always computed from start so a 31st keeps landing on month end.
  return f.weeks > 0 ? start + n * f.weeks * 7 : plusMonths(start, n * f.months)
}

/** First occurrence strictly after [after] (epoch days). */
export function nextAfter(start: number, freq: Frequency, after: number): number {
  if (start > after) return start
  const f = frequency(freq)
  let n = f.weeks > 0 ? Math.trunc(weeksBetween(start, after) / f.weeks) : Math.trunc(monthsBetween(start, after) / f.months)
  n = Math.max(0, n - 1)
  while (occurrence(start, freq, n) <= after) n++
  return occurrence(start, freq, n)
}

/** Billing dates of [item] falling in [from]..[to], starting from its next unprocessed date. */
export function occurrencesBetween(item: Recurring, from: number, to: number): number[] {
  const result: number[] = []
  let current = item.nextDate
  let guard = 0
  while (current <= to && guard++ < 500) {
    if (current >= from) result.push(current)
    current = nextAfter(item.startDate, item.frequency, current)
  }
  return result
}
