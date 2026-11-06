import { useMemo, useState } from 'react'
import { today as todayEpoch } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import {
  argb, frequency, installmentsLeft, monthlyCents, Palette, paymentChoice, paymentLabel, recurringSorts, sameChoice,
  type PaymentChoice, type Recurring, type RecurringKind,
} from '../../../shared/model.ts'
import { navigate } from '../lib/nav.ts'
import { updateSettings, usePaymentMethods, useStore } from '../lib/store.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { BlobLayer } from '../components/Blob.tsx'
import {
  AnimatedAmount, AppIcon, Bouncy, EmptyState, Icon, Pill, RecurringLogo, RoundIconButton, Staggered,
} from '../components/ui.tsx'

export function RecurringScreen() {
  const data = useStore((s) => s.data)
  const [kind, setKind] = useState<RecurringKind | null>(null)
  const [choosingSort, setChoosingSort] = useState(false)
  const sort = useStore((s) => s.settings.recurringSort)
  const custom = usePaymentMethods()

  const list = useMemo(() => {
    const label = (r: Recurring) => paymentLabel(paymentChoice(r), custom).label
    const order = (a: Recurring, b: Recurring): number => {
      switch (sort) {
        case 'NAME': return a.name.localeCompare(b.name, 'it', { sensitivity: 'base' })
        case 'COST_HIGH': return monthlyCents(b.frequency, b.amountCents) - monthlyCents(a.frequency, a.amountCents)
        case 'COST_LOW': return monthlyCents(a.frequency, a.amountCents) - monthlyCents(b.frequency, b.amountCents)
        case 'PAYMENT': return label(a).localeCompare(label(b), 'it', { sensitivity: 'base' }) || a.nextDate - b.nextDate
        default: return a.nextDate - b.nextDate
      }
    }
    return [...(data?.recurring ?? [])].sort((a, b) => Number(b.active) - Number(a.active) || order(a, b))
  }, [data, sort, custom])
  if (!data) return null
  const visible = kind == null ? list : list.filter((r) => r.kind === kind)
  const active = list.filter((r) => r.active)
  const monthly = active.reduce((acc, r) => acc + monthlyCents(r.frequency, r.amountCents), 0)
  const subscriptions = active.filter((r) => r.kind === 'SUBSCRIPTION').reduce((acc, r) => acc + monthlyCents(r.frequency, r.amountCents), 0)

  return (
    <div className="scroll">
      <div className="col" style={{ padding: '0 20px 156px', gap: 12 }}>
        <div className="row status-pad">
          <div className="row" style={{ paddingTop: 12, width: '100%' }}>
            <span className="t-display flex1" style={{ color: 'var(--ink)' }}>Spese fisse</span>
            <RoundIconButton icon="add" onClick={() => navigate('recurringEdit', kind == null ? {} : { kind })} background="var(--accent)" tint={Ink} label="Aggiungi" />
          </div>
        </div>
        <Staggered index={0} className="center blob-box" style={{ height: 190 }}>
          <BlobLayer gradient={['var(--accent)', argb(Palette.SKY)]} seed={404} amplitude={0.05} speed={0.6} />
          <div className="blob-content col" style={{ alignItems: 'center' }}>
            <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>ogni mese, in media</span>
            <AnimatedAmount cents={monthly} className="t-display" color={Ink} decimalsScale={0.45} masked={false} />
            <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>
              {Money.short(monthly * 12)} l'anno · abbonamenti {Money.short(subscriptions)}/mese
            </span>
          </div>
        </Staggered>
        {active.length > 0 && (
          <Staggered index={1}>
            <PaymentsEntry active={active} />
          </Staggered>
        )}
        <Staggered index={1}>
          <KindChips items={list} selected={kind} onSelect={setKind} />
        </Staggered>
        {visible.length > 1 && (
          <Staggered index={1} className="row">
            <span className="t-caption flex1" style={{ color: 'var(--inkSoft)' }}>Ordina per</span>
            <div style={{ position: 'relative' }}>
              <Pill text={recurringSorts.find((o) => o.id === sort)?.label ?? 'Prossimo addebito'} selected={false}
                onClick={() => setChoosingSort(true)} icon="swap_vert" />
              {choosingSort && (
                <>
                  <div style={{ position: 'fixed', inset: 0, zIndex: 40 }} onClick={() => setChoosingSort(false)} />
                  <div className="col" style={{
                    position: 'absolute', right: 0, top: 'calc(100% + 6px)', zIndex: 41, minWidth: 220, padding: '6px 0',
                    background: 'var(--surface)', borderRadius: 18, boxShadow: '0 8px 28px rgba(0, 0, 0, 0.18)',
                  }}>
                    {recurringSorts.map((o) => (
                      <Bouncy key={o.id} pressedScale={0.98} className="row" style={{ padding: '12px 16px' }}
                        onClick={() => { updateSettings({ recurringSort: o.id }); setChoosingSort(false) }}>
                        <span className="t-label flex1" style={{ color: 'var(--ink)' }}>{o.label}</span>
                        {o.id === sort && <Icon name="check" size={20} color="var(--accentText)" />}
                      </Bouncy>
                    ))}
                  </div>
                </>
              )}
            </div>
          </Staggered>
        )}
        {visible.length === 0 && (
          <EmptyState title="Niente qui" icon="repeat"
            text={kind === 'FINANCING' ? 'Aggiungi un acquisto a rate, come Klarna: Money conta le rate pagate e quelle che mancano.' : "Aggiungi Netflix, l'affitto o la palestra: Money li registrerà da solo e ti avviserà prima del rinnovo."} />
        )}
        {visible.map((item) => (
          <Staggered key={item.id} index={2}>
            <RecurringCard item={item} onLongClick={() => navigate('recurringRegister', { id: item.id })} />
          </Staggered>
        ))}
        {visible.length > 0 && (
          <span className="t-caption" style={{ color: 'var(--inkFaint)', textAlign: 'center', paddingTop: 4 }}>
            Tieni premuto per aggiungere subito una spesa ai movimenti.
          </span>
        )}
      </div>
    </div>
  )
}

