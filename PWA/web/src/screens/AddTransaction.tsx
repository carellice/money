import { animate, AnimatePresence, motion } from 'framer-motion'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { today as todayEpoch } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import { argb, paymentMethods, type Category, type PaymentMethod, type Transaction, type TxType } from '../../../shared/model.ts'
import { api } from '../lib/api.ts'
import { pickImage, shrinkToJpeg } from '../lib/images.ts'
import { close, useBackHandler } from '../lib/nav.ts'
import { actions, getStore, useStore } from '../lib/store.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { BlobBox, BlobLayer, PX } from '../components/Blob.tsx'
import { Bouncy, CategoryBadge, Icon, NoteField, Pill, PrimaryButton, RoundIconButton, Segmented, Staggered, TopBar, categoryLabel } from '../components/ui.tsx'
import { AlertDialog, DatePickerDialog, toast } from '../components/overlays.tsx'
import { MemberAvatar } from './Settings.tsx'
import { ThinkingDots } from './Chat.tsx'
import { Expand } from './RecurringEdit.tsx'
import { fastOutSlowIn, spring, tween } from '../components/motion.ts'

export function AddTransactionScreen({ type: initialType, id }: { type: TxType; id: number }) {
  const categories = useStore((s) => s.data?.categories ?? [])
  const members = useStore((s) => s.data?.members ?? [])
  const original = useMemo(() => (id ? getStore().data?.transactions.find((t) => t.id === id) ?? null : null), [id])
  const isEdit = id !== 0

  const [type, setType] = useState<TxType>(original?.type ?? initialType)
  const [amountText, setAmountText] = useState(original ? Money.toInput(original.amountCents) : '')
  const [categoryId, setCategoryId] = useState<number | null>(original?.categoryId ?? null)
  const [subcategoryId, setSubcategoryId] = useState<number | null>(original?.subcategoryId ?? null)
  const [date, setDate] = useState(original?.date ?? todayEpoch())
  const [method, setMethod] = useState<PaymentMethod>(original?.payment ?? (initialType === 'INCOME' ? 'TRANSFER' : 'CARD'))
  const [note, setNote] = useState(original?.note ?? '')
  const [receiptPath, setReceiptPath] = useState<string | null>(original?.receiptPath ?? null)
  const [memberId, setMemberId] = useState<number | null>(original ? original.memberId : getStore().data?.members[0]?.id ?? null)
  const [step, setStepRaw] = useState(isEdit ? 2 : 0)
  const [direction, setDirection] = useState(1)
  const [saved, setSaved] = useState(false)
  const [suggesting, setSuggesting] = useState(false)
  const [suggestion, setSuggestion] = useState<string | null>(null)
  /** Pictures taken in this session, deleted if the movement is not saved. */
  const createdReceipts = useRef<string[]>([])
  const savedRef = useRef(false)

  const setStep = useCallback((next: number) => {
    setStepRaw((current) => {
      setDirection(next > current ? 1 : -1)
      return next
    })
  }, [])

  useEffect(() => () => {
    if (!savedRef.current) createdReceipts.current.forEach(actions.deleteImage)
  }, [])

  useBackHandler(step > 0 && !isEdit, useCallback(() => setStep(step - 1), [step, setStep]))

  const amountCents = Money.parse(amountText) ?? 0
  const accent = type === 'EXPENSE' ? 'var(--expense)' : 'var(--income)'
  /** With people set up, every movement must say whose it is. */
  const memberChosen = members.length === 0 || members.some((m) => m.id === memberId)

  const press = (key: string) => {
    const t = amountText
    if (key === ',') {
      if (!t.includes(',')) setAmountText(t === '' ? '0,' : `${t},`)
      return
    }
    if (t.includes(',')) {
      if (t.split(',')[1].length >= 2) return
    } else if (t.length >= 7) return
    setAmountText(t === '0' ? key : t + key)
  }

  const switchType = (newType: TxType) => {
    if (newType === type) return
    setType(newType)
    setCategoryId(null)
    setSubcategoryId(null)
    setMethod(newType === 'INCOME' ? 'TRANSFER' : 'CARD')
  }

  const selectCategory = (cid: number) => {
    if (categoryId !== cid) setSubcategoryId(null)
    setCategoryId(cid)
  }

  /** Picks category and subcategory from the note: past movements first, then the AI. */
  const suggestCategory = async () => {
    if (!note.trim() || suggesting) return
    setSuggesting(true)
    setSuggestion(null)
    try {
      const { result, connected } = await api<{ result: [number, number | null] | null; connected: boolean }>('/api/ai/suggest', { method: 'POST', json: { note, type } })
      if (!result) {
        setSuggestion(connected ? 'Non sono sicuro: scegli tu la categoria.' : "Nessun movimento simile. Collega l'AI nelle impostazioni per avere suggerimenti.")
      } else {
        selectCategory(result[0])
        setSubcategoryId(result[1])
        setTimeout(() => setStep(2), 450)
      }
    } catch (e) {
      setSuggestion((e as Error).message)
    }
    setSuggesting(false)
  }

  const setReceipt = (path: string | null) => {
    if (receiptPath && createdReceipts.current.includes(receiptPath)) {
      actions.deleteImage(receiptPath)
      createdReceipts.current = createdReceipts.current.filter((p) => p !== receiptPath)
    }
    setReceiptPath(path)
    if (path) createdReceipts.current.push(path)
  }

  const addReceipt = async (camera: boolean) => {
    const file = await pickImage(camera)
    if (!file) return
    const jpeg = await shrinkToJpeg(file, 1800)
    if (!jpeg) return
    try {
      setReceipt(await actions.uploadImage(jpeg, 'receipt'))
    } catch (e) {
      toast((e as Error).message, true)
    }
  }

  const save = async () => {
    if (categoryId == null || amountCents <= 0 || !memberChosen) return
    const tx: Transaction = {
      id,
      type,
      amountCents,
      date,
      categoryId,
      subcategoryId,
      payment: method,
      note: note.trim(),
      receiptPath,
      recurringId: original?.recurringId ?? null,
      createdAt: original?.createdAt ?? Date.now(),
      memberId: members.some((m) => m.id === memberId) ? memberId : null,
    }
    try {
      await actions.saveTransaction(tx)
    } catch (e) {
      toast((e as Error).message, true)
      return
    }
    if (original?.receiptPath && original.receiptPath !== receiptPath) actions.deleteImage(original.receiptPath)
    createdReceipts.current = []
    savedRef.current = true
    setSaved(true)
  }

  const remove = async () => {
    if (original) {
      await actions.deleteTransaction(original)
      actions.discardReceipt(original)
    }
    createdReceipts.current.forEach(actions.deleteImage)
    createdReceipts.current = []
    savedRef.current = true
    close('add')
  }

  const category = categories.find((c) => c.id === categoryId)
  const sub = categories.find((c) => c.id === subcategoryId)

  return (
    <div style={{ position: 'relative', height: '100%' }}>
      <div className="col" style={{ height: '100%' }}>
        <TopBar title={isEdit ? 'Modifica' : type === 'EXPENSE' ? 'Nuova spesa' : 'Nuova entrata'} onBack={() => close('add')}
          actions={<StepDots step={step} accent={accent} />} />
        <Expand visible={step > 0}>
          <div className="row" style={{ padding: '4px 20px', gap: 8 }}>
            <Pill text={Money.format(amountCents)} selected onClick={() => setStep(0)} color={accent} />
            {category && step > 1 && (
              <Pill text={categoryLabel(category, sub)} selected onClick={() => setStep(1)} color={argb(category.color)} appIcon={category.icon} />
            )}
          </div>
        </Expand>
        <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
          <AnimatePresence initial={false} custom={direction}>
            <motion.div
              key={step}
              custom={direction}
              variants={{
                enter: (d: number) => ({ x: `${d * 100}%`, opacity: 0 }),
                center: { x: 0, opacity: 1, transition: { x: spring(0.85, 300), opacity: tween(220) } },
                exit: (d: number) => ({ x: `${(-d * 100) / 3}%`, opacity: 0, transition: { x: tween(250), opacity: tween(200) } }),
              }}
              initial="enter"
              animate="center"
              exit="exit"
              style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column' }}
            >
              {step === 0 && (
                <AmountStep type={type} isEdit={isEdit} accent={accent} amountText={amountText} amountCents={amountCents}
                  onType={switchType} onKey={press} onBackspace={() => setAmountText(amountText.slice(0, -1))}
                  onClear={() => setAmountText('')} onNext={() => setStep(1)} />
              )}
              {step === 1 && (
                <CategoryStep type={type} categories={categories} categoryId={categoryId} subcategoryId={subcategoryId}
                  note={note} setNote={setNote} suggesting={suggesting} suggestion={suggestion} onSuggest={suggestCategory}
                  onSelect={(cid) => {
                    selectCategory(cid)
                    if (!categories.some((c) => c.parentId === cid)) setTimeout(() => setStep(2), 260)
                  }}
                  onSub={(sid) => {
                    setSubcategoryId(sid)
                    if (sid != null) setTimeout(() => setStep(2), 220)
                  }}
                  onNext={() => setStep(2)} />
              )}
              {step === 2 && (
                <DetailsStep type={type} accent={accent} isEdit={isEdit} date={date} setDate={setDate} method={method} setMethod={setMethod}
                  note={note} setNote={setNote} receiptPath={receiptPath} onCamera={() => addReceipt(true)} onGallery={() => addReceipt(false)}
                  onRemoveReceipt={() => setReceipt(null)} memberId={memberId} setMemberId={setMemberId}
                  canSave={amountCents > 0 && categoryId != null && memberChosen} onSave={save} onDelete={remove} />
              )}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
      {saved && <SuccessBurst color={accent} onDone={() => close('add')} />}
    </div>
  )
}

