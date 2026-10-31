import { useMemo, useState } from 'react'
import { today as todayEpoch } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import { argb, frequency, monthlyCents, paymentMethods, Palette, type PaymentMethod, type Recurring } from '../../../shared/model.ts'
import { navigate } from '../lib/nav.ts'
import { actions, useStore } from '../lib/store.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { BlobLayer } from '../components/Blob.tsx'
import {
  AmountField, AnimatedAmount, Bouncy, EmptyState, FormLabel, Icon, Pill, PrimaryButton, RecurringLogo, RoundIconButton, Segmented, Staggered,
} from '../components/ui.tsx'
import { DatePickerDialog, Sheet, toast } from '../components/overlays.tsx'

export function RecurringScreen() {
  const data = useStore((s) => s.data)
  const [tab, setTab] = useState(0)
  const [registering, setRegistering] = useState<Recurring | null>(null)

  const list = useMemo(
    () => [...(data?.recurring ?? [])].sort((a, b) => Number(b.active) - Number(a.active) || a.nextDate - b.nextDate),
    [data],
  )
  if (!data) return null
  const visible = tab === 1 ? list.filter((r) => r.kind === 'SUBSCRIPTION') : tab === 2 ? list.filter((r) => r.kind === 'FIXED') : list
  const active = list.filter((r) => r.active)
  const monthly = active.reduce((acc, r) => acc + monthlyCents(r.frequency, r.amountCents), 0)
  const subscriptions = active.filter((r) => r.kind === 'SUBSCRIPTION').reduce((acc, r) => acc + monthlyCents(r.frequency, r.amountCents), 0)

  return (
    <div className="scroll">
      <div className="col" style={{ padding: '0 20px 156px', gap: 12 }}>
        <div className="row status-pad">
          <div className="row" style={{ paddingTop: 12, width: '100%' }}>
            <span className="t-display flex1" style={{ color: 'var(--ink)' }}>Spese fisse</span>
            <RoundIconButton icon="add" onClick={() => navigate('recurringEdit')} background="var(--accent)" tint={Ink} label="Aggiungi" />
          </div>
        </div>
        <Staggered index={0} className="center blob-box" style={{ height: 190 }}>
          <BlobLayer gradient={['var(--accent)', argb(Palette.SKY)]} seed={404} amplitude={0.05} speed={0.6} />
          <div className="blob-content col" style={{ alignItems: 'center' }}>
            <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>ogni mese, in media</span>
            <AnimatedAmount cents={monthly} className="t-display" color={Ink} decimalsScale={0.45} />
            <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>
              {Money.short(monthly * 12)} l'anno · abbonamenti {Money.short(subscriptions)}/mese
            </span>
          </div>
        </Staggered>
        <Staggered index={1}>
          <Segmented options={['Tutte', 'Abbonamenti', 'Fisse']} selected={tab} onSelect={setTab} />
        </Staggered>
        {visible.length === 0 && (
          <EmptyState title="Niente qui" icon="repeat"
            text="Aggiungi Netflix, l'affitto o la palestra: Money li registrerà da solo e ti avviserà prima del rinnovo." />
        )}
        {visible.map((item) => (
          <Staggered key={item.id} index={2}>
            <RecurringCard item={item} onLongClick={() => setRegistering(item)} />
          </Staggered>
        ))}
        {visible.length > 0 && (
          <span className="t-caption" style={{ color: 'var(--inkFaint)', textAlign: 'center', paddingTop: 4 }}>
            Tieni premuto per aggiungere subito una spesa ai movimenti.
          </span>
        )}
      </div>
      {registering && (
        <RegisterNowSheet item={registering} onDismiss={() => setRegistering(null)} onConfirm={async (cents, date, method) => {
          const item = registering
          await actions.saveTransaction({
            id: 0, type: 'EXPENSE', amountCents: cents, date, categoryId: item.categoryId, subcategoryId: item.subcategoryId,
            payment: method, note: item.name, receiptPath: null, recurringId: item.id, createdAt: Date.now(), memberId: null,
          })
          setRegistering(null)
          toast(`${item.name} aggiunto ai movimenti del ${Dates.long(date)}`)
        }} />
      )}
    </div>
  )
}

