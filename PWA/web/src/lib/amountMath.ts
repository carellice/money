import { Money } from '../../../shared/format.ts'

// Amounts with operations, as in the Android app (AmountMath and AmountCalc): worked out from
// left to right like a pocket calculator. Euros are added and subtracted; multiplying and
// dividing take plain numbers.

export type AmountOp = '+' | '−' | '×' | '÷'
export const amountOps: AmountOp[] = ['+', '−', '×', '÷']

const isMoney = (op: AmountOp) => op === '+' || op === '−'

export function opOf(ch: string): AmountOp | null {
  switch (ch) {
    case '+': return '+'
    case '-': case '−': return '−'
    case '*': case '×': case 'x': case 'X': return '×'
    case '/': case '÷': case ':': return '÷'
    default: return null
  }
}

/** Applies [op] to [cents]; never below zero, and dividing by zero changes nothing. */
function apply(op: AmountOp, cents: number, operand: number): number {
  const result = op === '+' ? cents + operand * 100
    : op === '−' ? cents - operand * 100
      : op === '×' ? cents * operand
        : operand === 0 ? cents : cents / operand
  return Math.max(0, Math.round(result))
}

// --- keypad (POS style) ------------------------------------------------------------------------

/** First amount's digits, then every operation with its digits. */
export type Calc = { first: string; steps: [AmountOp, string][] }

export const emptyCalc: Calc = { first: '', steps: [] }

export const calcOf = (cents: number): Calc => ({ first: cents > 0 ? String(cents) : '', steps: [] })

const pos = (cents: number) => `${Math.trunc(cents / 100)},${String(cents % 100).padStart(2, '0')}`

export function calcCents(c: Calc): number {
  return c.steps.reduce((acc, [op, digits]) => {
    if (!digits) return acc
    return apply(op, acc, isMoney(op) ? Number(digits) / 100 : Number(digits))
  }, Number(c.first || '0'))
}

/** "12,50", empty before the first digit. */
export const calcText = (c: Calc) => (c.first || c.steps.length ? pos(calcCents(c)) : '')

/** "11,00 × 2", only once there is an operation. */
export function calcExpression(c: Calc): string | null {
  if (!c.steps.length) return null
  return [pos(Number(c.first || '0')), ...c.steps.map(([op, d]) => (d ? `${op} ${isMoney(op) ? pos(Number(d)) : d}` : op))].join(' ')
}

/** The operation waiting for its number, if any. */
export const calcPending = (c: Calc): AmountOp | null => {
  const last = c.steps[c.steps.length - 1]
  return last && !last[1] ? last[0] : null
}

/** A digit (or "00") enters from the right, as on a card terminal: up to 9.999.999,99 €, or a factor up to 9999. */
export function calcPress(c: Calc, key: string): Calc {
  if (!c.steps.length) {
    const next = (c.first + key).replace(/^0+/, '')
    return next.length <= 9 ? { ...c, first: next } : c
  }
  const [op, digits] = c.steps[c.steps.length - 1]
  const next = (digits + key).replace(/^0+/, '')
  return next.length <= (isMoney(op) ? 9 : 4) ? { ...c, steps: [...c.steps.slice(0, -1), [op, next]] } : c
}

/** A second operation in a row replaces the first one. */
export function calcOperation(c: Calc, op: AmountOp): Calc {
  if (!c.first && !c.steps.length) return c
  return calcPending(c) ? { ...c, steps: [...c.steps.slice(0, -1), [op, '']] } : { ...c, steps: [...c.steps, [op, '']] }
}

export function calcBackspace(c: Calc): Calc {
  const last = c.steps[c.steps.length - 1]
  if (!last) return { ...c, first: c.first.slice(0, -1) }
  if (!last[1]) return { ...c, steps: c.steps.slice(0, -1) }
  return { ...c, steps: [...c.steps.slice(0, -1), [last[0], last[1].slice(0, -1)]] }
}

// --- text fields ("11,00×2") -------------------------------------------------------------------

/** The result in cents of an amount typed with operations, or null when it isn't one. */
export function evaluateAmount(text: string): number | null {
  const clean = text.replace(/\s/g, '')
  if (!clean) return null
  const parts: string[] = []
  const ops: AmountOp[] = []
  let current = ''
  ;[...clean].forEach((ch, i) => {
    const op = i > 0 ? opOf(ch) : null
    if (op) {
      parts.push(current)
      ops.push(op)
      current = ''
    } else current += ch
  })
  parts.push(current)
  let cents = Money.parse(parts[0])
  if (cents == null) return null
  for (let i = 0; i < ops.length; i++) {
    if (!parts[i + 1]) continue
    const value = Number(parts[i + 1].replace(',', '.'))
    if (!Number.isFinite(value)) return null
    cents = apply(ops[i], cents, value)
  }
  return cents
}

/** Whether [text] has at least one operation, so the result is worth showing apart. */
export const hasOperation = (text: string) => [...text.slice(1)].some((ch) => opOf(ch) != null)