function StepDots({ step, accent }: { step: number; accent: string }) {
  return (
    <div className="row" style={{ gap: 6, paddingRight: 4 }}>
      {[0, 1, 2].map((i) => (
        <motion.div key={i} initial={false} animate={{ width: i === step ? 22 : 8 }} transition={spring(0.6, 1500)}
          style={{ height: 8, borderRadius: 4, background: i <= step ? accent : 'var(--surfaceHigh)', transition: 'background-color 0.25s' }} />
      ))}
    </div>
  )
}

// --- Step 1: amount ----------------------------------------------------------------------------

function AmountStep(p: {
  type: TxType
  isEdit: boolean
  accent: string
  amountText: string
  amountCents: number
  onType: (t: TxType) => void
  onKey: (k: string) => void
  onBackspace: () => void
  onClear: () => void
  onNext: () => void
}) {
  return (
    <div className="col nav-pad" style={{ height: '100%', padding: '0 20px', alignItems: 'center' }}>
      {!p.isEdit && (
        <Segmented options={['Spesa', 'Entrata']} selected={p.type === 'EXPENSE' ? 0 : 1} onSelect={(i) => p.onType(i === 0 ? 'EXPENSE' : 'INCOME')}
          colors={['var(--expense)', 'var(--income)']} style={{ width: '100%', marginTop: 8 }} />
      )}
      <div style={{ flex: 1 }} />
      <span className="t-bodyStrong" style={{ color: 'var(--inkSoft)' }}>{p.type === 'EXPENSE' ? 'Quanto hai speso?' : 'Quanto hai ricevuto?'}</span>
      <BlobBox width="100%" height={170} color={alpha(p.type === 'EXPENSE' ? '#FF8A7A' : '#8FE3C0', 0.22)} seed={5} amplitude={0.1}>
        <AmountDisplay text={p.amountText} />
      </BlobBox>
      <div style={{ flex: 1 }} />
      <Keypad onKey={p.onKey} onBackspace={p.onBackspace} onClear={p.onClear} />
      <div style={{ height: 16 }} />
      <PrimaryButton text="Avanti" onClick={p.onNext} enabled={p.amountCents > 0} color={p.accent} />
      <div style={{ height: 12 }} />
    </div>
  )
}

