import { useSyncExternalStore } from 'react'
import { currentMonth, type Month } from '../../../shared/date.ts'
import { Palette, type RecurringSort, type Account, type AppState, type Category, type CustomPayment, type Member, type PaymentChoice, type PaymentMethod, type Recurring, type Transaction, type Transfer } from '../../../shared/model.ts'
import { api } from './api.ts'

// --- device settings (theme, accent, lock): per phone, like the Android DataStore -----------

export type ThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK'
export const themeModes: { id: ThemeMode; label: string }[] = [
  { id: 'SYSTEM', label: 'Sistema' },
  { id: 'LIGHT', label: 'Chiaro' },
  { id: 'DARK', label: 'Scuro' },
]

export type Settings = {
  theme: ThemeMode
  /** WebAuthn credential used as the fingerprint lock, null when the lock is off. */
  lockCredential: string | null
  remindersEnabled: boolean
  /** ARGB of the app primary color. */
  accent: number
  onboarded: boolean
  recurringSort: RecurringSort
  /** Amounts on Home and Movimenti shown as dots, for prying eyes. */
  hideAmounts: boolean
  /** Only the account balances (Disponibile) shown as dots. */
  hideBalances: boolean
  /** Modern UI look (Windows Phone: flat tiles, big lowercase titles) instead of the classic one. */
  metro: boolean
  /** Nothing look (monochrome, dot-matrix, Space Mono labels), wins over [metro]. */
  nothing: boolean
}

// Every account keeps its own settings and data cache, so a shared tablet can switch between them.
const LAST_USER_KEY = 'money.user'
const settingsKey = (user: string) => `money.settings.${user.toLowerCase()}`
const cacheKey = (user: string) => `money.cache.${user.toLowerCase()}`

function read(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}

