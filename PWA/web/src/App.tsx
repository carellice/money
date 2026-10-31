import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { parseMonthKey } from '../../shared/date.ts'
import { api, authEvents } from './lib/api.ts'
import { blobTime, onFrame } from './lib/blob.ts'
import { navigate, openDeepLink, sheetRoutes, switchTab, topLevel, useNav, type Entry, type Route } from './lib/nav.ts'
import { getStore, loggedUser, refresh, setMonth, setUser, updateSettings, useStore } from './lib/store.ts'
import { applyTheme, Ink, themeColors } from './lib/theme.ts'
import { BlobLayer } from './components/Blob.tsx'
import { MessagesHost } from './components/overlays.tsx'
import { Bouncy, Icon } from './components/ui.tsx'
import { spring, tween } from './components/motion.ts'
import type { UiIcon } from './lib/icons.gen.ts'
import { HomeScreen } from './screens/Home.tsx'
import { TransactionsScreen } from './screens/Transactions.tsx'
import { RecurringScreen } from './screens/Recurring.tsx'
import { RecurringEditScreen } from './screens/RecurringEdit.tsx'
import { ReportScreen } from './screens/Report.tsx'
import { AddTransactionScreen } from './screens/AddTransaction.tsx'
import { CategoriesScreen } from './screens/Categories.tsx'
import { SettingsScreen } from './screens/Settings.tsx'
import { ChatScreen } from './screens/Chat.tsx'
import { AiSettingsScreen } from './screens/AiSettings.tsx'
import { OnboardingScreen } from './screens/Onboarding.tsx'
import { LockScreen, unlockWithDevice } from './screens/Lock.tsx'
import { LoginScreen } from './screens/Login.tsx'

const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')
function useSystemDark() {
  return useSyncExternalStore(
    (l) => {
      darkQuery.addEventListener('change', l)
      return () => darkQuery.removeEventListener('change', l)
    },
    () => darkQuery.matches,
  )
}

/** Short trips outside the app (camera, file picker) don't lock it again. */
const LOCK_AFTER_MS = 30_000