function AmountDisplay({ text }: { text: string }) {
  const shown = text || '0'
  const size = shown.length > 8 ? 44 : shown.length > 6 ? 52 : 64
  return (
    <div className="row t-hero" style={{ alignItems: 'flex-end', fontSize: size, lineHeight: `${size}px` }}>
      {[...shown].map((ch, i) => <PopChar key={`${i}-${ch}`} ch={ch} still={!text} />)}
      <span style={{ fontSize: size * 0.5, lineHeight: 1.15, color: 'var(--inkSoft)', paddingBottom: 8 }}>&nbsp;€</span>
    </div>
  )
}

function PopChar({ ch, still }: { ch: string; still: boolean }) {
  const ref = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    if (still) return
    const controls = animate(0, 1, {
      ...spring(0.45, 600),
      onUpdate: (v) => {
        const el = ref.current
        if (!el) return
        const s = 0.4 + 0.6 * v
        el.style.transform = `translateY(${(1 - v) * 40 * PX}px) scale(${s})`
        el.style.opacity = String(Math.min(1, Math.max(0, v)))
      },
    })
    return () => controls.stop()
  }, [still])
  return (
    <span ref={ref} style={{ display: 'inline-block', color: still ? 'var(--inkFaint)' : 'var(--ink)', opacity: still ? 1 : 0, transformOrigin: 'center' }}>{ch}</span>
  )
}

