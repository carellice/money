import { useEffect, useSyncExternalStore } from 'react'

// A small back stack in the spirit of Navigation Compose, wired to the browser history
// so the Android back gesture and the browser back button behave like in the app.

export type Route =
  | 'home' | 'transactions' | 'recurring' | 'report'
  | 'add' | 'recurringEdit' | 'categories' | 'settings' | 'chat' | 'aiSettings'

export type Params = { type?: 'EXPENSE' | 'INCOME'; id?: number; kind?: 'SUBSCRIPTION' | 'FIXED' }

export type Entry = { key: number; route: Route; params: Params }

export const topLevel: Route[] = ['home', 'transactions', 'recurring', 'report']
/** Screens that slide up from the bottom, like the add and edit forms on Android. */
export const sheetRoutes: Route[] = ['add', 'recurringEdit']

let nextKey = 1
let stack: Entry[] = [{ key: nextKey++, route: 'home', params: {} }]
let snapshot = { stack, popping: false }
const listeners = new Set<() => void>()
const backHandlers: (() => boolean)[] = []
/** Set while the app itself goes back (closing a screen), so BackHandlers don't take it. */
let programmatic = false
/** Fired when the Home tab is selected again, to jump back to the top. */
export const homeReselect = new EventTarget()

function emit(popping: boolean) {
  snapshot = { stack, popping }
  listeners.forEach((l) => l())
}

export function useNav() {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => snapshot,
  )
}

export const top = () => stack[stack.length - 1]

history.replaceState({ depth: 0 }, '')

window.addEventListener('popstate', (event) => {
  const depth = (event.state as { depth?: number } | null)?.depth ?? 0
  if (depth >= stack.length - 1) return
  const fromApp = programmatic
  programmatic = false
  // A screen can take the back gesture for itself (BackHandler).
  for (let i = fromApp ? -1 : backHandlers.length - 1; i >= 0; i--) {
    if (backHandlers[i]()) {
      history.pushState({ depth: stack.length - 1 }, '')
      return
    }
  }
  stack = stack.slice(0, depth + 1)
  emit(true)
})

export function navigate(route: Route, params: Params = {}) {
  stack = [...stack, { key: nextKey++, route, params }]
  history.pushState({ depth: stack.length - 1 }, '')
  emit(false)
}

export function back() {
  if (stack.length > 1) {
    programmatic = true
    history.back()
  }
}

/** Pops only if [route] is still on top, so double close requests are harmless. */
export function close(route: Route) {
  if (top().route === route) back()
}

export function switchTab(target: Route) {
  // Tapping the tab you are already on does nothing.
  if (top().route === target) return
  if (target === 'home') {
    homeReselect.dispatchEvent(new Event('reselect'))
    if (stack.length > 1) {
      programmatic = true
      history.go(-(stack.length - 1))
    }
    return
  }
  if (stack.length === 1) {
    navigate(target)
    return
  }
  // Every tab sits right above Home, so switching replaces the tab on top.
  stack = [stack[0], { key: nextKey++, route: target, params: {} }]
  history.replaceState({ depth: 1 }, '')
  emit(false)
}

/** BackHandler: [handler] returns true when it consumed the back gesture. */
export function useBackHandler(enabled: boolean, handler: () => void) {
  useEffect(() => {
    if (!enabled) return
    const h = () => {
      handler()
      return true
    }
    backHandlers.push(h)
    return () => {
      const i = backHandlers.indexOf(h)
      if (i >= 0) backHandlers.splice(i, 1)
    }
  }, [enabled, handler])
}

/** Opens a deep link such as "/?route=report&month=2026-09" from a notification or a shortcut. */
export function openDeepLink(url: string, onMonth: (key: string) => void) {
  const params = new URL(url, location.origin).searchParams
  const route = params.get('route')
  const month = params.get('month')
  if (month) onMonth(month)
  if (route === 'add') navigate('add', { type: params.get('type') === 'INCOME' ? 'INCOME' : 'EXPENSE' })
  else if (route && topLevel.includes(route as Route)) switchTab(route as Route)
}
