// Data model, same tables and fields as the Android app (Room entities), so backups move freely between them.

export type TxType = 'EXPENSE' | 'INCOME'
export type RecurringKind = 'SUBSCRIPTION' | 'FIXED' | 'FINANCING'

export type PaymentMethod = 'CARD' | 'DEBIT' | 'CASH' | 'TRANSFER' | 'PAYPAL' | 'APP' | 'OTHER'

export const paymentMethods: { id: PaymentMethod; label: string; icon: string }[] = [
  { id: 'CARD', label: 'Carta', icon: 'card' },
  { id: 'DEBIT', label: 'Bancomat', icon: 'atm' },
  { id: 'CASH', label: 'Contanti', icon: 'cash' },
  { id: 'TRANSFER', label: 'Bonifico', icon: 'bank' },
  { id: 'PAYPAL', label: 'PayPal', icon: 'wallet' },
  { id: 'APP', label: 'App / Satispay', icon: 'phone' },
  { id: 'OTHER', label: 'Altro', icon: 'more' },
]

export function payment(id: PaymentMethod) {
  return paymentMethods.find((p) => p.id === id) ?? paymentMethods[6]
}

/** Payment method added by the user (a specific card, a voucher...), next to the built-in ones. */
export type CustomPayment = {
  id: number
  name: string
  icon: string
  /** ARGB color. */
  color: number
  sortOrder: number
}

/**
 * What something was paid with: a built-in method, or the custom one [customId]
 * (stored as OTHER, so versions without custom methods still read it).
 */
export type PaymentChoice = { method: PaymentMethod; customId: number | null }

export const paymentChoice = (row: { payment: PaymentMethod; paymentId: number | null }): PaymentChoice =>
  ({ method: row.payment, customId: row.paymentId })

export const sameChoice = (a: PaymentChoice, b: PaymentChoice) => a.method === b.method && a.customId === b.customId

/** Name and icon of a payment method, custom ones included. */
export function paymentLabel(choice: PaymentChoice, custom: CustomPayment[]): { label: string; icon: string } {
  const own = choice.customId != null ? custom.find((c) => c.id === choice.customId) : undefined
  return own ? { label: own.name, icon: own.icon } : payment(choice.method)
}

export type Frequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'BIMONTHLY' | 'QUARTERLY' | 'SEMIANNUAL' | 'YEARLY' | 'BIENNIAL'

export const frequencies: { id: Frequency; label: string; short: string; months: number; weeks: number }[] = [
  { id: 'WEEKLY', label: 'Ogni settimana', short: '/sett', months: 0, weeks: 1 },
  { id: 'BIWEEKLY', label: 'Ogni 2 settimane', short: '/2 sett', months: 0, weeks: 2 },
  { id: 'MONTHLY', label: 'Ogni mese', short: '/mese', months: 1, weeks: 0 },
  { id: 'BIMONTHLY', label: 'Ogni 2 mesi', short: '/2 mesi', months: 2, weeks: 0 },
  { id: 'QUARTERLY', label: 'Ogni 3 mesi', short: '/3 mesi', months: 3, weeks: 0 },
  { id: 'SEMIANNUAL', label: 'Ogni 6 mesi', short: '/6 mesi', months: 6, weeks: 0 },
  { id: 'YEARLY', label: 'Ogni anno', short: '/anno', months: 12, weeks: 0 },
  { id: 'BIENNIAL', label: 'Ogni 2 anni', short: '/2 anni', months: 24, weeks: 0 },
]

export function frequency(id: Frequency) {
  return frequencies.find((f) => f.id === id) ?? frequencies[2]
}

/** Average cost per month of something billed [cents] at this frequency (Long arithmetic, like Kotlin). */
export function monthlyCents(id: Frequency, cents: number): number {
  const f = frequency(id)
  return f.weeks > 0 ? Math.trunc((cents * 52) / (12 * f.weeks)) : Math.trunc(cents / f.months)
}

/** Cost over a year of something billed [cents] at this frequency. */
export function yearlyCents(id: Frequency, cents: number): number {
  const f = frequency(id)
  return f.weeks > 0 ? Math.trunc((cents * 52) / f.weeks) : Math.trunc((cents * 12) / f.months)
}

/** Order of the fixed expenses list; paused ones always go last. */
export type RecurringSort = 'NEXT' | 'NAME' | 'COST_HIGH' | 'COST_LOW' | 'PAYMENT'

export const recurringSorts: { id: RecurringSort; label: string }[] = [
  { id: 'NEXT', label: 'Prossimo addebito' },
  { id: 'NAME', label: 'Nome' },
  { id: 'COST_HIGH', label: 'Costo più alto' },
  { id: 'COST_LOW', label: 'Costo più basso' },
  { id: 'PAYMENT', label: 'Metodo di pagamento' },
]

