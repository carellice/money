import { AnimatePresence, motion, useAnimationControls, useDragControls } from 'framer-motion'
import { useEffect, useState, useSyncExternalStore, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { dayOfWeek, epochOf, monthDays, monthOf, monthStart, monthYear, today, ymd } from '../../../shared/date.ts'
import { Dates } from '../../../shared/format.ts'
import { useBackHandler } from '../lib/nav.ts'
import { Icon } from './ui.tsx'
import { spring, tween } from './motion.ts'

function Portal({ children }: { children: ReactNode }) {
  const target = document.getElementById('overlays')
  return target ? createPortal(children, target) : null
}

// --- bottom sheet ----------------------------------------------------------------------------

/** ModalBottomSheet, always fully expanded; drag down, tap outside or go back to close. */
export function Sheet({ onDismiss, children }: { onDismiss: () => void; children: ReactNode }) {
  const controls = useAnimationControls()
  const drag = useDragControls()
  const [closing, setClosing] = useState(false)

  useEffect(() => {
    controls.start({ y: 0, transition: spring(1, 400) })
  }, [controls])

  const dismiss = async () => {
    if (closing) return
    setClosing(true)
    await controls.start({ y: '100%', transition: tween(250) })
    onDismiss()
  }
  useBackHandler(true, dismiss)

  return (
    <Portal>
      <motion.div className="scrim" initial={{ opacity: 0 }} animate={{ opacity: closing ? 0 : 1 }} transition={tween(250)} onClick={dismiss} />
      <motion.div
        initial={{ y: '100%' }}
        animate={controls}
        drag="y"
        dragControls={drag}
        dragListener={false}
        dragConstraints={{ top: 0, bottom: 0 }}
        dragElastic={{ top: 0, bottom: 1 }}
        onDragEnd={(_, info) => {
          if (info.offset.y > 120 || info.velocity.y > 600) dismiss()
          else controls.start({ y: 0, transition: spring(1, 400) })
        }}
        style={{
          position: 'absolute', left: 0, right: 0, bottom: 0, zIndex: 51, maxHeight: 'calc(100% - var(--safe-top) - 24px)',
          background: 'var(--bg)', borderRadius: '28px 28px 0 0', display: 'flex', flexDirection: 'column',
        }}
      >
        <div onPointerDown={(e) => drag.start(e)} style={{ padding: '22px 0', touchAction: 'none', cursor: 'grab', flexShrink: 0 }}>
          <div style={{ width: 32, height: 4, borderRadius: 2, margin: '0 auto', background: 'var(--inkSoft)', opacity: 0.4 }} />
        </div>
        <div className="scroll nav-pad" style={{ flex: '0 1 auto' }}>{children}</div>
      </motion.div>
    </Portal>
  )
}

// --- dialogs ---------------------------------------------------------------------------------

export function AlertDialog({ title, text, confirm, confirmColor, onConfirm, onDismiss, dismissText = 'Annulla' }: {
  title: string
  text: string
  confirm: string
  confirmColor?: string
  onConfirm: () => void
  onDismiss: () => void
  dismissText?: string
}) {
  useBackHandler(true, onDismiss)
  return (
    <Portal>
      <motion.div className="scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} onClick={onDismiss} />
      <motion.div className="dialog" initial={{ opacity: 0, scale: 0.9, x: '-50%', y: '-50%' }} animate={{ opacity: 1, scale: 1, x: '-50%', y: '-50%' }}
        transition={spring(0.9, 700)} style={{ transform: undefined, left: '50%', top: '50%' }}>
        <h2>{title}</h2>
        <p>{text}</p>
        <div className="dialog-actions">
          <button className="text-button" onClick={onDismiss}>{dismissText}</button>
          <button className="text-button" style={{ color: confirmColor }} onClick={onConfirm}>{confirm}</button>
        </div>
      </motion.div>
    </Portal>
  )
}

const weekdayLetters = ['L', 'M', 'M', 'G', 'V', 'S', 'D']

/** Material 3 DatePickerDialog. */
export function DatePickerDialog({ value, onPick, onDismiss }: { value: number; onPick: (epoch: number) => void; onDismiss: () => void }) {
  const [selected, setSelected] = useState(value)
  const [month, setMonth] = useState(monthOf(value))
  const now = today()
  useBackHandler(true, onDismiss)
  const offset = dayOfWeek(monthStart(month)) - 1
  const days = monthDays(month)
  const { y, m } = monthYear(month)
  const cells = Array.from({ length: offset + days }, (_, i) => (i < offset ? null : i - offset + 1))
  const sel = ymd(selected)

  return (
    <Portal>
      <motion.div className="scrim" initial={{ opacity: 0 }} animate={{ opacity: 1 }} onClick={onDismiss} />
      <motion.div className="dialog" initial={{ opacity: 0, scale: 0.9, x: '-50%', y: '-50%' }} animate={{ opacity: 1, scale: 1, x: '-50%', y: '-50%' }}
        transition={spring(0.9, 700)} style={{ width: 'min(360px, calc(100% - 32px))', padding: 0, transform: undefined }}>
        <div style={{ padding: '16px 12px 12px 24px' }}>
          <div className="t-label" style={{ color: 'var(--inkSoft)', fontWeight: 500 }}>Seleziona data</div>
          <div style={{ fontSize: 32, lineHeight: '40px', marginTop: 36, fontVariationSettings: "'opsz' 72" }}>
            {Dates.short(selected).replace(/^\w/, (c) => c.toUpperCase())} {sel.y !== ymd(now).y ? sel.y : ''}
          </div>
        </div>
        <div style={{ height: 1, background: 'var(--line)' }} />
        <div className="row" style={{ padding: '8px 12px 8px 24px' }}>
          <span className="t-label flex1" style={{ color: 'var(--inkSoft)' }}>{Dates.monthName(month).toLowerCase()} {y}</span>
          <button className="center" style={{ width: 40, height: 40, borderRadius: '50%' }} onClick={() => setMonth(month - 1)} aria-label="Mese precedente">
            <Icon name="chevron_left" size={24} color="var(--inkSoft)" />
          </button>
          <button className="center" style={{ width: 40, height: 40, borderRadius: '50%' }} onClick={() => setMonth(month + 1)} aria-label="Mese successivo">
            <Icon name="chevron_right" size={24} color="var(--inkSoft)" />
          </button>
        </div>
        <div style={{ padding: '0 12px', display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', rowGap: 2 }}>
          {weekdayLetters.map((d, i) => (
            <div key={i} className="center t-caption" style={{ height: 40, color: 'var(--ink)' }}>{d}</div>
          ))}
          {cells.map((d, i) => {
            if (d == null) return <div key={i} />
            const epoch = epochOf(y, m, d)
            const isSelected = epoch === selected
            const isToday = epoch === now
            return (
              <div key={i} className="center" style={{ height: 48 }}>
                <button onClick={() => setSelected(epoch)} className="center" style={{
                  width: 40, height: 40, borderRadius: '50%', fontSize: 15,
                  background: isSelected ? 'var(--pickerPrimary)' : 'transparent',
                  color: isSelected ? 'var(--pickerOnPrimary)' : isToday ? 'var(--pickerPrimary)' : 'var(--ink)',
                  boxShadow: isToday && !isSelected ? 'inset 0 0 0 1px var(--pickerPrimary)' : 'none',
                }}>{d}</button>
              </div>
            )
          })}
        </div>
        <div className="dialog-actions" style={{ margin: 0, padding: '8px 12px 12px' }}>
          <button className="text-button" style={{ color: 'var(--pickerPrimary)' }} onClick={onDismiss}>Annulla</button>
          <button className="text-button" style={{ color: 'var(--pickerPrimary)' }} onClick={() => onPick(selected)}>OK</button>
        </div>
      </motion.div>
    </Portal>
  )
}

// --- toast and snackbar ----------------------------------------------------------------------

type ToastState = { id: number; text: string } | null
let toastState: ToastState = null
type SnackState = { id: number; text: string; action: string; resolve: (performed: boolean) => void } | null
let snackState: SnackState = null
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((l) => l())
let counter = 0

/** Android Toast: a short message at the bottom of the screen. */
export function toast(text: string, long = false) {
  const id = ++counter
  toastState = { id, text }
  emit()
  setTimeout(() => {
    if (toastState?.id === id) {
      toastState = null
      emit()
    }
  }, long ? 3500 : 2000)
}

/** Snackbar with one action; resolves true when the action was tapped. */
export function showSnackbar(text: string, action: string): Promise<boolean> {
  snackState?.resolve(false)
  return new Promise((resolve) => {
    const id = ++counter
    snackState = { id, text, action, resolve }
    emit()
    setTimeout(() => {
      if (snackState?.id === id) {
        snackState = null
        emit()
        resolve(false)
      }
    }, 4000)
  })
}

function useMessages() {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => (toastState?.id ?? 0) * 100000 + (snackState?.id ?? 0),
  )
}

export function MessagesHost() {
  useMessages()
  const t = toastState
  const s = snackState
  return (
    <>
      <AnimatePresence>
        {t && (
          <motion.div key={t.id} className="toast" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={tween(200)}>
            {t.text}
          </motion.div>
        )}
      </AnimatePresence>
      <AnimatePresence>
        {s && (
          <motion.div key={s.id} className="snackbar" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={tween(250)}>
            <span className="flex1">{s.text}</span>
            <button onClick={() => {
              snackState = null
              emit()
              s.resolve(true)
            }}>{s.action}</button>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}
