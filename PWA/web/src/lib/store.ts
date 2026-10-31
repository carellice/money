import { useSyncExternalStore } from 'react'
import { currentMonth, type Month } from '../../../shared/date.ts'
import { Palette, type AppState, type Category, type Member, type Recurring, type Transaction } from '../../../shared/model.ts'
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
  const defaults: Settings = { theme: 'SYSTEM', lockCredential: null, remindersEnabled: false, accent: Palette.LILAC, onboarded: false }
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
  if (loading) return loading
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
  deleteTransaction: (tx: Transaction) => {
    // Optimistic: the row leaves the list at once, like on the phone.
    if (store.data) set({ data: { ...store.data, transactions: store.data.transactions.filter((t) => t.id !== tx.id) } })
    return mutate(() => api('/api/transactions/' + tx.id, { method: 'DELETE' }))
  },
  saveRecurring: (item: Recurring) => mutate(() => api<Recurring>('/api/recurring', { method: 'POST', json: item })),
  deleteRecurring: (item: Recurring) => mutate(() => api('/api/recurring/' + item.id, { method: 'DELETE' })),
  saveCategory: (category: Category, subs: Category[]) => mutate(() => api('/api/categories', { method: 'POST', json: { category, subs } })),
  deleteCategory: (category: Category) => mutate(() => api('/api/categories/' + category.id, { method: 'DELETE' })),
  saveMember: (member: Member) => mutate(() => api('/api/members', { method: 'POST', json: member })),
  deleteMember: (member: Member) => mutate(() => api('/api/members/' + member.id, { method: 'DELETE' })),

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