export type Category = {
  id: number
  name: string
  icon: string
  /** ARGB color. */
  color: number
  type: TxType
  /** Set for subcategories; null for top level categories. */
  parentId: number | null
  /** Monthly budget, only for top level expense categories. */
  budgetCents: number | null
  sortOrder: number
}

export type Transaction = {
  id: number
  type: TxType
  amountCents: number
  /** Epoch day. */
  date: number
  categoryId: number
  subcategoryId: number | null
  payment: PaymentMethod
  note: string
  receiptPath: string | null
  /** Set when generated automatically from a fixed expense. */
  recurringId: number | null
  createdAt: number
  /** Person of the household the movement belongs to. */
  memberId: number | null
  /** Custom payment method; when set, payment is OTHER. */
  paymentId: number | null
  /** How the expense counts when settling up between people: one of Settle. */
  settle: number
}

/** States of Transaction.settle. */
export const Settle = {
  /** Counts the next time people settle up. */
  OPEN: 0,
  /** Never counts: the payer keeps it for themselves. */
  EXCLUDED: 1,
  /** Already settled up: counted once, not again. */
  SETTLED: 2,
} as const

export type Recurring = {
  id: number
  kind: RecurringKind
  name: string
  amountCents: number
  categoryId: number
  subcategoryId: number | null
  payment: PaymentMethod
  frequency: Frequency
  /** First billing date (epoch day); every occurrence is computed from it. */
  startDate: number
  /** Next billing date not yet processed (epoch day). */
  nextDate: number
  brandKey: string | null
  imagePath: string | null
  icon: string
  color: number
  autoRegister: boolean
  /** Days before billing to notify; negative disables the reminder. */
  remindDaysBefore: number
  notifiedFor: number | null
  active: boolean
  note: string
  /** Custom payment method; when set, payment is OTHER. */
  paymentId: number | null
  /** How many installments a financing has; null for subscriptions and fixed expenses. */
  installments: number | null
  /** Installments already paid: on the last one the financing closes itself. */
  installmentsPaid: number
}

/** Installments still to pay, or null when it is not a financing. */
export function installmentsLeft(r: Recurring): number | null {
  return r.installments == null ? null : Math.max(0, r.installments - r.installmentsPaid)
}

/** The item after one more payment: a financing counts it and stops after the last installment. */
export function afterPayment(r: Recurring): Recurring {
  if (r.installments == null) return r
  const paid = r.installmentsPaid + 1
  return { ...r, installmentsPaid: paid, active: r.active && paid < r.installments }
}

export type Member = {
  id: number
  name: string
  color: number
  sortOrder: number
}

/** Money of the household (a bank account, the cash...), moved by the payment methods it holds. */
export type Account = {
  id: number
  name: string
  icon: string
  /** ARGB color. */
  color: number
  /** Comma separated [methodKey]s: "CARD,DEBIT,c3". A method belongs to one account at most. */
  methods: string
  /** The balance stated at [balanceAt]: movements and transfers registered after it move it. */
  balanceCents: number
  /** Epoch millis. */
  balanceAt: number
  sortOrder: number
}

/** Money moved from one account to another: neither an expense nor an income. */
export type Transfer = {
  id: number
  fromId: number
  toId: number
  amountCents: number
  /** Epoch day. */
  date: number
  note: string
  createdAt: number
}

/** "CARD", or "c3" for the custom method 3. */
export const methodKey = (c: PaymentChoice) => (c.customId != null ? `c${c.customId}` : c.method)

export const accountMethods = (a: Account) => (a.methods ? a.methods.split(',') : [])

/** The account holding [choice], if any. */
export const accountOf = (choice: PaymentChoice, accounts: Account[]) =>
  accounts.find((a) => accountMethods(a).includes(methodKey(choice)))

/** Movements of [account] registered after its stated balance, so the ones that move it. */
export const accountTransactions = (account: Account, accounts: Account[], transactions: Transaction[]) =>
  transactions.filter((t) => t.createdAt > account.balanceAt && accountOf(paymentChoice(t), accounts)?.id === account.id)

/** Transfers in or out of [account] registered after its stated balance. */
export const accountTransfers = (account: Account, transfers: Transfer[]) =>
  transfers.filter((t) => t.createdAt > account.balanceAt && (t.fromId === account.id || t.toId === account.id))

