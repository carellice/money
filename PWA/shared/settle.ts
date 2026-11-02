import { Settle, type Member, type Transaction } from './model.ts'

// Settling up between the people of the household, as in the Android app (SettleUpBuilder).

/** One payment that evens out the month: [from] gives [cents] to [to]. */
export type SettleTransfer = { from: Member; to: Member; cents: number }

export type SettleUp = {
  /** What each person paid among the expenses still to settle, in the order of the people. */
  paid: { member: Member; cents: number }[]
  total: number
  transfers: SettleTransfer[]
  /** Expenses that count now: marked as settled once the money has been given. */
  ids: number[]
  /** Some expenses of the month were settled up already. */
  settledBefore: boolean
}

/**
 * Splits the month's expenses equally among everyone in the household: whoever paid less than
 * their share gives the difference to whoever paid more. Expenses left out by the payer, or
 * already settled, don't count.
 */
export function buildSettleUp(transactions: Transaction[], members: Member[]): SettleUp | null {
  if (members.length < 2) return null
  const known = new Set(members.map((m) => m.id))
  const expenses = transactions.filter((t) => t.type === 'EXPENSE' && t.memberId != null && known.has(t.memberId))
  const open = expenses.filter((t) => (t.settle ?? Settle.OPEN) === Settle.OPEN)
  const paid = members.map((member) => ({ member, cents: open.filter((t) => t.memberId === member.id).reduce((a, t) => a + t.amountCents, 0) }))
  const total = paid.reduce((a, p) => a + p.cents, 0)
  // Balances times the number of people, so equal shares stay whole cents until the end.
  const n = members.length
  const creditors = paid.filter((p) => p.cents * n - total > 0).map((p) => ({ member: p.member, left: p.cents * n - total }))
  const debtors = paid.filter((p) => p.cents * n - total < 0).map((p) => ({ member: p.member, left: total - p.cents * n }))
  const transfers: SettleTransfer[] = []
  let c = 0
  let d = 0
  while (c < creditors.length && d < debtors.length) {
    const amount = Math.min(creditors[c].left, debtors[d].left)
    const cents = Math.round(amount / n)
    if (cents > 0) transfers.push({ from: debtors[d].member, to: creditors[c].member, cents })
    creditors[c].left -= amount
    debtors[d].left -= amount
    if (creditors[c].left === 0) c++
    if (debtors[d].left === 0) d++
  }
  return {
    paid,
    total,
    transfers,
    ids: open.map((t) => t.id),
    settledBefore: expenses.some((t) => t.settle === Settle.SETTLED),
  }
}
