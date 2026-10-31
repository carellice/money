import { AsyncLocalStorage } from 'node:async_hooks'
import { DatabaseSync } from 'node:sqlite'
import { mkdirSync, unlinkSync } from 'node:fs'
import { basename, join } from 'node:path'
import { today as todayEpoch } from '../../shared/date.ts'
import {
  defaultExpenseCategories,
  defaultIncomeCategories,
  type AppState,
  type Category,
  type Member,
  type Recurring,
  type Transaction,
  type TxType,
} from '../../shared/model.ts'
import { nextAfter } from '../../shared/recurrence.ts'

export const dataDir = process.env.DATA_DIR ?? join(process.cwd(), 'data')
mkdirSync(dataDir, { recursive: true })

// Same tables and columns as the Android Room database.
const schema = `
CREATE TABLE IF NOT EXISTS categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL,
  type TEXT NOT NULL, parentId INTEGER, budgetCents INTEGER, sortOrder INTEGER NOT NULL DEFAULT 0);
CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories (parentId);
CREATE TABLE IF NOT EXISTS transactions (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, amountCents INTEGER NOT NULL, date INTEGER NOT NULL,
  categoryId INTEGER NOT NULL, subcategoryId INTEGER, payment TEXT NOT NULL, note TEXT NOT NULL, receiptPath TEXT,
  recurringId INTEGER, createdAt INTEGER NOT NULL, memberId INTEGER);
CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions (date);
CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions (categoryId);
CREATE INDEX IF NOT EXISTS index_transactions_recurringId ON transactions (recurringId);
CREATE INDEX IF NOT EXISTS index_transactions_memberId ON transactions (memberId);
CREATE TABLE IF NOT EXISTS recurring (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, amountCents INTEGER NOT NULL,
  categoryId INTEGER NOT NULL, subcategoryId INTEGER, payment TEXT NOT NULL, frequency TEXT NOT NULL,
  startDate INTEGER NOT NULL, nextDate INTEGER NOT NULL, brandKey TEXT, imagePath TEXT, icon TEXT NOT NULL,
  color INTEGER NOT NULL, autoRegister INTEGER NOT NULL, remindDaysBefore INTEGER NOT NULL, notifiedFor INTEGER,
  active INTEGER NOT NULL, note TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS members (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, sortOrder INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS push_subs (endpoint TEXT PRIMARY KEY NOT NULL, json TEXT NOT NULL, reminders INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS ai_cache (month TEXT PRIMARY KEY NOT NULL, signature TEXT NOT NULL, text TEXT NOT NULL);
`

// --- accounts: every account has its own database, picked per request -------------------------

type UserData = { id: number; db: DatabaseSync; imagesDir: string }

const opened = new Map<number, UserData>()
const current = new AsyncLocalStorage<UserData>()

export const userDir = (userId: number) => join(dataDir, 'users', String(userId))

function open(userId: number): UserData {
  const known = opened.get(userId)
  if (known) return known
  const dir = userDir(userId)
  const imagesDir = join(dir, 'images')
  mkdirSync(imagesDir, { recursive: true })
  const db = new DatabaseSync(join(dir, 'money.db'))
  db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = OFF;')
  db.exec(schema)
  const data = { id: userId, db, imagesDir }
  opened.set(userId, data)
  current.run(data, seedIfEmpty)
  return data
}

/** Runs [work] on the data of [userId]: every function of this module then reads and writes there. */
export function withUser<T>(userId: number, work: () => T): T {
  return current.run(open(userId), work)
}

export function closeUser(userId: number) {
  opened.get(userId)?.db.close()
  opened.delete(userId)
}

function user(): UserData {
  const data = current.getStore()
  if (!data) throw new Error('Nessun account attivo')
  return data
}

/** The database of the current account. */
export const db = {
  prepare: (sql: string) => user().db.prepare(sql),
  exec: (sql: string) => user().db.exec(sql),
}

export const imagesDir = () => user().imagesDir

// --- meta ------------------------------------------------------------------------------------

