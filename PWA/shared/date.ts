// Calendar helpers mirroring java.time on the Android app.
// Dates are epoch days (LocalDate.toEpochDay()), months are "month indexes": year * 12 + (month - 1).

const DAY_MS = 86_400_000

export type Ymd = { y: number; m: number; d: number }

export function epochOf(y: number, m: number, d: number): number {
  return Math.floor(Date.UTC(y, m - 1, d) / DAY_MS)
}

export function ymd(epoch: number): Ymd {
  const date = new Date(epoch * DAY_MS)
  return { y: date.getUTCFullYear(), m: date.getUTCMonth() + 1, d: date.getUTCDate() }
}

/** Today in the local time zone, as an epoch day. */
export function today(): number {
  const now = new Date()
  return epochOf(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

/** 1 = Monday ... 7 = Sunday, like DayOfWeek.value. */
export function dayOfWeek(epoch: number): number {
  // 1970-01-01 was a Thursday.
  return ((((epoch + 3) % 7) + 7) % 7) + 1
}

export function isLeap(y: number): boolean {
  return (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0
}

export function monthLength(y: number, m: number): number {
  return [31, isLeap(y) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1]
}

/** LocalDate.plusMonths: the day is clamped to the end of the target month. */
export function plusMonths(epoch: number, months: number): number {
  const { y, m, d } = ymd(epoch)
  const index = y * 12 + (m - 1) + months
  const ny = Math.floor(index / 12)
  const nm = index - ny * 12 + 1
  return epochOf(ny, nm, Math.min(d, monthLength(ny, nm)))
}

export function plusYears(epoch: number, years: number): number {
  return plusMonths(epoch, years * 12)
}

/** ChronoUnit.MONTHS.between. */
export function monthsBetween(start: number, end: number): number {
  const a = ymd(start)
  const b = ymd(end)
  let months = (b.y * 12 + b.m) - (a.y * 12 + a.m)
  if (months > 0 && b.d < a.d) months--
  else if (months < 0 && b.d > a.d) months++
  return months
}

/** ChronoUnit.WEEKS.between. */
export function weeksBetween(start: number, end: number): number {
  return Math.trunc((end - start) / 7)
}

// --- months -------------------------------------------------------------------------------

export type Month = number

export function monthOf(epoch: number): Month {
  const { y, m } = ymd(epoch)
  return y * 12 + (m - 1)
}

export function currentMonth(): Month {
  return monthOf(today())
}

export function monthYear(month: Month): { y: number; m: number } {
  const y = Math.floor(month / 12)
  return { y, m: month - y * 12 + 1 }
}

export function monthStart(month: Month): number {
  const { y, m } = monthYear(month)
  return epochOf(y, m, 1)
}

export function monthDays(month: Month): number {
  const { y, m } = monthYear(month)
  return monthLength(y, m)
}

export function monthEnd(month: Month): number {
  return monthStart(month) + monthDays(month) - 1
}

/** YearMonth.toString(): "2026-10". */
export function monthKey(month: Month): string {
  const { y, m } = monthYear(month)
  return `${y}-${String(m).padStart(2, '0')}`
}

export function parseMonthKey(key: string): Month | null {
  const match = /^(\d{4})-(\d{2})$/.exec(key)
  return match ? Number(match[1]) * 12 + Number(match[2]) - 1 : null
}

/** LocalDate.toString(): "2026-10-30". */
export function isoDate(epoch: number): string {
  const { y, m, d } = ymd(epoch)
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}
