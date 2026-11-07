import { AsyncLocalStorage } from 'node:async_hooks'
import { DatabaseSync } from 'node:sqlite'
import { mkdirSync, unlinkSync } from 'node:fs'
import { basename, join } from 'node:path'
import { today as todayEpoch } from '../../shared/date.ts'
import {
  afterPayment,
  defaultExpenseCategories,
  defaultIncomeCategories,
  type Account,
  type AppState,
  type Category,
  type CustomPayment,
  type Member,
  type Recurring,
  type Transaction,
  type Transfer,
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
  recurringId INTEGER, createdAt INTEGER NOT NULL, memberId INTEGER, paymentId INTEGER, settle INTEGER NOT NULL DEFAULT 0);
CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions (date);
CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions (categoryId);
CREATE INDEX IF NOT EXISTS index_transactions_recurringId ON transactions (recurringId);
CREATE INDEX IF NOT EXISTS index_transactions_memberId ON transactions (memberId);
CREATE TABLE IF NOT EXISTS recurring (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, amountCents INTEGER NOT NULL,
  categoryId INTEGER NOT NULL, subcategoryId INTEGER, payment TEXT NOT NULL, frequency TEXT NOT NULL,
  startDate INTEGER NOT NULL, nextDate INTEGER NOT NULL, brandKey TEXT, imagePath TEXT, icon TEXT NOT NULL,
  color INTEGER NOT NULL, autoRegister INTEGER NOT NULL, remindDaysBefore INTEGER NOT NULL, notifiedFor INTEGER,
  active INTEGER NOT NULL, note TEXT NOT NULL, paymentId INTEGER, installments INTEGER,
  installmentsPaid INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS members (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, color INTEGER NOT NULL, sortOrder INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS payment_methods (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL,
  sortOrder INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS accounts (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL,
  methods TEXT NOT NULL, balanceCents INTEGER NOT NULL, balanceAt INTEGER NOT NULL, sortOrder INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS transfers (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fromId INTEGER NOT NULL, toId INTEGER NOT NULL,
  amountCents INTEGER NOT NULL, date INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL);
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
  migrate(db)
  const data = { id: userId, db, imagesDir }
  opened.set(userId, data)
  current.run(data, seedIfEmpty)
  return data
}

/** Columns added after the first release, for databases created before them. */
function migrate(db: DatabaseSync) {
  for (const table of ['transactions', 'recurring']) {
    const columns = (db.prepare(`PRAGMA table_info(${table})`).all() as { name: string }[]).map((c) => c.name)
    if (!columns.includes('paymentId')) db.exec(`ALTER TABLE ${table} ADD COLUMN paymentId INTEGER`)
    if (table === 'transactions' && !columns.includes('settle')) db.exec('ALTER TABLE transactions ADD COLUMN settle INTEGER NOT NULL DEFAULT 0')
    if (table === 'recurring' && !columns.includes('installments')) {
      db.exec('ALTER TABLE recurring ADD COLUMN installments INTEGER')
      db.exec('ALTER TABLE recurring ADD COLUMN installmentsPaid INTEGER NOT NULL DEFAULT 0')
    }
  }
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
  createdAt: num(r.createdAt), memberId: optNum(r.memberId), paymentId: optNum(r.paymentId), settle: num(r.settle ?? 0),
})

const toRecurring = (r: Row): Recurring => ({
  id: num(r.id), kind: r.kind as Recurring['kind'], name: String(r.name), amountCents: num(r.amountCents),
  categoryId: num(r.categoryId), subcategoryId: optNum(r.subcategoryId), payment: r.payment as Recurring['payment'],
  frequency: r.frequency as Recurring['frequency'], startDate: num(r.startDate), nextDate: num(r.nextDate),
  brandKey: (r.brandKey as string | null) ?? null, imagePath: (r.imagePath as string | null) ?? null, icon: String(r.icon),
  color: num(r.color), autoRegister: num(r.autoRegister) !== 0, remindDaysBefore: num(r.remindDaysBefore),
  notifiedFor: optNum(r.notifiedFor), active: num(r.active) !== 0, note: String(r.note), paymentId: optNum(r.paymentId),
  installments: optNum(r.installments), installmentsPaid: num(r.installmentsPaid),
})

const toMember = (r: Row): Member => ({ id: num(r.id), name: String(r.name), color: num(r.color), sortOrder: num(r.sortOrder) })

const toPayment = (r: Row): CustomPayment => ({
  id: num(r.id), name: String(r.name), icon: String(r.icon), color: num(r.color), sortOrder: num(r.sortOrder),
})

export function paymentMethods(): CustomPayment[] {
  return (db.prepare('SELECT * FROM payment_methods ORDER BY sortOrder, id').all() as Row[]).map(toPayment)
}

const toAccount = (r: Row): Account => ({
  id: num(r.id), name: String(r.name), icon: String(r.icon), color: num(r.color), methods: String(r.methods),
  balanceCents: num(r.balanceCents), balanceAt: num(r.balanceAt), sortOrder: num(r.sortOrder),
})

const toTransfer = (r: Row): Transfer => ({
  id: num(r.id), fromId: num(r.fromId), toId: num(r.toId), amountCents: num(r.amountCents), date: num(r.date),
  note: String(r.note), createdAt: num(r.createdAt),
})

export function moneyAccounts(): Account[] {
  return (db.prepare('SELECT * FROM accounts ORDER BY sortOrder, id').all() as Row[]).map(toAccount)
}

export function transfers(): Transfer[] {
  return (db.prepare('SELECT * FROM transfers ORDER BY date DESC, createdAt DESC').all() as Row[]).map(toTransfer)
}

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
  return {
    categories: categories(), transactions: transactions(), recurring: recurring(), members: members(),
    paymentMethods: paymentMethods(), accounts: moneyAccounts(), transfers: transfers(),
  }
}

// --- writes ----------------------------------------------------------------------------------

const txColumns = ['type', 'amountCents', 'date', 'categoryId', 'subcategoryId', 'payment', 'note', 'receiptPath', 'recurringId', 'createdAt', 'memberId', 'paymentId', 'settle'] as const
const recurringColumns = ['kind', 'name', 'amountCents', 'categoryId', 'subcategoryId', 'payment', 'frequency', 'startDate', 'nextDate', 'brandKey', 'imagePath', 'icon', 'color', 'autoRegister', 'remindDaysBefore', 'notifiedFor', 'active', 'note', 'paymentId', 'installments', 'installmentsPaid'] as const
const categoryColumns = ['name', 'icon', 'color', 'type', 'parentId', 'budgetCents', 'sortOrder'] as const
const memberColumns = ['name', 'color', 'sortOrder'] as const
const paymentColumns = ['name', 'icon', 'color', 'sortOrder'] as const
const accountColumns = ['name', 'icon', 'color', 'methods', 'balanceCents', 'balanceAt', 'sortOrder'] as const
const transferColumns = ['fromId', 'toId', 'amountCents', 'date', 'note', 'createdAt'] as const

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
  // Apps from before settling up don't send it: the movement keeps what it had.
  const settle = tx.settle ?? (tx.id ? transactionById(tx.id)?.settle : undefined) ?? 0
  return transaction(() => transactionById(upsert('transactions', txColumns, { ...tx, settle }))!)
}