function Keypad({ onKey, onBackspace, onClear }: { onKey: (k: string) => void; onBackspace: () => void; onClear: () => void }) {
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', ',', '0', '<']
  return (
    <div className="col" style={{ gap: 10, width: '100%' }}>
      {[0, 1, 2, 3].map((r) => (
        <div key={r} className="row" style={{ gap: 10 }}>
          {keys.slice(r * 3, r * 3 + 3).map((k) => (
            <Bouncy key={k} pressedScale={0.88} onClick={() => (k === '<' ? onBackspace() : onKey(k))} className="center flex1"
              style={{ height: 62, borderRadius: 24, background: 'var(--surface)' }}>
              {k === '<' ? <Icon name="backspace" color="var(--ink)" label="Cancella" /> : <span className="t-title" style={{ color: 'var(--ink)' }}>{k}</span>}
            </Bouncy>
          ))}
        </div>
      ))}
      <div className="t-caption" role="button" onClick={onClear} style={{ textAlign: 'center', color: 'var(--inkFaint)', padding: 4, cursor: 'pointer' }}>Azzera</div>
    </div>
  )
}

// --- Step 2: category --------------------------------------------------------------------------

function CategoryStep(p: {
  type: TxType
  categories: Category[]
  categoryId: number | null
  subcategoryId: number | null
  note: string
  setNote: (v: string) => void
  suggesting: boolean
  suggestion: string | null
  onSuggest: () => void
  onSelect: (id: number) => void
  onSub: (id: number | null) => void
  onNext: () => void
}) {
  const tops = p.categories.filter((c) => c.type === p.type && c.parentId == null)
  const subs = p.categories.filter((c) => c.parentId != null && c.parentId === p.categoryId)
  const selected = tops.find((c) => c.id === p.categoryId)
  const hasNote = !!p.note.trim()
  const color = selected ? argb(selected.color) : 'var(--accent)'
  return (
    <div className="col" style={{ height: '100%' }}>
      <span className="t-title" style={{ color: 'var(--ink)', padding: '8px 20px' }}>In che categoria?</span>
      <div className="row" style={{ padding: '0 20px' }}>
        <NoteField value={p.note} onChange={p.setNote} style={{ flex: 1 }}
          placeholder={p.type === 'EXPENSE' ? 'Cosa hai comprato? Es. Esselunga' : 'Da dove arriva? Es. stipendio'} />
        <div style={{ width: 8 }} />
        <Bouncy onClick={p.onSuggest} disabled={!hasNote} className="center blob-box" style={{ width: 54, height: 54, flexShrink: 0 }} aria-label="Suggerisci categoria">
          <BlobLayer color={hasNote ? 'var(--accent)' : 'var(--surfaceHigh)'} seed={61} amplitude={0.09} animated={p.suggesting} />
          <div className="blob-content center">
            {p.suggesting ? <ThinkingDots color={Ink} style={{ paddingTop: 6 }} /> : <Icon name="auto_awesome" color={hasNote ? Ink : 'var(--inkFaint)'} />}
          </div>
        </Bouncy>
      </div>
      {p.suggestion && <span className="t-caption" style={{ color: 'var(--inkSoft)', padding: '6px 24px' }}>{p.suggestion}</span>}
      <div className="scroll" style={{ padding: '8px 12px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', rowGap: 14 }}>
          {tops.map((cat, index) => (
            <Staggered key={cat.id} index={index} step={25}>
              <CategoryCell cat={cat} selected={cat.id === p.categoryId} onClick={() => p.onSelect(cat.id)} />
            </Staggered>
          ))}
        </div>
      </div>
      <AnimatePresence>
        {subs.length > 0 && (
          <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
            transition={{ height: spring(0.8, 300), opacity: tween(200) }} style={{ overflow: 'hidden', flexShrink: 0 }}>
            <div className="col nav-pad" style={{ borderRadius: '32px 32px 0 0', background: 'var(--surface)', padding: 20 }}>
              <span className="t-label" style={{ color: 'var(--inkSoft)' }}>Più nel dettaglio?</span>
              <div className="flow" style={{ marginTop: 10 }}>
                <Pill text="Generico" selected={p.subcategoryId == null} onClick={() => p.onSub(null)} color={color} />
                {subs.map((s) => <Pill key={s.id} text={s.name} selected={p.subcategoryId === s.id} onClick={() => p.onSub(s.id)} color={color} />)}
              </div>
              <div style={{ height: 16 }} />
              <PrimaryButton text="Avanti" onClick={p.onNext} color={color} />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

function CategoryCell({ cat, selected, onClick }: { cat: Category; selected: boolean; onClick: () => void }) {
  return (
    <Bouncy onClick={onClick} className="col" style={{ alignItems: 'center', padding: '4px 0' }}>
      <motion.div animate={{ scale: selected ? 1.15 : 1 }} transition={spring(0.4, 400)}>
        <CategoryBadge icon={cat.icon} color={argb(cat.color)} size={60} seed={cat.id} animated={selected} />
      </motion.div>
      <span className="t-caption" style={{
        marginTop: 8, textAlign: 'center', color: selected ? 'var(--ink)' : 'var(--inkSoft)', fontWeight: selected ? 700 : 500,
        display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', padding: '0 4px',
      }}>{cat.name}</span>
    </Bouncy>
  )
}

// --- Step 3: details ---------------------------------------------------------------------------

function DetailsStep(p: {
  type: TxType
  accent: string
  isEdit: boolean
  date: number
  setDate: (d: number) => void
  method: PaymentMethod
  setMethod: (m: PaymentMethod) => void
  note: string
  setNote: (v: string) => void
  receiptPath: string | null
  onCamera: () => void
  onGallery: () => void
  onRemoveReceipt: () => void
  memberId: number | null
  setMemberId: (id: number) => void
  canSave: boolean
  onSave: () => void
  onDelete: () => void
}) {
  const members = useStore((s) => s.data?.members ?? [])
  const today = todayEpoch()
  const [showPicker, setShowPicker] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const other = p.date !== today && p.date !== today - 1
  const label = (text: string) => <div className="t-label" style={{ color: 'var(--inkSoft)', paddingTop: 10 }}>{text}</div>

  return (
    <div className="scroll">
      <div className="col nav-pad" style={{ padding: '0 20px', gap: 10 }}>
        <div style={{ height: 4 }} />
        {members.length > 0 && (
          <>
            {label(p.type === 'EXPENSE' ? 'Di chi è la spesa?' : "Di chi è l'entrata?")}
            <div className="flow">
              {members.map((m) => (
                <Pill key={m.id} text={m.name} selected={p.memberId === m.id} onClick={() => p.setMemberId(m.id)} color={argb(m.color)}
                  leading={<MemberAvatar member={m} size={24} />} />
              ))}
            </div>
          </>
        )}

        {label('Quando?')}
        <div className="flow">
          <Pill text="Oggi" selected={p.date === today} onClick={() => p.setDate(today)} color={p.accent} />
          <Pill text="Ieri" selected={p.date === today - 1} onClick={() => p.setDate(today - 1)} color={p.accent} />
          <Pill text={other ? Dates.long(p.date) : 'Altra data'} selected={other} onClick={() => setShowPicker(true)} color={p.accent} icon="calendar_month" />
        </div>

        {label(p.type === 'EXPENSE' ? 'Come hai pagato?' : "Come l'hai ricevuto?")}
        <div className="flow">
          {paymentMethods.map((m) => <Pill key={m.id} text={m.label} selected={p.method === m.id} onClick={() => p.setMethod(m.id)} color={p.accent} appIcon={m.icon} />)}
        </div>

        {label('Nota')}
        <NoteField value={p.note} onChange={p.setNote} placeholder={p.type === 'EXPENSE' ? 'Es. pizza con gli amici' : 'Es. stipendio di ottobre'} />

        {label(p.type === 'EXPENSE' ? 'Scontrino' : 'Ricevuta')}
        <ReceiptPicker path={p.receiptPath} onCamera={p.onCamera} onGallery={p.onGallery} onRemove={p.onRemoveReceipt} />

        <div style={{ height: 12 }} />
        <PrimaryButton text={p.isEdit ? 'Salva modifiche' : 'Salva'} onClick={p.onSave} enabled={p.canSave} color={p.accent} icon="check" />
        {p.isEdit && (
          <div className="t-bodyStrong" role="button" onClick={() => setConfirmDelete(true)}
            style={{ color: 'var(--expenseText)', textAlign: 'center', padding: 14, borderRadius: 999, cursor: 'pointer' }}>Elimina movimento</div>
        )}
        <div style={{ height: 16 }} />
      </div>
      {showPicker && <DatePickerDialog value={p.date} onDismiss={() => setShowPicker(false)} onPick={(d) => { p.setDate(d); setShowPicker(false) }} />}
      {confirmDelete && (
        <AlertDialog title="Eliminare il movimento?" text="L'operazione non si può annullare." confirm="Elimina" confirmColor="var(--expenseText)"
          onConfirm={() => { setConfirmDelete(false); p.onDelete() }} onDismiss={() => setConfirmDelete(false)} />
      )}
    </div>
  )
}

function ReceiptPicker({ path, onCamera, onGallery, onRemove }: { path: string | null; onCamera: () => void; onGallery: () => void; onRemove: () => void }) {
  const [preview, setPreview] = useState(false)
  if (path) {
    return (
      <div style={{ position: 'relative', height: 180, borderRadius: 24, overflow: 'hidden', cursor: 'pointer' }} onClick={() => setPreview(true)}>
        <img src={path} alt="Scontrino" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
        <div style={{ position: 'absolute', top: 10, right: 10 }} onClick={(e) => e.stopPropagation()}>
          <RoundIconButton icon="close" onClick={onRemove} background={alpha(Ink, 0.6)} tint="#FFFFFF" size={36} label="Rimuovi" />
        </div>
        {preview && <ImagePreview src={path} onClose={() => setPreview(false)} />}
      </div>
    )
  }
  return (
    <div className="row" style={{ gap: 10 }}>
      {([['Scatta foto', 'photo_camera', onCamera], ['Galleria', 'image', onGallery]] as const).map(([text, icon, action]) => (
        <Bouncy key={text} onClick={action} className="row flex1" style={{ justifyContent: 'center', borderRadius: 20, background: 'var(--surface)', padding: '18px 0' }}>
          <Icon name={icon} size={20} color="var(--ink)" />
          <span className="t-label" style={{ marginLeft: 8, color: 'var(--ink)' }}>{text}</span>
        </Bouncy>
      ))}
    </div>
  )
}

function ImagePreview({ src, onClose }: { src: string; onClose: () => void }) {
  useBackHandler(true, onClose)
  const target = document.getElementById('overlays')
  if (!target) return null
  return createPortal(
    <div onClick={(e) => { e.stopPropagation(); onClose() }} className="center"
      style={{ position: 'absolute', inset: 0, zIndex: 60, background: 'rgba(0,0,0,0.6)', padding: 24 }}>
      <img src={src} alt="Scontrino" style={{ maxWidth: '100%', maxHeight: '100%', borderRadius: 20 }} />
    </div>,
    target,
  )
}

/** Blob that pops with a ring of particles, then closes the screen. */
function SuccessBurst({ color, onDone }: { color: string; onDone: () => void }) {
  const [pop, setPop] = useState(0)
  const [burst, setBurst] = useState(0)
  useEffect(() => {
    const a = animate(0, 1, { ...spring(0.45, 260), onUpdate: setPop })
    const b = animate(0, 1, {
      duration: 0.65, ease: fastOutSlowIn, onUpdate: setBurst,
      onComplete: () => setTimeout(onDone, 150),
    })
    return () => { a.stop(); b.stop() }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const count = 14
  const radius = 160 * (0.35 + 0.65 * burst)
  return (
    <div className="center" style={{
      position: 'absolute', inset: 0, zIndex: 20,
      background: `color-mix(in srgb, var(--bg) ${Math.round(85 * Math.min(1, Math.max(0, pop)))}%, transparent)`,
    }}>
      <svg width={320} height={320} style={{ position: 'absolute' }}>
        {Array.from({ length: count }, (_, i) => {
          const angle = (2 * Math.PI * i) / count
          return <circle key={i} cx={160 + Math.cos(angle) * radius} cy={160 + Math.sin(angle) * radius}
            r={Math.max(0, (i % 2 === 0 ? 9 : 6) * (1.2 - burst))} fill={color} fillOpacity={1 - burst} />
        })}
      </svg>
      <div style={{ transform: `scale(${pop})` }}>
        <BlobBox size={150} color={color} seed={77} amplitude={0.1}>
          <Icon name="check" size={64} color={Ink} />
        </BlobBox>
      </div>
    </div>
  )
}