/** What [account] holds now: its stated balance plus what was registered after it. */
export function balanceOf(account: Account, accounts: Account[], transactions: Transaction[], transfers: Transfer[]): number {
  let cents = account.balanceCents
  for (const t of accountTransactions(account, accounts, transactions)) cents += t.type === 'INCOME' ? t.amountCents : -t.amountCents
  for (const t of accountTransfers(account, transfers)) cents += t.toId === account.id ? t.amountCents : -t.amountCents
  return cents
}

export type AppState = {
  categories: Category[]
  transactions: Transaction[]
  recurring: Recurring[]
  members: Member[]
  paymentMethods: CustomPayment[]
  /** Servers and backups before accounts don't have them. */
  accounts?: Account[]
  transfers?: Transfer[]
}

/** Pastel palette shared by categories and fixed expenses. */
export const Palette = {
  LILAC: 0xffb8a6ff,
  PEACH: 0xffffb4a2,
  MINT: 0xff8fe3c0,
  BUTTER: 0xffffd86e,
  SKY: 0xff8ec9ff,
  ROSE: 0xffff9ec4,
  LIME: 0xffc9f27a,
  CORAL: 0xffff8a7a,
  SAND: 0xffe8c9a0,
  TEAL: 0xff7fd8d8,
  LAVENDER: 0xffd9b8ff,
  ORANGE: 0xffffb86b,
}

export const paletteAll: number[] = [
  Palette.LILAC, Palette.PEACH, Palette.MINT, Palette.BUTTER, Palette.SKY, Palette.ROSE,
  Palette.LIME, Palette.CORAL, Palette.SAND, Palette.TEAL, Palette.LAVENDER, Palette.ORANGE,
]

/** ARGB long to a CSS color. */
export function argb(color: number, alpha?: number): string {
  const a = alpha ?? ((color >>> 24) & 0xff) / 255
  const r = (color >>> 16) & 0xff
  const g = (color >>> 8) & 0xff
  const b = color & 0xff
  return a >= 1 ? `rgb(${r},${g},${b})` : `rgba(${r},${g},${b},${a})`
}

type DefaultCategory = { name: string; icon: string; color: number; subs?: string[] }

export const defaultExpenseCategories: DefaultCategory[] = [
  { name: 'Casa', icon: 'home', color: Palette.PEACH, subs: ['Affitto', 'Mutuo', 'Bollette', 'Condominio', 'Manutenzione'] },
  { name: 'Cibo', icon: 'food', color: Palette.BUTTER, subs: ['Supermercato', 'Ristorante', 'Delivery', 'Bar e caffè'] },
  { name: 'Trasporti', icon: 'car', color: Palette.SKY, subs: ['Carburante', 'Mezzi pubblici', 'Auto', 'Parcheggio', 'Taxi'] },
  { name: 'Abbonamenti', icon: 'repeat', color: Palette.LILAC, subs: ['Streaming', 'Musica', 'Software', 'Telefonia', 'Internet'] },
  { name: 'Svago', icon: 'party', color: Palette.ROSE, subs: ['Uscite', 'Hobby', 'Cinema ed eventi', 'Giochi'] },
  { name: 'Viaggi', icon: 'flight', color: Palette.TEAL, subs: ['Alloggio', 'Biglietti', 'Vacanza'] },
  { name: 'Shopping', icon: 'bag', color: Palette.CORAL, subs: ['Abbigliamento', 'Elettronica', 'Arredo'] },
  { name: 'Salute', icon: 'health', color: Palette.MINT, subs: ['Farmacia', 'Visite', 'Sport e palestra'] },
  { name: 'Cura personale', icon: 'spa', color: Palette.LAVENDER, subs: ['Parrucchiere', 'Cosmetici'] },
  { name: 'Istruzione', icon: 'school', color: Palette.SAND, subs: ['Corsi', 'Libri'] },
  { name: 'Tasse e assicurazioni', icon: 'receipt', color: Palette.ORANGE, subs: ['Tasse', 'Assicurazioni'] },
  { name: 'Regali', icon: 'gift', color: Palette.ROSE },
  { name: 'Animali', icon: 'pets', color: Palette.LIME },
  { name: 'Altro', icon: 'more', color: Palette.SAND },
]

export const defaultIncomeCategories: DefaultCategory[] = [
  { name: 'Stipendio', icon: 'work', color: Palette.MINT },
  { name: 'Freelance', icon: 'laptop', color: Palette.SKY },
  { name: 'Rimborsi', icon: 'undo', color: Palette.BUTTER },
  { name: 'Regali', icon: 'gift', color: Palette.ROSE },
  { name: 'Investimenti', icon: 'trending', color: Palette.LILAC },
  { name: 'Vendite', icon: 'sell', color: Palette.PEACH },
  { name: 'Altro', icon: 'more', color: Palette.SAND },
]