/** People settled up: the expenses [ids] don't count again. */
export function settleTransactions(ids: number[]) {
  transaction(() => {
    const update = db.prepare('UPDATE transactions SET settle = 2 WHERE settle = 0 AND id = ?')
    for (const id of ids) update.run(id)
  })
}

export function deleteTransaction(id: number) {
  transaction(() => db.prepare('DELETE FROM transactions WHERE id = ?').run(id))
}

export function saveRecurring(item: Recurring): Recurring {
  // Apps from before financings don't send them: the item keeps what it had.
  const known = item.id ? recurringById(item.id) : null
  const installments = item.installments === undefined ? known?.installments ?? null : item.installments
  const installmentsPaid = item.installmentsPaid ?? known?.installmentsPaid ?? 0
  return transaction(() => recurringById(upsert('recurring', recurringColumns, { ...item, installments, installmentsPaid }))!)
}

export function deleteRecurring(id: number) {
  const item = recurringById(id)
  transaction(() => db.prepare('DELETE FROM recurring WHERE id = ?').run(id))
  if (item?.imagePath) deleteImage(item.imagePath)
}

/** Returns the id of the person. */
export function saveMember(member: Member): number {
  return transaction(() => {
    const sortOrder = member.id === 0 ? members().length : member.sortOrder
    return upsert('members', memberColumns, { ...member, sortOrder })
  })
}

/** Movements of the removed person stay, just without an owner. */
export function deleteMember(id: number) {
  transaction(() => {
    db.prepare('UPDATE transactions SET memberId = NULL WHERE memberId = ?').run(id)
    db.prepare('DELETE FROM members WHERE id = ?').run(id)
  })
}

