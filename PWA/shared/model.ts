// Data model, same tables and fields as the Android app (Room entities), so backups move freely between them.

export type TxType = 'EXPENSE' | 'INCOME'
export type RecurringKind = 'SUBSCRIPTION' | 'FIXED'

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

export type Frequency = 'WEEKLY' | 'MONTHLY' | 'BIMONTHLY' | 'QUARTERLY' | 'SEMIANNUAL' | 'YEARLY' | 'BIENNIAL'

export const frequencies: { id: Frequency; label: string; short: string; months: number; weeks: number }[] = [
  { id: 'WEEKLY', label: 'Ogni settimana', short: '/sett', months: 0, weeks: 1 },
  { id: 'MONTHLY', label: 'Ogni mese', short: '/mese', months: 1, weeks: 0 },
  { id: 'BIMONTHLY', label: 'Ogni 2 mesi', short: '/2 mesi', months: 2, weeks: 0 },
  { id: 'QUARTERLY', label: 'Ogni 3 mesi', short: '/3 mesi', months: 3, weeks: 0 },
  { id: 'SEMIANNUAL', label: 'Ogni 6 mesi', short: '/6 mesi', months: 6, weeks: 0 },
  { id: 'YEARLY', label: 'Ogni anno', short: '/anno', months: 12, weeks: 0 },
  { id: 'BIENNIAL', label: 'Ogni 2 anni', short: '/2 anni', months: 24, weeks: 0 },
]

export function frequency(id: Frequency) {
  return frequencies.find((f) => f.id === id) ?? frequencies[1]
}

/** Average cost per month of something billed [cents] at this frequency (Long arithmetic, like Kotlin). */
export function monthlyCents(id: Frequency, cents: number): number {
  const f = frequency(id)
  return f.weeks > 0 ? Math.trunc((cents * 52) / 12) : Math.trunc(cents / f.months)
}

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
}

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
}

export type Member = {
  id: number
  name: string
  color: number
  sortOrder: number
}

export type AppState = {
  categories: Category[]
  transactions: Transaction[]
  recurring: Recurring[]
  members: Member[]
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
