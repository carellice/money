import type { AppState, Category, Member, Recurring, Transaction } from '../../shared/model.ts'
import { replaceAll, state } from './db.ts'

// JSON backup of the whole database, same format as the Android app, so files can move between the two.
// Images (receipts, custom logos) are not included.

const VERSION = 1

type Json = Record<string, unknown>

const optNum = (o: Json, key: string) => (o[key] == null ? null : Number(o[key]))
const str = (o: Json, key: string, fallback = '') => (o[key] == null ? fallback : String(o[key]))

export function exportBackup(): string {
  const { categories, transactions, recurring, members } = state()
  return JSON.stringify({
    app: 'Money',
    version: VERSION,
    exportedAt: Date.now(),
    categories: categories.map((c) => ({
      id: c.id, name: c.name, icon: c.icon, color: c.color, type: c.type, parentId: c.parentId,
      budgetCents: c.budgetCents, sortOrder: c.sortOrder,
    })),
    transactions: transactions.map((t) => ({
      id: t.id, type: t.type, amountCents: t.amountCents, date: t.date, categoryId: t.categoryId,
      subcategoryId: t.subcategoryId, payment: t.payment, note: t.note, recurringId: t.recurringId,
      createdAt: t.createdAt, memberId: t.memberId,
    })),
    recurring: recurring.map((r) => ({
      id: r.id, kind: r.kind, name: r.name, amountCents: r.amountCents, categoryId: r.categoryId,
      subcategoryId: r.subcategoryId, payment: r.payment, frequency: r.frequency, startDate: r.startDate,
      nextDate: r.nextDate, brandKey: r.brandKey, icon: r.icon, color: r.color, autoRegister: r.autoRegister,
      remindDaysBefore: r.remindDaysBefore, active: r.active, note: r.note,
    })),
    members: members.map((m) => ({ id: m.id, name: m.name, color: m.color, sortOrder: m.sortOrder })),
  }, null, 2)
}

/** Replaces all data with the backup content. Returns the number of movements imported. */
export function importBackup(text: string): number {
  const root = JSON.parse(text) as Json
  if (root.app !== 'Money') throw new Error('Il file non è un backup di Money')
  const list = (key: string) => (Array.isArray(root[key]) ? (root[key] as Json[]) : [])

  const categories: Category[] = list('categories').map((o) => ({
    id: Number(o.id), name: str(o, 'name'), icon: str(o, 'icon'), color: Number(o.color),
    type: str(o, 'type') as Category['type'], parentId: optNum(o, 'parentId'), budgetCents: optNum(o, 'budgetCents'),
    sortOrder: Number(o.sortOrder ?? 0),
  }))
  // Backups made before people were introduced have no members.
  const members: Member[] = list('members').map((o) => ({
    id: Number(o.id), name: str(o, 'name'), color: Number(o.color), sortOrder: Number(o.sortOrder ?? 0),
  }))
  const known = new Set(members.map((m) => m.id))
  const transactions: Transaction[] = list('transactions').map((o) => {
    const memberId = optNum(o, 'memberId')
    return {
      id: Number(o.id), type: str(o, 'type') as Transaction['type'], amountCents: Number(o.amountCents),
      date: Number(o.date), categoryId: Number(o.categoryId), subcategoryId: optNum(o, 'subcategoryId'),
      payment: str(o, 'payment') as Transaction['payment'], note: str(o, 'note'), receiptPath: null,
      recurringId: optNum(o, 'recurringId'), createdAt: Number(o.createdAt ?? 0),
      memberId: memberId != null && known.has(memberId) ? memberId : null,
    }
  })
  const recurring: Recurring[] = list('recurring').map((o) => ({
    id: Number(o.id), kind: str(o, 'kind') as Recurring['kind'], name: str(o, 'name'), amountCents: Number(o.amountCents),
    categoryId: Number(o.categoryId), subcategoryId: optNum(o, 'subcategoryId'), payment: str(o, 'payment') as Recurring['payment'],
    frequency: str(o, 'frequency') as Recurring['frequency'], startDate: Number(o.startDate), nextDate: Number(o.nextDate),
    brandKey: o.brandKey == null ? null : String(o.brandKey), imagePath: null, icon: str(o, 'icon', 'repeat'),
    color: Number(o.color), autoRegister: o.autoRegister !== false, remindDaysBefore: Number(o.remindDaysBefore ?? 2),
    notifiedFor: null, active: o.active !== false, note: str(o, 'note'),
  }))
  if (!root.categories || !root.transactions || !root.recurring) throw new Error('Backup incompleto')
  const snapshot: AppState = { categories, transactions, recurring, members }
  replaceAll(snapshot)
  return transactions.length
}
