import { animate, AnimatePresence, motion, useMotionValue, useTransform } from 'framer-motion'
import { useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode, type RefObject } from 'react'
import { parseMonthKey } from '../../shared/date.ts'
import { api, authEvents } from './lib/api.ts'
import { BlobSpec, blobTime, onFrame } from './lib/blob.ts'
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
import { RecurringPaymentsScreen } from './screens/RecurringPayments.tsx'
import { RecurringRegisterScreen } from './screens/RecurringRegister.tsx'
import { ReportScreen } from './screens/Report.tsx'
import { AddTransactionScreen } from './screens/AddTransaction.tsx'
import { AccountScreen } from './screens/Accounts.tsx'
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
    case 'account': return <AccountScreen id={p.id ?? 0} />
    case 'recurringEdit': return <RecurringEditScreen id={p.id ?? 0} kind={p.kind ?? 'SUBSCRIPTION'} />
    case 'recurringRegister': return <RecurringRegisterScreen id={p.id ?? 0} />
    case 'recurringPayments': return <RecurringPaymentsScreen />
    case 'categories': return <CategoriesScreen />
    case 'settings': return <SettingsScreen />
    case 'chat': return <ChatScreen />
    case 'aiSettings': return <AiSettingsScreen />
  }
}

/** NavHost: screens stay mounted under the one on top, with the app's enter and exit transitions. */
function Navigator() {
  const { stack } = useNav()
  const topEntry = stack[stack.length - 1]
  // Between tabs the page slides the way the bar's capsule goes.
  const previous = useRef(topEntry)
  const dir = useRef(0)
  if (previous.current.key !== topEntry.key) {
    dir.current = tabDirection(previous.current.route, topEntry.route)
    previous.current = topEntry
  }
  const sliding = dir.current !== 0
  return (
    <>
      <AnimatePresence initial={false} custom={dir.current}>
        {stack.map((entry, i) => {
          const isTop = i === stack.length - 1
          const sheet = sheetRoutes.includes(entry.route)
          const under = tabDirection(entry.route, topEntry.route)
          return (
            <motion.div
              key={entry.key}
              className="layer"
              custom={dir.current}
              initial={sheet ? { opacity: 0, y: '50%' } : sliding ? { opacity: 0, x: `${dir.current * 25}%` } : { opacity: 0, scale: 0.97 }}
              animate={isTop
                ? {
                    opacity: 1, scale: 1, y: 0, x: 0,
                    transition: sheet ? { y: spring(0.85, 320), opacity: tween(240) } : sliding ? { x: spring(0.85, 380), opacity: tween(200) } : tween(240),
                  }
                : sliding && under !== 0
                  ? { opacity: 0, scale: 1, x: `${-under * 25}%`, transition: { x: spring(0.85, 380), opacity: tween(160) } }
                  : { opacity: 0, scale: 1.03, transition: tween(180) }}
              variants={{
                exit: (d: number) => sheet
                  ? { opacity: 0, y: '50%', transition: { y: tween(260), opacity: tween(200) } }
                  : d !== 0
                    ? { opacity: 0, x: `${-d * 25}%`, transition: { x: spring(0.85, 380), opacity: tween(160) } }
                    : { opacity: 0, scale: 0.97, transition: tween(180) },
              }}
              exit="exit"
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

/** +1 when moving to a tab on the right, -1 to one on the left, 0 when either side is not a tab. */
function tabDirection(from: Route, to: Route): number {
  const a = tabs.findIndex((t) => t.route === from)
  const b = tabs.findIndex((t) => t.route === to)
  return a < 0 || b < 0 ? 0 : Math.sign(b - a)
}

/** The capsule wraps the whole selected tab, icon and label, with a little air around it. */
const CAPSULE_INSET = 2
const CAPSULE_H = 64
/** How much bigger than the capsule the resting blob is. */
const BLOB_W = 1.22
const BLOB_H = 1.14

function BottomBar({ route }: { route: Route }) {
  const rowRef = useRef<HTMLDivElement>(null)
  const slots = useRef<(HTMLDivElement | null)[]>([])
  // Stable refs: a new callback each render would detach the tabs right when the capsule measures them.
  const slotRefs = useMemo(() => tabs.map((_, i) => (el: HTMLDivElement | null) => { slots.current[i] = el }), [])
  const selected = tabs.findIndex((t) => t.route === route)
  return (
    <div className="center nav-pad" style={{ position: 'relative' }}>
      <div style={{ position: 'relative', width: '100%', padding: '12px 16px' }}>
        <div ref={rowRef} className="row" style={{
          position: 'relative', height: 80, borderRadius: 999, background: 'var(--surface)', padding: '0 10px',
          boxShadow: '0 12px 28px rgba(21,18,31,0.22), 0 2px 8px rgba(21,18,31,0.12)',
        }}>
          {tabs.map((t, i) => (
            <TabSlot key={t.route} gap={i === 2} slotRef={slotRefs[i]}>
              <TabItem tab={t} selected={route === t.route} />
            </TabSlot>
          ))}
          {/* After the tabs, so they are in place when it measures them; it still sits behind. */}
          <LiquidCapsule index={selected} rowRef={rowRef} slots={slots} />
        </div>
        <div className="center" style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <AddButton />
        </div>
      </div>
    </div>
  )
}

/** Room for the add button goes before the third tab. */
function TabSlot({ gap, slotRef, children }: { gap: boolean; slotRef: (el: HTMLDivElement | null) => void; children: ReactNode }) {
  return (
    <>
      {gap && <div style={{ width: 80, flexShrink: 0 }} />}
      <div ref={slotRef} className="flex1" style={{ height: '100%', minWidth: 0, position: 'relative', zIndex: 1 }}>{children}</div>
    </>
  )
}

/**
 * The capsule behind the selected tab. Its two ends move on their own springs: the one in front
 * runs ahead and the one behind catches up later, so it stretches like a drop on the way and
 * settles back with a small bounce. Once it stops it slowly turns into a living blob, like the
 * add button, and goes back to a smooth capsule as soon as it moves again.
 */
function LiquidCapsule({ index, rowRef, slots }: {
  index: number
  rowRef: RefObject<HTMLDivElement | null>
  slots: RefObject<(HTMLDivElement | null)[]>
}) {
  const left = useMotionValue(0)
  const right = useMotionValue(0)
  const rest = useMotionValue(1)
  const middle = useMotionValue(0)
  // Hidden until it knows where the first tab is.
  const opacity = useMotionValue(0)
  const placed = useRef(false)
  const width = useTransform(() => Math.max(right.get() - left.get(), rest.get() * 0.78))
  const x = useTransform(() => (left.get() + right.get()) / 2 - width.get() / 2)
  // Stretched it gets a little thinner, squeezed a little taller, like a drop of liquid.
  const height = useTransform(() => Math.min(Math.max(CAPSULE_H * Math.pow(rest.get() / width.get(), 0.25), CAPSULE_H * 0.82), CAPSULE_H * 1.08))
  const y = useTransform(() => middle.get() - height.get() / 2)
  const radius = useTransform(() => Math.min(width.get(), height.get()) / 2)
  // 0 smooth capsule, 1 blob: the capsule shrinks inside the blob while the blob grows over it.
  const organic = useMotionValue(0)
  const capsuleScale = useTransform(() => 1 - 0.4 * organic.get())
  const capsuleOpacity = useTransform(() => (organic.get() > 0.99 ? 0 : 1))
  const blobScale = useTransform(() => 0.72 + 0.28 * organic.get())
  const blobOpacity = useTransform(() => (organic.get() < 0.01 ? 0 : opacity.get()))
  // Bigger than the capsule and following it as it is right now: a blob is round, and the label
  // must stay inside it; the shape changes without jumping anywhere.
  // Wide enough for the label too, which sits low where the blob is narrower.
  const labelWidth = useMotionValue(0)
  const blobWidth = useTransform(() => Math.max(width.get() * BLOB_W, labelWidth.get() / 0.8 + 8))
  const blobHeight = useTransform(() => Math.min(height.get() * BLOB_H, middle.get() * 2 - 6))
  const blobX = useTransform(() => (left.get() + right.get()) / 2 - blobWidth.get() / 2)
  const blobY = useTransform(() => middle.get() - blobHeight.get() / 2)
  const blobPath = useRef<SVGPathElement>(null)
  const spec = useMemo(() => new BlobSpec(11 + Math.max(index, 0), 7, 0.06), [index])

  useEffect(() => onFrame((time) => {
    if (organic.get() < 0.01 || !blobPath.current) return
    blobPath.current.setAttribute('d', spec.path(blobWidth.get(), blobHeight.get(), time * 1.4))
  }), [spec, organic, blobWidth, blobHeight])

  useLayoutEffect(() => {
    const row = rowRef.current
    if (!row || index < 0) return
    // Layout offsets, not on-screen boxes: a tab shrinks a little while pressed.
    const target = () => {
      const slot = slots.current?.[index]
      if (!slot) return null
      const label = slot.querySelector<HTMLElement>('.t-caption')
      return { center: slot.offsetLeft + slot.offsetWidth / 2, half: slot.offsetWidth / 2 - CAPSULE_INSET, label: label?.offsetWidth ?? 0 }
    }
    const place = () => {
      const t = target()
      if (!t) return
      middle.set(row.clientHeight / 2)
      rest.set(t.half * 2)
      labelWidth.set(t.label)
      left.set(t.center - t.half)
      right.set(t.center + t.half)
      opacity.set(1)
      organic.set(1)
      placed.current = true
    }
    const controls: { stop: () => void }[] = []
    const t = target()
    if (!t) return
    if (!placed.current) place()
    else {
      const toLeft = t.center - t.half
      labelWidth.set(t.label)
      const toRight = t.center + t.half
      const front = spring(0.8, 900)
      const back = spring(0.7, 240)
      const goingRight = toLeft > left.get()
      controls.push(animate(organic, 0, tween(120)))
      controls.push(animate(left, toLeft, goingRight ? back : front), animate(right, toRight, goingRight ? front : back))
      // As soon as both ends are about there, the blob takes over: the last wobble happens under it.
      let morphed = false
      const check = () => {
        if (morphed || Math.abs(left.get() - toLeft) >= 8 || Math.abs(right.get() - toRight) >= 8) return
        morphed = true
        controls.push(animate(organic, 1, tween(260)))
      }
      const offLeft = left.on('change', check)
      const offRight = right.on('change', check)
      controls.push({ stop: () => { offLeft(); offRight() } })
    }
    // A new width (rotation, resize) puts it straight in place.
    let width = row.clientWidth
    const observer = new ResizeObserver(() => {
      if (row.clientWidth === width) return
      width = row.clientWidth
      controls.forEach((c) => c.stop())
      place()
    })
    observer.observe(row)
    return () => {
      observer.disconnect()
      controls.forEach((c) => c.stop())
    }
  }, [index, rowRef, slots, left, right, rest, middle, opacity, organic, labelWidth])

  return (
    <>
      <motion.div style={{
        position: 'absolute', left: 0, top: 0, zIndex: 0, x, y, width, height, opacity, pointerEvents: 'none',
      }}>
        <motion.div style={{
          width: '100%', height: '100%', borderRadius: radius, background: 'var(--accent)', scale: capsuleScale, opacity: capsuleOpacity,
        }} />
      </motion.div>
      <motion.svg aria-hidden style={{
        position: 'absolute', left: 0, top: 0, zIndex: 0, x: blobX, y: blobY, width: blobWidth, height: blobHeight,
        scale: blobScale, opacity: blobOpacity, overflow: 'visible', pointerEvents: 'none',
      }}>
        <path ref={blobPath} fill="var(--accent)" />
      </motion.svg>
    </>
  )
}

function TabItem({ tab, selected }: { tab: (typeof tabs)[number]; selected: boolean }) {
  const icon = useRef<HTMLDivElement>(null)
  const armed = useRef(false)
  // Chosen: the icon shrinks at once, then springs back to size wobbling side to side.
  useEffect(() => {
    if (armed.current && selected && icon.current) {
      animate(icon.current, { scale: [0.62, 1] }, spring(0.38, 420))
      animate(icon.current, { rotate: [0, 11, -8, 5, -2.5, 1, 0] }, { duration: 0.7, ease: 'easeOut' })
    }
    armed.current = true
  }, [selected])
  return (
    <Bouncy pressedScale={0.9} onClick={() => switchTab(tab.route)} className="col" style={{ width: '100%', height: '100%', alignItems: 'center', justifyContent: 'center' }}>
      <div ref={icon} className="center"><Icon name={tab.icon} size={24} color={selected ? Ink : 'var(--inkSoft)'} /></div>
      {/* On the capsule both stay dark, as on every accent fill. */}
      <span className="t-caption" style={{ marginTop: 4, color: selected ? Ink : 'var(--inkSoft)', transition: 'color 0.25s', whiteSpace: 'nowrap' }}>
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
        {/* Only the outline, so it doesn't look like the selected tab. */}
        <BlobLayer color="var(--accent)" seed={3} amplitude={0.09} speed={1.4} stroke={3} />
        <div className="blob-content center"><Icon name="add" size={32} color="var(--accentText)" /></div>
      </Bouncy>
    </div>
  )
}
