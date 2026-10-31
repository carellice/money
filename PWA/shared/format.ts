import { dayOfWeek, monthYear, today as todayEpoch, ymd, type Month } from './date.ts'

// Java's Italian NumberFormat groups thousands from 1.000 on; Intl needs useGrouping "always" for that.
const grouping = 'always' as unknown as boolean
const currency = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR', useGrouping: grouping })
const integer = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 0, useGrouping: grouping })

export const Money = {
  /** "1.240,50 €", with "+"/"−" when [signed]. */
  format(cents: number, signed = false): string {
    const text = currency.format(Math.abs(cents) / 100)
    if (!signed || cents === 0) return cents < 0 ? `−${text}` : text
    return cents > 0 ? `+${text}` : `−${text}`
  },

  /** Whole euros, no decimals: "1.240 €". */
  short(cents: number): string {
    const sign = cents < 0 ? '−' : ''
    return `${sign}${integer.format(Math.trunc(Math.abs(cents) / 100))} €`
  },

  /** Integer part ("1.240") and decimal part (",50") for big displays. */
  parts(cents: number): [string, string] {
    const value = Math.abs(Math.trunc(cents))
    return [integer.format(Math.trunc(value / 100)), ',' + String(value % 100).padStart(2, '0')]
  },

  /** Parses user input such as "12", "12,5", "1.240,50" into cents. */
  parse(text: string): number | null {
    const clean = text.trim().replaceAll('.', '').replaceAll(' ', '').replace(',', '.')
    if (!clean || !/^-?\d*(\.\d*)?$/.test(clean) || clean === '.' || clean === '-') return null
    const value = Number(clean)
    if (!Number.isFinite(value)) return null
    return Math.round(value * 100 + (value >= 0 ? 1e-9 : -1e-9))
  },

  /** Cents to an editable string: 1250 -> "12,50", 1200 -> "12". */
  toInput(cents: number): string {
    return cents % 100 === 0
      ? String(Math.trunc(cents / 100))
      : `${Math.trunc(cents / 100)},${String(Math.abs(cents % 100)).padStart(2, '0')}`
  },
}

const monthNames = ['gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno', 'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre']
const monthShortNames = ['gen', 'feb', 'mar', 'apr', 'mag', 'giu', 'lug', 'ago', 'set', 'ott', 'nov', 'dic']
const weekdays = ['lunedì', 'martedì', 'mercoledì', 'giovedì', 'venerdì', 'sabato', 'domenica']
const weekdaysShort = ['lun', 'mar', 'mer', 'gio', 'ven', 'sab', 'dom']

export function cap(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export const Dates = {
  monthName(month: Month): string {
    return cap(monthNames[monthYear(month).m - 1])
  },

  monthYear(month: Month): string {
    return `${Dates.monthName(month)} ${monthYear(month).y}`
  },

  monthShort(month: Month): string {
    return cap(monthShortNames[monthYear(month).m - 1])
  },

  weekday(epoch: number): string {
    return weekdays[dayOfWeek(epoch) - 1]
  },

  dayHeader(epoch: number, today = todayEpoch()): string {
    if (epoch === today) return 'Oggi'
    if (epoch === today - 1) return 'Ieri'
    const { m, d } = ymd(epoch)
    return `${cap(Dates.weekday(epoch))} ${d} ${monthNames[m - 1]}`
  },

  long(epoch: number): string {
    const { y, m, d } = ymd(epoch)
    return y === ymd(todayEpoch()).y ? `${d} ${monthNames[m - 1]}` : `${d} ${monthNames[m - 1]} ${y}`
  },

  short(epoch: number): string {
    const { m, d } = ymd(epoch)
    return `${weekdaysShort[dayOfWeek(epoch) - 1]} ${d} ${monthShortNames[m - 1]}`
  },

  numeric(epoch: number): string {
    const { y, m, d } = ymd(epoch)
    return `${String(d).padStart(2, '0')}/${String(m).padStart(2, '0')}/${y}`
  },

  /** "oggi", "domani", "tra 5 giorni", "3 giorni fa". */
  relative(epoch: number, today = todayEpoch()): string {
    const days = epoch - today
    if (days === 0) return 'oggi'
    if (days === 1) return 'domani'
    if (days === -1) return 'ieri'
    if (days > 1) return `tra ${days} giorni`
    return `${-days} giorni fa`
  },
}

export function percent(value: number): string {
  return `${Math.trunc(value * 100)}%`
}
