import { useMemo, useState } from 'react'
import { today as todayEpoch } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import {
  argb, frequency, installmentsLeft, monthlyCents, Palette, paymentChoice, paymentLabel, recurringSorts, sameChoice, yearlyCents,
  type PaymentChoice, type Recurring, type RecurringKind,
} from '../../../shared/model.ts'
import { navigate } from '../lib/nav.ts'
import { updateSettings, usePaymentMethods, useStore } from '../lib/store.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { BlobLayer } from '../components/Blob.tsx'
import {
  AnimatedAmount, AppIcon, Bouncy, EmptyState, Icon, Pill, RecurringLogo, RoundIconButton, SectionTitle,
  Segmented, SoftCard, SoftProgress, Staggered,
} from '../components/ui.tsx'
import { Sheet } from '../components/overlays.tsx'

export function RecurringScreen() {
  const data = useStore((s) => s.data)
  const [tab, setTab] = useState(0)
  const [yearly, setYearly] = useState(false)
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
  const visible = tab === 0 ? list : list.filter((r) => r.kind === tabKinds[tab - 1])
  const active = list.filter((r) => r.active)
  const monthly = active.reduce((acc, r) => acc + monthlyCents(r.frequency, r.amountCents), 0)
  const subscriptions = active.filter((r) => r.kind === 'SUBSCRIPTION').reduce((acc, r) => acc + monthlyCents(r.frequency, r.amountCents), 0)

  return (
    <div className="scroll">
      <div className="col" style={{ padding: '0 20px 156px', gap: 12 }}>
        <div className="row status-pad">
          <div className="row" style={{ paddingTop: 12, width: '100%' }}>
            <span className="t-display flex1" style={{ color: 'var(--ink)' }}>Spese fisse</span>
            <RoundIconButton icon="add" onClick={() => navigate('recurringEdit', tab === 0 ? {} : { kind: tabKinds[tab - 1] })} background="var(--accent)" tint={Ink} label="Aggiungi" />
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
          <Segmented options={['Tutte', 'Abbonamenti', 'Fisse', 'Rate']} selected={tab} onSelect={setTab} />
        </Staggered>
        {visible.length > 1 && (
          <Staggered index={1} className="row">
            <span className="t-caption flex1" style={{ color: 'var(--inkSoft)' }}>Ordina per</span>
            <Pill text={recurringSorts.find((o) => o.id === sort)?.label ?? 'Prossimo addebito'} selected={false}
              onClick={() => setChoosingSort(true)} icon="swap_vert" />
          </Staggered>
        )}
        {visible.length === 0 && (
          <EmptyState title="Niente qui" icon="repeat"
            text={tab === 3 ? 'Aggiungi un acquisto a rate, come Klarna: Money conta le rate pagate e quelle che mancano.' : "Aggiungi Netflix, l'affitto o la palestra: Money li registrerà da solo e ti avviserà prima del rinnovo."} />
        )}
        {visible.map((item) => (
          <Staggered key={item.id} index={2}>
            <RecurringCard item={item} onLongClick={() => navigate('recurringRegister', { id: item.id })} />
          </Staggered>
        ))}
        {visible.some((r) => r.active) && (
          <PaymentTotals items={visible.filter((r) => r.active)} yearly={yearly} onYearly={setYearly} />
        )}
        {visible.length > 0 && (
          <span className="t-caption" style={{ color: 'var(--inkFaint)', textAlign: 'center', paddingTop: 4 }}>
            Tieni premuto per aggiungere subito una spesa ai movimenti.
          </span>
        )}
      </div>
      {choosingSort && (
        <Sheet onDismiss={() => setChoosingSort(false)}>
          <div className="col" style={{ padding: '0 20px 28px', gap: 4 }}>
            <span className="t-title" style={{ color: 'var(--ink)', paddingBottom: 8 }}>Ordina per</span>
            {recurringSorts.map((o) => (
              <Bouncy key={o.id} pressedScale={0.98} className="row" style={{ padding: '14px 4px' }}
                onClick={() => { updateSettings({ recurringSort: o.id }); setChoosingSort(false) }}>
                <span className="t-bodyStrong flex1" style={{ color: 'var(--ink)' }}>{o.label}</span>
                {o.id === sort && <Icon name="check" color="var(--accentText)" />}
              </Bouncy>
            ))}
          </div>
        </Sheet>
      )}
    </div>
  )
}