/** Returns the id of the payment method. */
export function savePaymentMethod(method: CustomPayment): number {
  return transaction(() => {
    const sortOrder = method.id === 0 ? paymentMethods().length : method.sortOrder
    return upsert('payment_methods', paymentColumns, { ...method, sortOrder })
  })
}

/** Movements and fixed expenses paid that way stay, as "Altro". */
export function deletePaymentMethod(id: number) {
  transaction(() => {
    db.prepare('UPDATE transactions SET paymentId = NULL WHERE paymentId = ?').run(id)
    db.prepare('UPDATE recurring SET paymentId = NULL WHERE paymentId = ?').run(id)
    db.prepare('DELETE FROM payment_methods WHERE id = ?').run(id)
  })
}

/** Returns the id of the account. */
export function saveAccount(account: Account): number {
  return transaction(() => {
    const sortOrder = account.id === 0 ? moneyAccounts().length : account.sortOrder
    return upsert('accounts', accountColumns, { ...account, sortOrder })
  })
}

/** Its transfers go with it; movements stay, they just no longer move any balance. */
export function deleteAccount(id: number) {
  transaction(() => {
    db.prepare('DELETE FROM transfers WHERE fromId = ? OR toId = ?').run(id, id)
    db.prepare('DELETE FROM accounts WHERE id = ?').run(id)
  })
}

/** Returns the id of the transfer. */
export function saveTransfer(transfer: Transfer): number {
  return transaction(() => upsert('transfers', transferColumns, transfer))
}

export function deleteTransfer(id: number) {
  transaction(() => db.prepare('DELETE FROM transfers WHERE id = ?').run(id))
}

/**
 * Saves a top level category with its subcategories (missing ones are removed).
 * Returns the ids of the category and of its subcategories, in the order given.
 */
export function saveCategory(category: Category, subs: Category[]): { id: number; subIds: number[] } {
  return transaction(() => {
    const id = upsert('categories', categoryColumns, category)
    const existing = categories().filter((c) => c.parentId === id)
    const keep = new Set(subs.filter((s) => s.id !== 0).map((s) => s.id))
    for (const old of existing.filter((c) => !keep.has(c.id))) {
      db.prepare('UPDATE transactions SET subcategoryId = NULL WHERE subcategoryId = ?').run(old.id)
      db.prepare('DELETE FROM categories WHERE id = ?').run(old.id)
    }
    const subIds = subs.map((sub, index) =>
      upsert('categories', categoryColumns, { ...sub, parentId: id, type: category.type, color: category.color, icon: category.icon, sortOrder: index }))
    return { id, subIds }
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
      let paid = original
      let guard = 0
      // Manual ones stay on their date until the payment is added by hand.
      while (original.autoRegister && paid.active && next <= today && guard++ < 400) {
        upsert('transactions', txColumns, {
          id: 0, type: 'EXPENSE', amountCents: original.amountCents, date: next, categoryId: original.categoryId,
          subcategoryId: original.subcategoryId, payment: original.payment, note: original.name, receiptPath: null,
          recurringId: original.id, createdAt: Date.now(), memberId: null, paymentId: original.paymentId, settle: 0,
        })
        paid = afterPayment(paid)
        next = nextAfter(original.startDate, original.frequency, next)
      }
      let item = { ...paid, nextDate: next }
      if (item.remindDaysBefore >= 0 && next - today <= item.remindDaysBefore && item.notifiedFor !== next) {
        reminders.push(item)
        item = { ...item, notifiedFor: next }
      }
      if (item.nextDate !== original.nextDate || item.notifiedFor !== original.notifiedFor || item.active !== original.active) {
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
    for (const table of ['transactions', 'recurring', 'categories', 'members', 'payment_methods']) db.exec(`DELETE FROM ${table}`)
    snapshot.categories.forEach((c) => upsert('categories', categoryColumns, c))
    snapshot.members.forEach((m) => upsert('members', memberColumns, m))
    snapshot.paymentMethods.forEach((p) => upsert('payment_methods', paymentColumns, p))
    snapshot.transactions.forEach((t) => upsert('transactions', txColumns, t))
    snapshot.recurring.forEach((r) => upsert('recurring', recurringColumns, r))
    // Backups from before accounts leave the ones on the server as they are.
    if (snapshot.accounts) {
      db.exec('DELETE FROM accounts')
      db.exec('DELETE FROM transfers')
      snapshot.accounts.forEach((a) => upsert('accounts', accountColumns, a))
      for (const t of snapshot.transfers ?? []) upsert('transfers', transferColumns, t)
    }
    db.exec('DELETE FROM ai_cache')
  })
}