function write(key: string, value: string | null) {
  try {
    if (value == null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch { /* too big or private mode */ }
}

let currentUser = read(LAST_USER_KEY) ?? ''

function loadSettings(user: string): Settings {
  const defaults: Settings = {
    theme: 'SYSTEM', lockCredential: null, remindersEnabled: false, accent: Palette.LILAC, onboarded: false, recurringSort: 'NEXT', hideAmounts: false, hideBalances: false, metro: false, nothing: false,
  }
  try {
    return { ...defaults, ...JSON.parse(read(settingsKey(user)) ?? '{}') }
  } catch {
    return defaults
  }
}

// --- store -----------------------------------------------------------------------------------

type Store = {
  data: AppState | null
  revision: number
  /** Month shown by home, movements and report, kept in sync between screens. */
  month: Month
  settings: Settings
  loadError: string | null
}

function cachedData(user: string): { data: AppState; revision: number } | null {
  try {
    const raw = read(cacheKey(user))
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

let store: Store = {
  data: currentUser ? cachedData(currentUser)?.data ?? null : null,
  revision: -1,
  month: currentMonth(),
  settings: loadSettings(currentUser),
  loadError: null,
}
const listeners = new Set<() => void>()

function set(patch: Partial<Store>) {
  store = { ...store, ...patch }
  listeners.forEach((l) => l())
}

export function useStore<T>(selector: (s: Store) => T): T {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => selector(store),
  )
}

export const getStore = () => store

const noPayments: CustomPayment[] = []

/** Custom payment methods (data cached by an older version has none). */
export const usePaymentMethods = () => useStore((s) => s.data?.paymentMethods ?? noPayments)

const noHidden: PaymentMethod[] = []

/** Built-in payment methods the user removed. */
export const useHiddenPayments = () => useStore((s) => s.data?.hiddenPayments ?? noHidden)

const noAccounts: Account[] = []
const noTransfers: Transfer[] = []

/** Accounts of the household (servers before accounts send none). */
export const useAccounts = () => useStore((s) => s.data?.accounts ?? noAccounts)
export const useTransfers = () => useStore((s) => s.data?.transfers ?? noTransfers)

export function setMonth(month: Month) {
  set({ month })
}

export function updateSettings(patch: Partial<Settings>) {
  const settings = { ...store.settings, ...patch }
  write(settingsKey(currentUser), JSON.stringify(settings))
  set({ settings })
}

/** Switches to the settings and data of [user], after logging in or when the app opens. */
export function setUser(user: string) {
  if (user.toLowerCase() === currentUser.toLowerCase() && store.data) {
    currentUser = user
    write(LAST_USER_KEY, user)
    return
  }
  currentUser = user
  write(LAST_USER_KEY, user)
  set({ data: cachedData(user)?.data ?? null, revision: -1, month: currentMonth(), settings: loadSettings(user), loadError: null })
}

/** Forgets the data of the account that just left this device (deleted account). */
export function forgetUser(user: string) {
  write(settingsKey(user), null)
  write(cacheKey(user), null)
}

export const loggedUser = () => currentUser

// --- data ------------------------------------------------------------------------------------

type StateResponse = ({ unchanged: true } | AppState) & { revision: number }

let loading: Promise<void> | null = null

/** Reloads the data when it changed on the server (another phone, a fixed expense registered...). */
export function refresh(force = false): Promise<void> {
  // A forced refresh after a change must not settle for a load that started before it.
  if (loading) return force ? loading.then(() => refresh(true)) : loading
  loading = (async () => {
    try {
      const since = force || !store.data ? '' : `?since=${store.revision}`
      const result = await api<StateResponse>(`/api/state${since}`)
      if ('unchanged' in result) return
      const { revision, ...data } = result
      set({ data: data as AppState, revision, loadError: null })
      write(cacheKey(currentUser), JSON.stringify({ data, revision }))
    } catch (e) {
      set({ loadError: (e as Error).message })
    } finally {
      loading = null
    }
  })()
  return loading
}

async function mutate<T>(work: () => Promise<T>): Promise<T> {
  const result = await work()
  await refresh(true)
  return result
}

export const actions = {
  saveTransaction: (tx: Transaction) => mutate(() => api<Transaction>('/api/transactions', { method: 'POST', json: tx })),
  /** People settled up: these expenses don't count again. */
  settleTransactions: (ids: number[]) => mutate(() => api('/api/transactions/settle', { method: 'POST', json: { ids } })),
  deleteTransaction: (tx: Transaction) => {
    // Optimistic: the row leaves the list at once, like on the phone.
    if (store.data) set({ data: { ...store.data, transactions: store.data.transactions.filter((t) => t.id !== tx.id) } })
    return mutate(() => api('/api/transactions/' + tx.id, { method: 'DELETE' }))
  },
  /** "Annulla" after a delete: the row is back at once, saved again once the delete is done. */
  restoreTransaction: async (tx: Transaction, deleting: Promise<unknown>) => {
    if (store.data) {
      const transactions = [...store.data.transactions.filter((t) => t.id !== tx.id), tx]
        .sort((a, b) => b.date - a.date || b.createdAt - a.createdAt)
      set({ data: { ...store.data, transactions } })
    }
    await deleting.catch(() => {})
    return mutate(() => api<Transaction>('/api/transactions', { method: 'POST', json: tx }))
  },
  saveRecurring: (item: Recurring) => mutate(() => api<Recurring>('/api/recurring', { method: 'POST', json: item })),
  deleteRecurring: (item: Recurring) => mutate(() => api('/api/recurring/' + item.id, { method: 'DELETE' })),
  saveCategory: (category: Category, subs: Category[]) => mutate(() => api('/api/categories', { method: 'POST', json: { category, subs } })),
  deleteCategory: (category: Category) => mutate(() => api('/api/categories/' + category.id, { method: 'DELETE' })),
  saveMember: (member: Member) => mutate(() => api('/api/members', { method: 'POST', json: member })),
  deleteMember: (member: Member) => mutate(() => api('/api/members/' + member.id, { method: 'DELETE' })),
  /** Returns the id of the payment method. */
  savePaymentMethod: (method: CustomPayment) =>
    mutate(() => api<{ id: number }>('/api/payment-methods', { method: 'POST', json: method })).then((r) => r.id),
  deletePaymentMethod: (method: CustomPayment) => mutate(() => api('/api/payment-methods/' + method.id, { method: 'DELETE' })),
  /** Removes [from] (built-in ones get hidden); its movements and fixed expenses move to [to]. */
  removePaymentMethod: (from: PaymentChoice, to: PaymentChoice | null) =>
    mutate(() => api('/api/payment-methods/remove', { method: 'POST', json: { from, to } })),
  /** Returns the id of the account. */
  saveAccount: (account: Account) =>
    mutate(() => api<{ id: number }>('/api/accounts', { method: 'POST', json: account })).then((r) => r.id),
  deleteAccount: (account: Account) => mutate(() => api('/api/accounts/' + account.id, { method: 'DELETE' })),
  saveTransfer: (transfer: Transfer) => mutate(() => api('/api/transfers', { method: 'POST', json: transfer })),
  deleteTransfer: (transfer: Transfer) => mutate(() => api('/api/transfers/' + transfer.id, { method: 'DELETE' })),

  async uploadImage(blob: Blob, prefix: 'receipt' | 'logo'): Promise<string> {
    const { path } = await api<{ path: string }>(`/api/images?prefix=${prefix}`, {
      method: 'POST',
      body: blob,
      headers: { 'Content-Type': 'image/jpeg' },
    })
    return path
  },

  deleteImage(path: string) {
    api('/api/images?path=' + encodeURIComponent(path), { method: 'DELETE' }).catch(() => {})
  },

  /** Called when an undo window has passed and the movement is gone for good. */
  discardReceipt(tx: Transaction) {
    if (tx.receiptPath) actions.deleteImage(tx.receiptPath)
  },
}

// Keep in step with the other phones of the household.
if (typeof window !== 'undefined') {
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') refresh() })
  setInterval(() => { if (document.visibilityState === 'visible') refresh() }, 30_000)
}

/** True when the Modern UI (Windows Phone) style is on; re-renders the caller when it changes. */
export const useMetro = () => useStore((st) => st.settings.metro && !st.settings.nothing)

/** True when the Nothing style is on; re-renders the caller when it changes. */
export const useNothing = () => useStore((st) => st.settings.nothing)