export function getMeta(key: string): string | null {
  const row = db.prepare('SELECT value FROM meta WHERE key = ?').get(key) as { value: string } | undefined
  return row?.value ?? null
}

export function setMeta(key: string, value: string | null) {
  if (value == null) db.prepare('DELETE FROM meta WHERE key = ?').run(key)
  else db.prepare('INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value').run(key, value)
}

/** Bumped on every change, so clients can tell when to reload. */
export function revision(): number {
  return Number(getMeta('revision') ?? '0')
}

function touch() {
  setMeta('revision', String(revision() + 1))
}

export function transaction<T>(work: () => T): T {
  db.exec('BEGIN')
  try {
    const result = work()
    db.exec('COMMIT')
    touch()
    return result
  } catch (e) {
    db.exec('ROLLBACK')
    throw e
  }
}

// --- rows ------------------------------------------------------------------------------------

type Row = Record<string, unknown>
const num = (v: unknown) => Number(v)
const optNum = (v: unknown) => (v == null ? null : Number(v))

const toCategory = (r: Row): Category => ({
  id: num(r.id), name: String(r.name), icon: String(r.icon), color: num(r.color), type: r.type as TxType,
  parentId: optNum(r.parentId), budgetCents: optNum(r.budgetCents), sortOrder: num(r.sortOrder),
})

const toTransaction = (r: Row): Transaction => ({
  id: num(r.id), type: r.type as TxType, amountCents: num(r.amountCents), date: num(r.date),
  categoryId: num(r.categoryId), subcategoryId: optNum(r.subcategoryId), payment: r.payment as Transaction['payment'],
  note: String(r.note), receiptPath: (r.receiptPath as string | null) ?? null, recurringId: optNum(r.recurringId),
  createdAt: num(r.createdAt), memberId: optNum(r.memberId),
})

const toRecurring = (r: Row): Recurring => ({
  id: num(r.id), kind: r.kind as Recurring['kind'], name: String(r.name), amountCents: num(r.amountCents),
  categoryId: num(r.categoryId), subcategoryId: optNum(r.subcategoryId), payment: r.payment as Recurring['payment'],
  frequency: r.frequency as Recurring['frequency'], startDate: num(r.startDate), nextDate: num(r.nextDate),
  brandKey: (r.brandKey as string | null) ?? null, imagePath: (r.imagePath as string | null) ?? null, icon: String(r.icon),
  color: num(r.color), autoRegister: num(r.autoRegister) !== 0, remindDaysBefore: num(r.remindDaysBefore),
  notifiedFor: optNum(r.notifiedFor), active: num(r.active) !== 0, note: String(r.note),
})

const toMember = (r: Row): Member => ({ id: num(r.id), name: String(r.name), color: num(r.color), sortOrder: num(r.sortOrder) })

export function categories(): Category[] {
  return (db.prepare('SELECT * FROM categories ORDER BY sortOrder, name').all() as Row[]).map(toCategory)
}

export function transactions(): Transaction[] {
  return (db.prepare('SELECT * FROM transactions ORDER BY date DESC, createdAt DESC').all() as Row[]).map(toTransaction)
}

export function transactionsBetween(from: number, to: number): Transaction[] {
  return (db.prepare('SELECT * FROM transactions WHERE date BETWEEN ? AND ? ORDER BY date, createdAt').all(from, to) as Row[]).map(toTransaction)
}

export function transactionById(id: number): Transaction | null {
  const row = db.prepare('SELECT * FROM transactions WHERE id = ?').get(id) as Row | undefined
  return row ? toTransaction(row) : null
}

export function recurring(): Recurring[] {
  return (db.prepare('SELECT * FROM recurring ORDER BY active DESC, nextDate').all() as Row[]).map(toRecurring)
}

export function recurringById(id: number): Recurring | null {
  const row = db.prepare('SELECT * FROM recurring WHERE id = ?').get(id) as Row | undefined
  return row ? toRecurring(row) : null
}

