import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { today } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import {
  argb, frequencies, paletteAll, Palette, paymentMethods, type Category, type Frequency, type PaymentMethod, type Recurring, type RecurringKind,
} from '../../../shared/model.ts'
import { brandGroups, findBrand, groupOf, searchBrands, type Brand } from '../lib/brands.ts'
import { pickImage, shrinkToJpeg } from '../lib/images.ts'
import { close } from '../lib/nav.ts'
import { enablePush, pushSupported } from '../lib/push.ts'
import { actions, getStore, useStore } from '../lib/store.ts'
import {
  AmountField, Bouncy, ColorSwatches, FormLabel, IconPicker, NoteField, Pill, PrimaryButton, RecurringLogo, RoundIconButton, Segmented, SwitchRow, TopBar,
} from '../components/ui.tsx'
import { AlertDialog, DatePickerDialog, Sheet, toast } from '../components/overlays.tsx'
import { spring } from '../components/motion.ts'

const reminderOptions: [number, string][] = [[-1, 'No'], [0, 'Il giorno stesso'], [1, '1 giorno prima'], [2, '2 giorni prima'], [3, '3 giorni prima'], [7, '1 settimana prima']]

/** AnimatedVisibility with expandVertically + fadeIn. */
export function Expand({ visible, children }: { visible: boolean; children: ReactNode }) {
  return (
    <AnimatePresence initial={false}>
      {visible && (
        <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }}
          transition={{ height: spring(0.8, 400), opacity: { duration: 0.2 } }} style={{ overflow: 'hidden' }}>
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export function RecurringEditScreen({ id, kind: initialKind }: { id: number; kind: RecurringKind }) {
  const categories = useStore((s) => s.data?.categories ?? [])
  const original = useMemo(() => (id ? getStore().data?.recurring.find((r) => r.id === id) ?? null : null), [id])
  const isEdit = id !== 0

  const [kind, setKind] = useState<RecurringKind>(original?.kind ?? initialKind)
  const [name, setName] = useState(original?.name ?? '')
  const [amountText, setAmountText] = useState(original ? Money.toInput(original.amountCents) : '')
  const [freq, setFreq] = useState<Frequency>(original?.frequency ?? 'MONTHLY')
  const [date, setDate] = useState(original?.nextDate ?? today())
  const [categoryId, setCategoryId] = useState<number | null>(original?.categoryId ?? null)
  const [subcategoryId, setSubcategoryId] = useState<number | null>(original?.subcategoryId ?? null)
  const [method, setMethod] = useState<PaymentMethod>(original?.payment ?? 'CARD')
  const [brandKey, setBrandKey] = useState<string | null>(original?.brandKey ?? null)
  const [imagePath, setImagePath] = useState<string | null>(original?.imagePath ?? null)
  const [icon, setIcon] = useState(original?.icon ?? 'repeat')
  const [color, setColor] = useState(original?.color ?? Palette.LILAC)
  const [autoRegister, setAutoRegister] = useState(original?.autoRegister ?? true)
  const [remindDays, setRemindDays] = useState(original?.remindDaysBefore ?? 2)
  const [active, setActive] = useState(original?.active ?? true)
  const [showBrands, setShowBrands] = useState(false)
  const [showDate, setShowDate] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [nameFocused, setNameFocused] = useState(false)
  const createdImages = useRef<string[]>([])
  const saved = useRef(false)
  const amountCents = Money.parse(amountText) ?? 0

  const applyKindDefaults = (k: RecurringKind, all: Category[]) => {
    const [catName, subName] = k === 'SUBSCRIPTION' ? ['Abbonamenti', 'Streaming'] : ['Casa', 'Affitto']
    const top = all.find((c) => c.parentId == null && c.type === 'EXPENSE' && c.name === catName) ??
      all.find((c) => c.parentId == null && c.type === 'EXPENSE')
    setCategoryId(top?.id ?? null)
    setSubcategoryId(all.find((c) => c.parentId === top?.id && c.name === subName)?.id ?? null)
    setIcon(k === 'SUBSCRIPTION' ? 'repeat' : 'home')
    setColor(k === 'SUBSCRIPTION' ? Palette.LILAC : Palette.PEACH)
  }

  useEffect(() => {
    if (!original) applyKindDefaults(kind, getStore().data?.categories ?? [])
    return () => {
      if (!saved.current) createdImages.current.forEach(actions.deleteImage)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const changeKind = (k: RecurringKind) => {
    if (k === kind) return
    setKind(k)
    if (!isEdit) applyKindDefaults(k, categories)
  }

  const selectCategory = (cid: number) => {
    if (cid !== categoryId) setSubcategoryId(null)
    setCategoryId(cid)
  }

  const setImage = (path: string | null) => {
    if (imagePath && createdImages.current.includes(imagePath)) {
      actions.deleteImage(imagePath)
      createdImages.current = createdImages.current.filter((p) => p !== imagePath)
    }
    setImagePath(path)
    if (path) createdImages.current.push(path)
  }

  /** [replaceName]: always use the brand name, as when picked from the name suggestions. */
  const pickBrand = (brand: Brand | null, replaceName = false) => {
    const previous = findBrand(brandKey)
    if (replaceName || !name.trim() || name === previous?.name) setName(brand?.name ?? '')
    setBrandKey(brand?.key ?? null)
    setImage(null)
    if (brand) suggestSubcategory(brand)
  }

  /** Points the subcategory at the brand family, when the category is still the default one. */
  const suggestSubcategory = (brand: Brand) => {
    const current = categories.find((c) => c.id === categoryId)
    if (!current || current.name !== 'Abbonamenti') return
    const group = groupOf(brand.key)
    const subName = group === 'Streaming' || group === 'Video & social' ? 'Streaming'
      : group === 'Musica & audio' ? 'Musica'
        : group === 'Cloud & software' || group === 'AI' || group === 'Sicurezza' ? 'Software'
          : group === 'Telefonia & casa' ? 'Telefonia' : null
    if (!subName) return
    const sub = categories.find((c) => c.parentId === current.id && c.name === subName)
    if (sub) setSubcategoryId(sub.id)
  }

  const importImage = async () => {
    const file = await pickImage(false)
    if (!file) return
    const jpeg = await shrinkToJpeg(file, 512)
    if (!jpeg) return
    try {
      setImage(await actions.uploadImage(jpeg, 'logo'))
      setBrandKey(null)
    } catch (e) {
      toast((e as Error).message, true)
    }
  }

  const save = async () => {
    if (amountCents <= 0 || categoryId == null) return
    if (remindDays >= 0 && pushSupported() && Notification.permission === 'default') await enablePush()
    const o = original
    const reschedule = !o || o.nextDate !== date || o.frequency !== freq
    const item: Recurring = {
      id,
      kind,
      name: name.trim() || (kind === 'SUBSCRIPTION' ? 'Abbonamento' : 'Spesa fissa'),
      amountCents,
      categoryId,
      subcategoryId,
      payment: method,
      frequency: freq,
      startDate: reschedule || !o ? date : o.startDate,
      nextDate: date,
      brandKey,
      imagePath,
      icon,
      color,
      autoRegister,
      remindDaysBefore: remindDays,
      notifiedFor: reschedule ? null : o?.notifiedFor ?? null,
      active,
      note: o?.note ?? '',
    }
    try {
      await actions.saveRecurring(item)
    } catch (e) {
      toast((e as Error).message, true)
      return
    }
    if (o?.imagePath && o.imagePath !== imagePath) actions.deleteImage(o.imagePath)
    createdImages.current = []
    saved.current = true
    close('recurringEdit')
  }

  const remove = async () => {
    setConfirmDelete(false)
    if (original) await actions.deleteRecurring(original)
    createdImages.current.forEach(actions.deleteImage)
    createdImages.current = []
    saved.current = true
    close('recurringEdit')
  }

  const suggestions = useMemo(
    () => (name.trim() ? searchBrands(name).filter((b) => b.name.toLowerCase() !== name.trim().toLowerCase()).slice(0, 8) : []),
    [name],
  )
  const tops = categories.filter((c) => c.parentId == null && c.type === 'EXPENSE')
  const subs = categories.filter((c) => c.parentId != null && c.parentId === categoryId)
  const subColor = tops.find((c) => c.id === categoryId)
  const logoKey = `${brandKey}|${imagePath}|${icon}|${color}`

  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar
        title={isEdit ? 'Modifica' : kind === 'SUBSCRIPTION' ? 'Nuovo abbonamento' : 'Nuova spesa fissa'}
        onBack={() => close('recurringEdit')}
        actions={isEdit ? <RoundIconButton icon="delete" onClick={() => setConfirmDelete(true)} label="Elimina" /> : undefined}
      />
      <div className="scroll">
        <div className="col nav-pad" style={{ padding: '0 20px' }}>
          <Segmented options={['Abbonamento', 'Spesa fissa']} selected={kind === 'SUBSCRIPTION' ? 0 : 1}
            onSelect={(i) => changeKind(i === 0 ? 'SUBSCRIPTION' : 'FIXED')} />

          <div className="col" style={{ alignItems: 'center', paddingTop: 20 }}>
            <div style={{ position: 'relative', width: 112, height: 112 }}>
              <AnimatePresence initial={false}>
                <motion.div key={logoKey} style={{ position: 'absolute', inset: 0 }}
                  initial={{ scale: 0, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0, opacity: 0 }}
                  transition={{ scale: spring(0.5, 300), opacity: { duration: 0.2 } }}>
                  <RecurringLogo brandKey={brandKey} imagePath={imagePath} icon={icon} color={argb(color)} size={112} animated />
                </motion.div>
              </AnimatePresence>
            </div>
            <div className="flow" style={{ justifyContent: 'center', marginTop: 14 }}>
              <Pill text="Logo preimpostato" selected={brandKey != null} onClick={() => setShowBrands(true)} icon="stars" />
              <Pill text="Carica immagine" selected={imagePath != null} onClick={importImage} icon="image" />
              {(brandKey != null || imagePath != null) && (
                <Pill text="Usa un'icona" selected={false} onClick={() => { setBrandKey(null); setImage(null) }} icon="block" />
              )}
            </div>
          </div>
          <Expand visible={brandKey == null && imagePath == null}>
            <FormLabel text="Icona" />
            <IconPicker selected={icon} color={argb(color)} onSelect={setIcon} />
            <FormLabel text="Colore" />
            <ColorSwatches colors={paletteAll} selected={color} onSelect={setColor} />
          </Expand>

          <FormLabel text="Nome" />
          <NoteField value={name} onChange={setName} placeholder={kind === 'SUBSCRIPTION' ? 'Es. Netflix' : 'Es. Affitto'}
            onFocus={() => setNameFocused(true)} onBlur={() => setTimeout(() => setNameFocused(false), 150)} />
          <Expand visible={nameFocused && kind === 'SUBSCRIPTION' && suggestions.length > 0}>
            <div className="hscroll" style={{ gap: 8, paddingTop: 10 }}>
              {suggestions.map((brand) => (
                <Pill key={brand.key} text={brand.name} selected={false}
                  onClick={() => {
                    pickBrand(brand, true)
                    ;(document.activeElement as HTMLElement | null)?.blur()
                  }}
                  leading={<RecurringLogo brandKey={brand.key} imagePath={null} icon="repeat" color={argb(brand.hex)} size={24} />} />
              ))}
            </div>
          </Expand>

          <FormLabel text="Importo" />
          <AmountField value={amountText} onChange={setAmountText} />

          <FormLabel text="Frequenza" />
          <div className="flow">
            {frequencies.map((f) => <Pill key={f.id} text={f.label} selected={freq === f.id} onClick={() => setFreq(f.id)} />)}
          </div>

          <FormLabel text="Prossimo addebito" />
          <div className="row">
            <Pill text={Dates.long(date).replace(/^./, (c) => c.toUpperCase())} selected onClick={() => setShowDate(true)} icon="calendar_month" />
          </div>
          {date < today() && autoRegister && (
            <span className="t-caption" style={{ color: 'var(--inkSoft)', paddingTop: 6 }}>
              Data passata: gli addebiti già trascorsi verranno registrati nei movimenti.
            </span>
          )}

          <FormLabel text="Categoria" />
          <div className="flow">
            {tops.map((cat) => (
              <Pill key={cat.id} text={cat.name} selected={categoryId === cat.id} onClick={() => selectCategory(cat.id)} color={argb(cat.color)} appIcon={cat.icon} />
            ))}
          </div>
          <Expand visible={subs.length > 0}>
            <div className="flow" style={{ paddingTop: 10 }}>
              <Pill text="Generico" selected={subcategoryId == null} onClick={() => setSubcategoryId(null)} color={subColor ? argb(subColor.color) : 'var(--accent)'} />
              {subs.map((sub) => (
                <Pill key={sub.id} text={sub.name} selected={subcategoryId === sub.id} onClick={() => setSubcategoryId(sub.id)}
                  color={subColor ? argb(subColor.color) : 'var(--accent)'} />
              ))}
            </div>
          </Expand>

          <FormLabel text="Metodo di pagamento" />
          <div className="flow">
            {paymentMethods.map((m) => <Pill key={m.id} text={m.label} selected={method === m.id} onClick={() => setMethod(m.id)} appIcon={m.icon} />)}
          </div>

          <FormLabel text="Promemoria" />
          <div className="flow">
            {reminderOptions.map(([days, label]) => <Pill key={days} text={label} selected={remindDays === days} onClick={() => setRemindDays(days)} />)}
          </div>

          <SwitchRow title="Registra in automatico" subtitle="Il giorno dell'addebito la spesa finisce da sola nei movimenti."
            checked={autoRegister} onChange={setAutoRegister} style={{ marginTop: 18 }} />
          {isEdit && <SwitchRow title="Attiva" subtitle="Metti in pausa senza perdere i dati." checked={active} onChange={setActive} style={{ marginTop: 10 }} />}

          <div style={{ height: 24 }} />
          <PrimaryButton text="Salva" onClick={save} enabled={amountCents > 0 && categoryId != null} icon="check" />
          <div style={{ height: 24 }} />
        </div>
      </div>

      {showBrands && <BrandPickerSheet onDismiss={() => setShowBrands(false)} onPick={(b) => { pickBrand(b); setShowBrands(false) }} />}
      {showDate && <DatePickerDialog value={date} onDismiss={() => setShowDate(false)} onPick={(d) => { setDate(d); setShowDate(false) }} />}
      {confirmDelete && (
        <AlertDialog title={`Eliminare ${name.trim() || 'questa spesa fissa'}?`} text="I movimenti già registrati restano nello storico."
          confirm="Elimina" confirmColor="var(--expenseText)" onConfirm={remove} onDismiss={() => setConfirmDelete(false)} />
      )}
    </div>
  )
}

function BrandPickerSheet({ onDismiss, onPick }: { onDismiss: () => void; onPick: (b: Brand) => void }) {
  const [query, setQuery] = useState('')
  const grid = (brands: Brand[]) => (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(84px, 1fr))' }}>
      {brands.map((b) => <BrandCell key={b.key} brand={b} onPick={onPick} />)}
    </div>
  )
  return (
    <Sheet onDismiss={onDismiss}>
      <div style={{ padding: '0 20px' }}>
        <div className="t-title" style={{ color: 'var(--ink)' }}>Scegli un logo</div>
        <div style={{ height: 12 }} />
        <NoteField value={query} onChange={setQuery} placeholder="Cerca Netflix, Spotify, iCloud…" />
        <div style={{ height: 8 }} />
      </div>
      <div style={{ padding: '0 12px 32px' }}>
        {query.trim()
          ? grid(searchBrands(query))
          : brandGroups.map((g) => (
            <div key={g.name}>
              <div className="t-label" style={{ color: 'var(--inkSoft)', padding: '16px 0 6px 8px' }}>{g.name}</div>
              {grid(g.brands)}
            </div>
          ))}
        <div className="t-caption" style={{ color: 'var(--inkFaint)', textAlign: 'center', padding: '20px 16px 0' }}>
          Loghi da Simple Icons (CC0). I marchi appartengono ai rispettivi proprietari.
        </div>
      </div>
    </Sheet>
  )
}

function BrandCell({ brand, onPick }: { brand: Brand; onPick: (b: Brand) => void }) {
  return (
    <Bouncy onClick={() => onPick(brand)} className="col" style={{ margin: 6, padding: '6px 0', borderRadius: 20, alignItems: 'center' }}>
      <RecurringLogo brandKey={brand.key} imagePath={null} icon="repeat" color={argb(brand.hex)} size={56} />
      <span className="t-caption ellipsis" style={{ marginTop: 6, width: 76, height: 18, textAlign: 'center', color: 'var(--ink)' }}>{brand.name}</span>
    </Bouncy>
  )
}
