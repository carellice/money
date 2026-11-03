import { useMemo, useState } from 'react'
import { argb, monthlyCents, paymentChoice, paymentLabel, sameChoice, yearlyCents, type PaymentChoice, type Recurring, type RecurringKind } from '../../../shared/model.ts'
import { Money } from '../../../shared/format.ts'
import { close } from '../lib/nav.ts'
import { usePaymentMethods, useStore } from '../lib/store.ts'
import { AppIcon, EmptyState, Segmented, SoftCard, SoftProgress, TopBar } from '../components/ui.tsx'
import { KindChips } from './Recurring.tsx'

/** What the active fixed expenses cost on each payment method, on a page of its own. */
export function RecurringPaymentsScreen() {
  const recurring = useStore((s) => s.data?.recurring)
  const active = useMemo(() => recurring?.filter((r) => r.active) ?? [], [recurring])
  const [kind, setKind] = useState<RecurringKind | null>(null)
  const [yearly, setYearly] = useState(false)
  const visible = kind == null ? active : active.filter((r) => r.kind === kind)
  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar title="Metodi di pagamento" onBack={() => close('recurringPayments')} />
      <div className="scroll">
        <div className="col nav-pad" style={{ padding: '0 20px 24px', gap: 12 }}>
          <KindChips items={active} selected={kind} onSelect={setKind} />
          <Segmented options={['Al mese', "All'anno"]} selected={yearly ? 1 : 0} onSelect={(i) => setYearly(i === 1)} />
          {visible.length === 0
            ? <EmptyState title="Niente qui" icon="wallet" text="Nessuna voce attiva di questo tipo." />
            : <PaymentTotals items={visible} yearly={yearly} />}
        </div>
      </div>
    </div>
  )
}

/** What the active fixed expenses cost on each payment method, per month or per year. */
function PaymentTotals({ items, yearly }: { items: Recurring[]; yearly: boolean }) {
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
    <div className="col">
      <SoftCard padding={0} style={{ overflow: 'hidden' }}>
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