export function members(): Member[] {
  return (db.prepare('SELECT * FROM members ORDER BY sortOrder, id').all() as Row[]).map(toMember)
}

export function state(): AppState {
  return { categories: categories(), transactions: transactions(), recurring: recurring(), members: members() }
}

// --- writes ----------------------------------------------------------------------------------

const txColumns = ['type', 'amountCents', 'date', 'categoryId', 'subcategoryId', 'payment', 'note', 'receiptPath', 'recurringId', 'createdAt', 'memberId'] as const
const recurringColumns = ['kind', 'name', 'amountCents', 'categoryId', 'subcategoryId', 'payment', 'frequency', 'startDate', 'nextDate', 'brandKey', 'imagePath', 'icon', 'color', 'autoRegister', 'remindDaysBefore', 'notifiedFor', 'active', 'note'] as const
const categoryColumns = ['name', 'icon', 'color', 'type', 'parentId', 'budgetCents', 'sortOrder'] as const
const memberColumns = ['name', 'color', 'sortOrder'] as const

type Sql = string | number | null

function sqlValue(v: unknown): Sql {
  if (typeof v === 'boolean') return v ? 1 : 0
  if (v === undefined) return null
  return v as Sql
}

/** Room's @Upsert: id 0 inserts a new row, any other id replaces (or recreates) that row. */
function upsert(table: string, columns: readonly string[], entity: Record<string, unknown>): number {
  const values = columns.map((c) => sqlValue(entity[c]))
  const id = Number(entity.id ?? 0)
  if (id === 0) {
    const result = db.prepare(`INSERT INTO ${table} (${columns.join(', ')}) VALUES (${columns.map(() => '?').join(', ')})`).run(...values)
    return Number(result.lastInsertRowid)
  }
  db.prepare(`INSERT OR REPLACE INTO ${table} (id, ${columns.join(', ')}) VALUES (?, ${columns.map(() => '?').join(', ')})`).run(id, ...values)
  return id
}

export function saveTransaction(tx: Transaction): Transaction {
  return transaction(() => transactionById(upsert('transactions', txColumns, tx))!)
}

export function deleteTransaction(id: number) {
  transaction(() => db.prepare('DELETE FROM transactions WHERE id = ?').run(id))
}

export function saveRecurring(item: Recurring): Recurring {
  return transaction(() => recurringById(upsert('recurring', recurringColumns, item))!)
}

export function deleteRecurring(id: number) {
  const item = recurringById(id)
  transaction(() => db.prepare('DELETE FROM recurring WHERE id = ?').run(id))
  if (item?.imagePath) deleteImage(item.imagePath)
}

export function saveMember(member: Member) {
  transaction(() => {
    const sortOrder = member.id === 0 ? members().length : member.sortOrder
    upsert('members', memberColumns, { ...member, sortOrder })
  })
}

/** Movements of the removed person stay, just without an owner. */
export function deleteMember(id: number) {
  transaction(() => {
    db.prepare('UPDATE transactions SET memberId = NULL WHERE memberId = ?').run(id)
    db.prepare('DELETE FROM members WHERE id = ?').run(id)
  })
}

/** Saves a top level category with its subcategories (missing ones are removed). */
export function saveCategory(category: Category, subs: Category[]) {
  transaction(() => {
    const id = upsert('categories', categoryColumns, category)
    const existing = categories().filter((c) => c.parentId === id)
    const keep = new Set(subs.filter((s) => s.id !== 0).map((s) => s.id))
    for (const old of existing.filter((c) => !keep.has(c.id))) {
      db.prepare('UPDATE transactions SET subcategoryId = NULL WHERE subcategoryId = ?').run(old.id)
      db.prepare('DELETE FROM categories WHERE id = ?').run(old.id)
    }
    subs.forEach((sub, index) => {
      upsert('categories', categoryColumns, { ...sub, parentId: id, type: category.type, color: category.color, icon: category.icon, sortOrder: index })
    })
  })
}