function RegisterNowSheet({ item, onDismiss, onConfirm }: {
  item: Recurring
  onDismiss: () => void
  onConfirm: (cents: number, date: number, payment: PaymentMethod) => void
}) {
  const today = todayEpoch()
  const [amount, setAmount] = useState(Money.toInput(item.amountCents))
  const [date, setDate] = useState(today)
  // Defaults to the usual method; this one payment may have gone another way.
  const [method, setMethod] = useState(item.payment)
  const [showPicker, setShowPicker] = useState(false)
  const cents = Money.parse(amount) ?? 0
  const other = date !== today && date !== today - 1

  return (
    <Sheet onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 28px' }}>
        <div className="row">
          <RecurringLogo brandKey={item.brandKey} imagePath={item.imagePath} icon={item.icon} color={argb(item.color)} size={56} animated />
          <div className="col flex1" style={{ marginLeft: 14 }}>
            <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>Aggiungi alle spese</span>
            <span className="t-title ellipsis" style={{ color: 'var(--ink)' }}>{item.name}</span>
          </div>
        </div>
        <FormLabel text="Importo" />
        <AmountField value={amount} onChange={setAmount} />
        <FormLabel text="Data" />
        <div className="flow">
          <Pill text="Oggi" selected={date === today} onClick={() => setDate(today)} />
          <Pill text="Ieri" selected={date === today - 1} onClick={() => setDate(today - 1)} />
          <Pill text={other ? Dates.long(date) : 'Altra data'} selected={other} onClick={() => setShowPicker(true)} icon="calendar_month" />
        </div>
        <FormLabel text="Metodo di pagamento" />
        <div className="flow">
          {paymentMethods.map((m) => <Pill key={m.id} text={m.label} selected={method === m.id} onClick={() => setMethod(m.id)} appIcon={m.icon} />)}
        </div>
        <span className="t-caption" style={{ color: 'var(--inkFaint)', paddingTop: 12 }}>
          Il prossimo addebito programmato ({Dates.long(item.nextDate)}) resta invariato.
        </span>
        <div style={{ height: 20 }} />
        <PrimaryButton text={`Aggiungi ${Money.format(cents)}`} onClick={() => onConfirm(cents, date, method)} enabled={cents > 0} icon="check" />
      </div>
      {showPicker && <DatePickerDialog value={date} onDismiss={() => setShowPicker(false)} onPick={(d) => { setDate(d); setShowPicker(false) }} />}
    </Sheet>
  )
}

function RecurringCard({ item, onLongClick }: { item: Recurring; onLongClick: () => void }) {
  const days = item.nextDate - todayEpoch()
  return (
    <Bouncy pressedScale={0.97} onLongClick={onLongClick} onClick={() => navigate('recurringEdit', { id: item.id })} className="row"
      style={{ borderRadius: 28, background: 'var(--surface)', padding: 14 }}>
      <div className="row flex1" style={{ opacity: item.active ? 1 : 0.5 }}>
        <RecurringLogo brandKey={item.brandKey} imagePath={item.imagePath} icon={item.icon} color={argb(item.color)} size={56} />
        <div className="col flex1" style={{ marginLeft: 14 }}>
          <span className="t-bodyStrong ellipsis" style={{ color: 'var(--ink)' }}>{item.name}</span>
          <div className="row" style={{ minWidth: 0 }}>
            {item.autoRegister && <Icon name="autorenew" size={13} color="var(--inkFaint)" />}
            {item.remindDaysBefore >= 0 && <Icon name="notifications_active" size={13} color="var(--inkFaint)" />}
            <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)' }}>
              {item.active ? ` ${Dates.long(item.nextDate)} · ${Dates.relative(item.nextDate)}` : ' In pausa'}
            </span>
          </div>
        </div>
        <div className="col" style={{ alignItems: 'flex-end' }}>
          <span className="t-amount" style={{ color: 'var(--ink)', whiteSpace: 'nowrap' }}>{Money.format(item.amountCents)}</span>
          <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>{frequency(item.frequency).short}</span>
          {item.active && days >= 0 && days <= 3 && (
            <span className="t-caption" style={{ marginTop: 4, color: Ink, background: 'var(--expense)', borderRadius: 999, padding: '2px 8px' }}>presto</span>
          )}
        </div>
      </div>
    </Bouncy>
  )
}