const kinds: { id: RecurringKind; label: string }[] = [
  { id: 'SUBSCRIPTION', label: 'Abbonamenti' }, { id: 'FIXED', label: 'Spese fisse' }, { id: 'FINANCING', label: 'Rate' },
]

/** Filters the list by type, with how many items each one has; long names never get cut. */
export function KindChips({ items, selected, onSelect }: { items: Recurring[]; selected: RecurringKind | null; onSelect: (k: RecurringKind | null) => void }) {
  return (
    <div className="hscroll" style={{ gap: 8 }}>
      <Pill text={`Tutte ${items.length}`} selected={selected == null} onClick={() => onSelect(null)} />
      {kinds.map((k) => (
        <Pill key={k.id} text={`${k.label} ${items.filter((r) => r.kind === k.id).length}`} selected={selected === k.id} onClick={() => onSelect(k.id)} />
      ))}
    </div>
  )
}

/** Opens the split by payment method, with a hint of what is inside. */
function PaymentsEntry({ active }: { active: Recurring[] }) {
  const custom = usePaymentMethods()
  const totals: { choice: PaymentChoice; cents: number }[] = []
  for (const r of active) {
    const choice = paymentChoice(r)
    const known = totals.find((t) => sameChoice(t.choice, choice))
    if (known) known.cents += monthlyCents(r.frequency, r.amountCents)
    else totals.push({ choice, cents: monthlyCents(r.frequency, r.amountCents) })
  }
  const top = [...totals].sort((a, b) => b.cents - a.cents)[0]
  return (
    <Bouncy pressedScale={0.97} onClick={() => navigate('recurringPayments')} className="row"
      style={{ borderRadius: 28, background: 'var(--surface)', padding: '14px 16px' }}>
      <div className="center" style={{ width: 42, height: 42, borderRadius: 21, flexShrink: 0, background: 'color-mix(in srgb, var(--accent) 22%, transparent)' }}>
        <AppIcon name="wallet" size={22} color="var(--ink)" />
      </div>
      <div className="col flex1" style={{ marginLeft: 14, minWidth: 0 }}>
        <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>Per metodo di pagamento</span>
        <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)' }}>
          {totals.length === 1 ? '1 metodo' : `${totals.length} metodi`}{top ? ` · il più usato è ${paymentLabel(top.choice, custom).label}` : ''}
        </span>
      </div>
      <Icon name="chevron_right" color="var(--inkFaint)" />
    </Bouncy>
  )
}

function RecurringCard({ item, onLongClick }: { item: Recurring; onLongClick: () => void }) {
  const days = item.nextDate - todayEpoch()
  const left = installmentsLeft(item)
  const method = paymentLabel(paymentChoice(item), usePaymentMethods())
  return (
    <Bouncy pressedScale={0.97} onLongClick={onLongClick} onClick={() => navigate('recurringEdit', { id: item.id })} className="row"
      style={{ borderRadius: 28, background: 'var(--surface)', padding: 14 }}>
      <div className="row flex1" style={{ opacity: item.active ? 1 : 0.5 }}>
        <RecurringLogo brandKey={item.brandKey} imagePath={item.imagePath} icon={item.icon} color={argb(item.color)} size={56} />
        <div className="col flex1" style={{ marginLeft: 14 }}>
          <span className="t-bodyStrong ellipsis" style={{ color: 'var(--ink)' }}>{item.name}</span>
          <div className="row" style={{ minWidth: 0 }}>
            <AppIcon name={method.icon} size={13} color="var(--inkFaint)" />
            <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)', marginLeft: 4 }}>{method.label}</span>
          </div>
          <div className="row" style={{ minWidth: 0 }}>
            {item.autoRegister && <Icon name="autorenew" size={13} color="var(--inkFaint)" />}
            {item.remindDaysBefore >= 0 && <Icon name="notifications_active" size={13} color="var(--inkFaint)" />}
            <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)' }}>
              {item.active ? ` ${Dates.long(item.nextDate)} · ${Dates.relative(item.nextDate)}` : left === 0 ? ' Saldato' : ' In pausa'}
            </span>
          </div>
          {item.installments != null && left !== 0 && (
            <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>
              {left === 1 ? 'Rimane 1 rata' : `Rimangono ${left} rate`} su {item.installments}
            </span>
          )}
        </div>
        <div className="col" style={{ alignItems: 'flex-end' }}>
          <span className="t-amount" style={{ color: 'var(--ink)', whiteSpace: 'nowrap' }}>{Money.format(item.amountCents)}</span>
          <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>{frequency(item.frequency).short}</span>
          {item.active && days <= 3 && (
            <span className="t-caption" style={{ marginTop: 4, color: Ink, background: 'var(--expense)', borderRadius: 999, padding: '2px 8px' }}>{days < 0 ? 'da pagare' : 'presto'}</span>
          )}
        </div>
      </div>
    </Bouncy>
  )
}