/** Returns false when the category is still used by movements or fixed expenses. */
export function deleteCategory(id: number): boolean {
  const used = (sql: string) => Number((db.prepare(sql).get(id, id) as { n: number }).n) > 0
  if (used('SELECT COUNT(*) AS n FROM transactions WHERE categoryId = ? OR subcategoryId = ?') ||
    used('SELECT COUNT(*) AS n FROM recurring WHERE categoryId = ? OR subcategoryId = ?')) return false
  transaction(() => {
    for (const sub of categories().filter((c) => c.parentId === id)) {
      db.prepare('UPDATE transactions SET subcategoryId = NULL WHERE subcategoryId = ?').run(sub.id)
    }
    db.prepare('DELETE FROM categories WHERE parentId = ?').run(id)
    db.prepare('DELETE FROM categories WHERE id = ?').run(id)
  })
  return true
}

export function seedIfEmpty() {
  const count = Number((db.prepare('SELECT COUNT(*) AS n FROM categories').get() as { n: number }).n)
  if (count > 0) return
  transaction(() => {
    for (const [type, list] of [['EXPENSE', defaultExpenseCategories], ['INCOME', defaultIncomeCategories]] as const) {
      list.forEach((def, index) => {
        const id = upsert('categories', categoryColumns, { id: 0, name: def.name, icon: def.icon, color: def.color, type, parentId: null, budgetCents: null, sortOrder: index })
        def.subs?.forEach((sub, i) => {
          upsert('categories', categoryColumns, { id: 0, name: sub, icon: def.icon, color: def.color, type, parentId: id, budgetCents: null, sortOrder: i })
        })
      })
    }
  })
}

/**
 * Registers due fixed expenses up to [today] and advances their next date.
 * Returns the items whose renewal reminder should be sent now.
 */
export function processRecurring(today = todayEpoch()): Recurring[] {
  const reminders: Recurring[] = []
  let changed = false
  db.exec('BEGIN')
  try {
    for (const original of recurring().filter((r) => r.active)) {
      let next = original.nextDate
      let guard = 0
      while (next <= today && guard++ < 400) {
        if (original.autoRegister) {
          upsert('transactions', txColumns, {
            id: 0, type: 'EXPENSE', amountCents: original.amountCents, date: next, categoryId: original.categoryId,
            subcategoryId: original.subcategoryId, payment: original.payment, note: original.name, receiptPath: null,
            recurringId: original.id, createdAt: Date.now(), memberId: null,
          })
        }
        next = nextAfter(original.startDate, original.frequency, next)
      }
      let item = { ...original, nextDate: next }
      if (item.remindDaysBefore >= 0 && next - today <= item.remindDaysBefore && item.notifiedFor !== next) {
        reminders.push(item)
        item = { ...item, notifiedFor: next }
      }
      if (item.nextDate !== original.nextDate || item.notifiedFor !== original.notifiedFor) {
        upsert('recurring', recurringColumns, item)
        changed = true
      }
    }
    db.exec('COMMIT')
  } catch (e) {
    db.exec('ROLLBACK')
    throw e
  }
  if (changed) touch()
  return reminders
}

// --- images ----------------------------------------------------------------------------------

/** Images are referenced as "/images/<file>"; only files inside the images folder can be removed. */
export function deleteImage(path: string) {
  const name = basename(path)
  if (!path.startsWith('/images/') || !/^[\w.-]+$/.test(name)) return
  try { unlinkSync(join(imagesDir(), name)) } catch { /* already gone */ }
}

// --- backup (same JSON as the Android app) ---------------------------------------------------

export function replaceAll(snapshot: AppState) {
  transaction(() => {
    for (const table of ['transactions', 'recurring', 'categories', 'members']) db.exec(`DELETE FROM ${table}`)
    snapshot.categories.forEach((c) => upsert('categories', categoryColumns, c))
    snapshot.members.forEach((m) => upsert('members', memberColumns, m))
    snapshot.transactions.forEach((t) => upsert('transactions', txColumns, t))
    snapshot.recurring.forEach((r) => upsert('recurring', recurringColumns, r))
    db.exec('DELETE FROM ai_cache')
  })
}