const tabKinds: RecurringKind[] = ['SUBSCRIPTION', 'FIXED', 'FINANCING']

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

/** What the active fixed expenses cost on each payment method, per month or per year. */
function PaymentTotals({ items, yearly, onYearly }: { items: Recurring[]; yearly: boolean; onYearly: (y: boolean) => void }) {
  const custom = usePaymentMethods()
  const groups: { choice: PaymentChoice; items: Recurring[] }[] = []
  for (const r of items) {
    const choice = paymentChoice(r)
    const group = groups.find((g) => sameChoice(g.choice, choice))
    if (group) group.items.push(r)
    else groups.push({ choice, items: [r] })
  }
  const cost = (r: Recurring) => (yearly ? yearlyCents(r.frequency, r.amountCents) : monthlyCents(r.frequency, r.amountCents))
  const totals = groups
    .map((g) => ({ ...g, cents: g.items.reduce((acc, r) => acc + cost(r), 0) }))
    .sort((a, b) => b.cents - a.cents)
  const total = Math.max(1, totals.reduce((acc, t) => acc + t.cents, 0))
  const unit = yearly ? '/anno' : '/mese'
  return (
    <div className="col" style={{ paddingTop: 12 }}>
      <SectionTitle text="Per metodo di pagamento" />
      <Segmented options={['Al mese', "All'anno"]} selected={yearly ? 1 : 0} onSelect={(i) => onYearly(i === 1)} />
      <SoftCard padding={0} style={{ marginTop: 12, overflow: 'hidden' }}>
        {totals.map((t, i) => {
          const { label, icon } = paymentLabel(t.choice, custom)
          const own = t.choice.customId != null ? custom.find((m) => m.id === t.choice.customId) : undefined
          const color = own ? argb(own.color) : 'var(--accent)'
          return (
            <div key={`${t.choice.method}:${t.choice.customId}`}>
              {i > 0 && <div style={{ height: 1, margin: '0 20px', background: 'var(--surfaceHigh)' }} />}
              <div className="col" style={{ padding: '16px 20px', gap: 12 }}>
                <div className="row">
                  <div className="center" style={{ width: 42, height: 42, borderRadius: 21, flexShrink: 0, background: `color-mix(in srgb, ${color} 22%, transparent)` }}>
                    <AppIcon name={icon} size={22} color="var(--ink)" />
                  </div>
                  <div className="col flex1" style={{ marginLeft: 14, minWidth: 0 }}>
                    <span className="t-bodyStrong ellipsis" style={{ color: 'var(--ink)' }}>{label}</span>
                    <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>
                      {t.items.length === 1 ? '1 voce' : `${t.items.length} voci`} · {Math.trunc((t.cents * 100) / total)}%
                    </span>
                  </div>
                  <div className="col" style={{ alignItems: 'flex-end', marginLeft: 12 }}>
                    <span className="t-bodyStrong" style={{ color: 'var(--ink)', whiteSpace: 'nowrap' }}>{Money.format(t.cents)}</span>
                    <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>{unit}</span>
                  </div>
                </div>
                <SoftProgress progress={t.cents / total} color={color} />
              </div>
            </div>
          )
        })}
        {totals.length > 1 && (
          <div className="row" style={{ padding: '14px 20px', background: 'color-mix(in srgb, var(--surfaceHigh) 50%, transparent)' }}>
            <span className="t-bodyStrong flex1" style={{ color: 'var(--ink)' }}>Totale</span>
            <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>{Money.format(totals.reduce((acc, t) => acc + t.cents, 0))}</span>
            <span className="t-caption" style={{ color: 'var(--inkFaint)', marginLeft: 4 }}>{unit}</span>
          </div>
        )}
      </SoftCard>
    </div>
  )
}
