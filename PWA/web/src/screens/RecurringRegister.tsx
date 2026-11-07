import { useCallback, useState } from 'react'
import { today as todayEpoch } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import { afterPayment, argb, frequencies, paymentChoice, type Frequency, type Recurring } from '../../../shared/model.ts'
import { occurrence } from '../../../shared/recurrence.ts'
import {
  calcBackspace, calcCents, calcExpression, calcOf, calcOperation, calcPending, calcPress, calcText, emptyCalc,
} from '../lib/amountMath.ts'
import { close, useBackHandler } from '../lib/nav.ts'
import { actions, getStore, useStore } from '../lib/store.ts'
import { FormLabel, Pill, PrimaryButton, RecurringLogo, TopBar } from '../components/ui.tsx'
import { DatePickerDialog, toast } from '../components/overlays.tsx'
import { PaymentPicker } from '../components/Payments.tsx'
import { MemberAvatar } from './Settings.tsx'
import { AmountStep } from './AddTransaction.tsx'

/** "+1 mese", "+3 mesi", "+1 anno": how far the next renewal moves. */
function step(id: Frequency): string {
  const f = frequencies.find((x) => x.id === id)!
  if (f.weeks > 0) return f.weeks === 1 ? '+1 settimana' : `+${f.weeks} settimane`
  if (f.months % 12 === 0) return f.months === 12 ? '+1 anno' : `+${f.months / 12} anni`
  return f.months === 1 ? '+1 mese' : `+${f.months} mesi`
}

/** Adds one payment of a fixed expense to the movements and, if wanted, sets its next renewal. */
export function RecurringRegisterScreen({ id }: { id: number }) {
  const item = useStore((s) => s.data?.recurring.find((r) => r.id === id))
  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar title="Aggiungi alle spese" onBack={() => close('recurringRegister')} />
      {item && <RegisterForm item={item} />}
    </div>
  )
}