export function App() {
  const settings = useStore((s) => s.settings)
  const systemDark = useSystemDark()
  const dark = settings.theme === 'SYSTEM' ? systemDark : settings.theme === 'DARK'
  const [session, setSession] = useState<'checking' | 'login' | 'ready'>('checking')
  const [locked, setLocked] = useState(false)
  const onboarding = !settings.onboarded
  const hiddenAt = useRef(0)

  useEffect(() => applyTheme(themeColors(dark, settings.accent)), [dark, settings.accent])

  useEffect(() => {
    const check = () => api<{ loggedIn: boolean; username: string | null }>('/api/session')
      .then((s) => {
        if (!s.loggedIn || !s.username) return setSession('login')
        setUser(s.username)
        setLocked(getStore().settings.lockCredential != null)
        setSession('ready')
      })
      // Offline: open with the data cached on this device.
      .catch(() => {
        if (!loggedUser()) return setSession('login')
        setLocked(getStore().settings.lockCredential != null)
        setSession('ready')
      })
    check()
    const logout = () => setSession('login')
    authEvents.addEventListener('logout', logout)
    return () => authEvents.removeEventListener('logout', logout)
  }, [])

  useEffect(() => {
    if (session !== 'ready') return
    refresh(true)
    openDeepLink(location.href, (key) => { const m = parseMonthKey(key); if (m != null) setMonth(m) })
    history.replaceState(history.state, '', '/')
    const onMessage = (e: MessageEvent) => {
      if (e.data?.type === 'open') openDeepLink(e.data.url, (key) => { const m = parseMonthKey(key); if (m != null) setMonth(m) })
    }
    navigator.serviceWorker?.addEventListener('message', onMessage)
    return () => navigator.serviceWorker?.removeEventListener('message', onMessage)
  }, [session])

  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') hiddenAt.current = Date.now()
      else if (settings.lockCredential && hiddenAt.current && Date.now() - hiddenAt.current > LOCK_AFTER_MS) setLocked(true)
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [settings.lockCredential])

  const unlock = async () => {
    if (await unlockWithDevice(settings.lockCredential)) setLocked(false)
  }

  return (
    <div className="app">
      {session === 'ready' && <Navigator />}
      <div id="overlays" />
      <MessagesHost />
      <AnimatePresence>
        {session === 'login' && (
          <motion.div key="login" className="layer" style={{ zIndex: 90 }} exit={{ opacity: 0, scale: 1.1 }}>
            <LoginScreen onDone={(username) => {
              setUser(username)
              setLocked(false)
              setSession('ready')
            }} />
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {session === 'ready' && locked && settings.lockCredential && (
          <motion.div key="lock" className="layer" style={{ zIndex: 95 }} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0, scale: 1.1 }}>
            <LockScreen onUnlockRequest={unlock} />
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {session === 'ready' && onboarding && (
          <motion.div key="onboarding" className="layer" style={{ zIndex: 96 }} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0, scale: 1.1 }}>
            <OnboardingScreen onDone={() => updateSettings({ onboarded: true })} />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function Screen({ entry }: { entry: Entry }) {
  const p = entry.params
  switch (entry.route) {
    case 'home': return <HomeScreen />
    case 'transactions': return <TransactionsScreen />
    case 'recurring': return <RecurringScreen />
    case 'report': return <ReportScreen />
    case 'add': return <AddTransactionScreen type={p.type ?? 'EXPENSE'} id={p.id ?? 0} />
    case 'recurringEdit': return <RecurringEditScreen id={p.id ?? 0} kind={p.kind ?? 'SUBSCRIPTION'} />
    case 'categories': return <CategoriesScreen />
    case 'settings': return <SettingsScreen />
    case 'chat': return <ChatScreen />
    case 'aiSettings': return <AiSettingsScreen />
  }
}

/** NavHost: screens stay mounted under the one on top, with the app's enter and exit transitions. */
function Navigator() {
  const { stack, popping } = useNav()
  const topEntry = stack[stack.length - 1]
  return (
    <>
      <AnimatePresence initial={false} custom={popping}>
        {stack.map((entry, i) => {
          const isTop = i === stack.length - 1
          const sheet = sheetRoutes.includes(entry.route)
          return (
            <motion.div
              key={entry.key}
              className="layer"
              custom={popping}
              initial={sheet ? { opacity: 0, y: '50%' } : { opacity: 0, scale: 0.97 }}
              animate={isTop
                ? { opacity: 1, scale: 1, y: 0, transition: sheet ? { y: spring(0.85, 320), opacity: tween(240) } : tween(240) }
                : { opacity: 0, scale: 1.03, transition: tween(180) }}
              exit={sheet
                ? { opacity: 0, y: '50%', transition: { y: tween(260), opacity: tween(200) } }
                : { opacity: 0, scale: 0.97, transition: tween(180) }}
              style={{ zIndex: i, pointerEvents: isTop ? 'auto' : 'none', visibility: undefined }}
              aria-hidden={!isTop}
            >
              <Screen entry={entry} />
            </motion.div>
          )
        })}
      </AnimatePresence>
      <AnimatePresence>
        {topLevel.includes(topEntry.route) && (
          <motion.div
            key="bottombar"
            initial={{ y: '100%', opacity: 0 }}
            animate={{ y: 0, opacity: 1, transition: { y: spring(0.7, 300), opacity: tween(200) } }}
            exit={{ y: '100%', opacity: 0, transition: tween(250) }}
            style={{ position: 'absolute', left: 0, right: 0, bottom: 0, zIndex: 30 }}
          >
            <BottomBar route={topEntry.route} />
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

const tabs: { route: Route; label: string; icon: UiIcon }[] = [
  { route: 'home', label: 'Home', icon: 'home' },
  { route: 'transactions', label: 'Movimenti', icon: 'receipt_long' },
  { route: 'recurring', label: 'Fisse', icon: 'autorenew' },
  { route: 'report', label: 'Report', icon: 'donut_large' },
]

function BottomBar({ route }: { route: Route }) {
  return (
    <div className="center nav-pad" style={{ position: 'relative' }}>
      <div style={{ position: 'relative', width: '100%', padding: '12px 16px' }}>
        <div className="row" style={{
          height: 80, borderRadius: 999, background: 'var(--surface)', padding: '0 10px',
          boxShadow: '0 12px 28px rgba(21,18,31,0.22), 0 2px 8px rgba(21,18,31,0.12)',
        }}>
          {tabs.slice(0, 2).map((t) => <TabItem key={t.route} tab={t} selected={route === t.route} />)}
          <div style={{ width: 80, flexShrink: 0 }} />
          {tabs.slice(2).map((t) => <TabItem key={t.route} tab={t} selected={route === t.route} />)}
        </div>
        <div className="center" style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <AddButton />
        </div>
      </div>
    </div>
  )
}

function TabItem({ tab, selected }: { tab: (typeof tabs)[number]; selected: boolean }) {
  return (
    <Bouncy pressedScale={0.9} onClick={() => switchTab(tab.route)} className="col flex1" style={{ height: '100%', alignItems: 'center', justifyContent: 'center' }}>
      <motion.div className="center" initial={false} animate={{ width: selected ? 56 : 40 }} transition={spring(0.6, 500)}
        style={{ height: 34, borderRadius: 999, background: selected ? 'var(--accent)' : 'var(--surface)', transition: 'background-color 0.25s' }}>
        <Icon name={tab.icon} size={24} color={selected ? Ink : 'var(--inkSoft)'} />
      </motion.div>
      <span className="t-caption" style={{ marginTop: 4, color: selected ? 'var(--ink)' : 'var(--inkSoft)', transition: 'color 0.25s', whiteSpace: 'nowrap' }}>
        {tab.label}
      </span>
    </Bouncy>
  )
}

function AddButton() {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => onFrame((time) => {
    const breathe = 1 + 0.035 * Math.sin(time * 2.2)
    if (ref.current) ref.current.style.transform = `scale(${breathe})`
  }), [])
  return (
    <div ref={ref} style={{ pointerEvents: 'auto', transform: `scale(${1 + 0.035 * Math.sin(blobTime() * 2.2)})` }}>
      <Bouncy pressedScale={0.85} onClick={() => navigate('add')} aria-label="Aggiungi" className="center blob-box" style={{ width: 70, height: 70 }}>
        <BlobLayer color="var(--accent)" seed={3} amplitude={0.09} speed={1.4} />
        <div className="blob-content center"><Icon name="add" size={32} color={Ink} /></div>
      </Bouncy>
    </div>
  )
}