function RegisterForm({ item }: { item: Recurring }) {
  const members = useStore((s) => s.data?.members ?? [])
  const today = todayEpoch()
  /** The current billing date moved forward by [period]. */
  const renewal = (period: Frequency) => occurrence(item.nextDate, period, 1)
  // First the amount on the keypad, as when adding a movement from "+", then the rest of the form.
  const [calc, setCalc] = useState(() => calcOf(item.amountCents))
  const [amountStep, setAmountStep] = useState(true)
  useBackHandler(!amountStep, useCallback(() => setAmountStep(true), []))
  const [date, setDate] = useState(today)
  // Defaults to the usual method; this one payment may have gone another way.
  const [method, setMethod] = useState(paymentChoice(item))
  const [memberId, setMemberId] = useState<number | null>(getStore().data?.members[0]?.id ?? null)
  // Manual ones wait for the payment to move on; automatic ones already move by themselves.
  const [next, setNext] = useState<number | null>(item.autoRegister ? null : renewal(item.frequency))
  const [picking, setPicking] = useState<'date' | 'next' | null>(null)
  const cents = calcCents(calc)
  const otherDate = date !== today && date !== today - 1
  const presets = frequencies.map((f) => ({ date: renewal(f.id), label: step(f.id) }))
  const otherNext = next != null && !presets.some((p) => p.date === next)
  const memberChosen = members.length === 0 || members.some((m) => m.id === memberId)

  const save = async () => {
    try {
      await actions.saveTransaction({
        id: 0, type: 'EXPENSE', amountCents: cents, date, categoryId: item.categoryId, subcategoryId: item.subcategoryId,
        payment: method.method, paymentId: method.customId, note: item.name, receiptPath: null, recurringId: item.id,
        createdAt: Date.now(), memberId: members.some((m) => m.id === memberId) ? memberId : null, settle: 0,
      })
      let updated = afterPayment(item)
      // The new date becomes the start of the schedule, so later renewals follow from it.
      if (next != null && next !== item.nextDate) updated = { ...updated, startDate: next, nextDate: next, notifiedFor: null }
      if (updated !== item) await actions.saveRecurring(updated)
    } catch (e) {
      toast((e as Error).message, true)
      return
    }
    toast(`${item.name} aggiunto ai movimenti del ${Dates.long(date)}`)
    close('recurringRegister')
  }

  const header = (
    <div className="row">
      <RecurringLogo brandKey={item.brandKey} imagePath={item.imagePath} icon={item.icon} color={argb(item.color)} size={56} animated />
      <div className="col flex1" style={{ marginLeft: 14, minWidth: 0 }}>
        <span className="t-title ellipsis" style={{ color: 'var(--ink)' }}>{item.name}</span>
        <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>
          {item.installments != null
            ? `Rata ${Math.min(item.installmentsPaid + 1, item.installments)} di ${item.installments} · prevista ${Dates.long(item.nextDate)}`
            : `Rinnovo previsto ${Dates.long(item.nextDate)}`}
        </span>
      </div>
    </div>
  )

  if (amountStep) {
    return (
      <div style={{ flex: 1, minHeight: 0 }}>
        <AmountStep type="EXPENSE" accent="var(--expense)" amountText={calcText(calc)} amountCents={cents}
          expression={calcExpression(calc)} pending={calcPending(calc)} top={<div className="row" style={{ width: '100%', marginTop: 8 }}>{header}</div>}
          onKey={(k) => setCalc(calcPress(calc, k))} onOperation={(op) => setCalc(calcOperation(calc, op))}
          onBackspace={() => setCalc(calcBackspace(calc))} onClear={() => setCalc(emptyCalc)}
          onNext={() => {
            // Moving on keeps only the result of the operations.
            setCalc(calcOf(cents))
            setAmountStep(false)
          }} />
      </div>
    )
  }

  return (
    <div className="scroll">
      <div className="col nav-pad" style={{ padding: '0 20px' }}>
        {header}
        <FormLabel text="Importo" />
        <div className="flow">
          <Pill text={Money.format(cents)} selected onClick={() => setAmountStep(true)} color="var(--expense)" />
        </div>
        {members.length > 0 && (
          <>
            <FormLabel text="Chi ha pagato?" />
            <div className="flow">
              {members.map((m) => (
                <Pill key={m.id} text={m.name} selected={memberId === m.id} onClick={() => setMemberId(m.id)} color={argb(m.color)}
                  leading={<MemberAvatar member={m} size={24} />} />
              ))}
            </div>
          </>
        )}
        <FormLabel text="Data" />
        <div className="flow">
          <Pill text="Oggi" selected={date === today} onClick={() => setDate(today)} />
          <Pill text="Ieri" selected={date === today - 1} onClick={() => setDate(today - 1)} />
          <Pill text={otherDate ? Dates.long(date) : 'Altra data'} selected={otherDate} onClick={() => setPicking('date')} icon="calendar_month" />
        </div>
        <FormLabel text="Metodo di pagamento" />
        <PaymentPicker selected={method} onSelect={setMethod} />

        <FormLabel text={item.installments != null ? 'Prossima rata' : 'Prossimo rinnovo'} />
        <div className="flow">
          <Pill text="Non cambiarlo" selected={next == null} onClick={() => setNext(null)} />
          {presets.map((p) => <Pill key={p.label} text={p.label} selected={next === p.date} onClick={() => setNext(p.date)} />)}
          <Pill text={otherNext ? Dates.long(next!) : 'Altra data'} selected={otherNext} onClick={() => setPicking('next')} icon="calendar_month" />
        </div>
        <span className="t-caption" style={{ color: 'var(--inkFaint)', paddingTop: 12 }}>
          {item.installments != null && item.installmentsPaid + 1 >= item.installments
            ? "È l'ultima rata: il finanziamento risulterà saldato."
            : next == null ? `Il prossimo rinnovo resta il ${Dates.long(item.nextDate)}.` : `Il prossimo rinnovo sarà il ${Dates.long(next)}.`}
        </span>

        <div style={{ height: 24 }} />
        <PrimaryButton text={`Aggiungi ${Money.format(cents)}`} onClick={save} enabled={cents > 0 && memberChosen} icon="check" />
        <div style={{ height: 24 }} />
      </div>
      {picking && (
        <DatePickerDialog value={picking === 'date' ? date : next ?? item.nextDate} onDismiss={() => setPicking(null)}
          onPick={(d) => { if (picking === 'date') setDate(d); else setNext(d); setPicking(null) }} />
      )}
    </div>
  )
}
