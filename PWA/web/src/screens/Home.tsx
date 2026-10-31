import { motion } from 'framer-motion'
import { useEffect, useMemo, useRef, useState } from 'react'
import { today } from '../../../shared/date.ts'
import { Dates, Money, percent } from '../../../shared/format.ts'
import { argb, Palette, type Recurring, type TxType } from '../../../shared/model.ts'
import { buildReport, type MonthReport } from '../../../shared/report.ts'
import { homeReselect, navigate, switchTab } from '../lib/nav.ts'
import { setMonth, useStore } from '../lib/store.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { BackdropBlobs, BlobBox, BlobLayer } from '../components/Blob.tsx'
import {
  AnimatedAmount, Bouncy, CategoryBadge, MonthSwitcher, RecurringLogo, RoundIconButton, SectionTitle, SoftCard, SoftProgress, Staggered,
} from '../components/ui.tsx'
import { fastOutSlowIn } from '../components/motion.ts'

export function HomeScreen() {
  const month = useStore((s) => s.month)
  const data = useStore((s) => s.data)
  const scroller = useRef<HTMLDivElement>(null)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const reset = () => scroller.current?.scrollTo({ top: 0 })
    homeReselect.addEventListener('reselect', reset)
    return () => homeReselect.removeEventListener('reselect', reset)
  }, [])

  const state = useMemo(() => {
    if (!data) return null
    const report = buildReport(month, data.transactions, data.categories, 2)
    const upcoming = data.recurring.filter((r) => r.active).sort((a, b) => a.nextDate - b.nextDate).slice(0, 10)
    return { report, upcoming }
  }, [data, month])

  const dark = document.documentElement.style.colorScheme === 'dark'

  return (
    <div style={{ position: 'relative', height: '100%', overflow: 'hidden' }}>
      <BackdropBlobs colors={['var(--accent)', 'var(--income)', argb(Palette.ROSE)]} alpha={dark ? 0.1 : 0.22} />
      <div className="col" style={{ position: 'relative', height: '100%' }}>
        <div className="row status-pad" style={{ flexShrink: 0 }}>
          <div className="row" style={{ width: '100%', padding: '12px 20px 8px' }}>
            <MonthSwitcher month={month} onChange={setMonth} style={{ flex: 1 }} />
            <RoundIconButton icon="auto_awesome" onClick={() => navigate('chat')} label="Chiedi a Money" />
            <div style={{ width: 8 }} />
            <RoundIconButton icon="settings" onClick={() => navigate('settings')} label="Impostazioni" />
          </div>
        </div>
        {/* The divider under the fixed bar shows up only once content scrolls beneath it. */}
        <div style={{ height: 1, background: 'var(--line)', opacity: scrolled ? 1 : 0, transition: 'opacity 0.2s', flexShrink: 0 }} />
        <div ref={scroller} className="scroll" onScroll={(e) => setScrolled(e.currentTarget.scrollTop > 0)}>
          <div className="col" style={{ padding: '8px 20px 156px', gap: 16 }}>
            {state && <HomeContent report={state.report} upcoming={state.upcoming} />}
          </div>
        </div>
      </div>
    </div>
  )
}

function HomeContent({ report: r, upcoming }: { report: MonthReport; upcoming: Recurring[] }) {
  return (
    <>
      <Staggered index={0}><Hero r={r} onAdd={(type) => navigate('add', { type })} /></Staggered>
      <Staggered index={1}><SpendGauge r={r} /></Staggered>

      {r.budgets.length > 0 && (
        <Staggered index={2}>
          <SectionTitle text="Budget" />
          <div className="hscroll" style={{ gap: 12, margin: '0 -20px', padding: '0 20px' }}>
            {r.budgets.map((line) => (
              <SoftCard key={line.category.id} padding={16} style={{ width: 150, flexShrink: 0 }}>
                <CategoryBadge icon={line.category.icon} color={argb(line.category.color)} size={36} />
                <span className="t-label ellipsis" style={{ marginTop: 10, color: 'var(--ink)' }}>{line.category.name}</span>
                <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{Money.short(line.spent)} di {Money.short(line.budget)}</span>
                <SoftProgress progress={line.ratio} height={8} style={{ marginTop: 10 }}
                  color={line.ratio > 1 ? 'var(--expense)' : line.ratio > 0.8 ? argb(Palette.BUTTER) : 'var(--income)'} />
              </SoftCard>
            ))}
          </div>
        </Staggered>
      )}

      <Staggered index={3}>
        <SectionTitle text="Dove vanno i soldi" action="Report" onAction={() => switchTab('report')} />
        {r.expenseSlices.length === 0 ? (
          <SoftCard><span className="t-body" style={{ color: 'var(--inkSoft)' }}>Ancora nessuna spesa questo mese.</span></SoftCard>
        ) : (
          <SoftCard style={{ gap: 14 }}>
            {r.expenseSlices.slice(0, 5).map((slice) => (
              <div key={slice.category.id} className="row">
                <CategoryBadge icon={slice.category.icon} color={argb(slice.category.color)} size={34} />
                <div className="col flex1" style={{ marginLeft: 12 }}>
                  <div className="row">
                    <span className="t-label flex1" style={{ color: 'var(--ink)' }}>{slice.category.name}</span>
                    <span className="t-label" style={{ color: 'var(--ink)' }}>{Money.format(slice.cents)}</span>
                  </div>
                  <SoftProgress progress={slice.share} color={argb(slice.category.color)} height={8} style={{ marginTop: 6 }} />
                </div>
                <span className="t-caption" style={{ width: 44, textAlign: 'right', color: 'var(--inkSoft)' }}>{percent(slice.share)}</span>
              </div>
            ))}
          </SoftCard>
        )}
      </Staggered>

      {upcoming.length > 0 && (
        <Staggered index={4}>
          <SectionTitle text="Prossimi addebiti" action="Tutti" onAction={() => switchTab('recurring')} />
          <div className="hscroll" style={{ gap: 12, margin: '0 -20px', padding: '0 20px' }}>
            {upcoming.map((item) => <UpcomingBubble key={item.id} item={item} />)}
          </div>
        </Staggered>
      )}
    </>
  )
}

function Hero({ r, onAdd }: { r: MonthReport; onAdd: (type: TxType) => void }) {
  const bob = {
    animate: { y: [-6, 6] },
    transition: { duration: 2.6, ease: fastOutSlowIn, repeat: Infinity, repeatType: 'reverse' as const },
  }
  return (
    <div style={{ position: 'relative', height: 310 }}>
      <div className="center" style={{ position: 'absolute', inset: 0 }}>
        <BlobBox size={250} gradient={['var(--accent)', argb(Palette.ROSE), argb(Palette.PEACH)]} seed={11} amplitude={0.08} speed={0.8}>
          <div className="col" style={{ alignItems: 'center' }}>
            <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>saldo del mese</span>
            <AnimatedAmount cents={r.balance} className="t-display" color={Ink} decimalsScale={0.45} />
            {r.savingsRate != null && (
              <span className="t-label" style={{ color: alpha(Ink, 0.8) }}>{r.balance >= 0 ? `risparmi ${percent(r.savingsRate)}` : 'in rosso'}</span>
            )}
          </div>
        </BlobBox>
      </div>
      <motion.div style={{ position: 'absolute', top: 0, right: 0 }} initial={{ y: -6, rotate: -2.4 }}
        animate={{ y: [-6, 6], rotate: [-2.4, 2.4] }} transition={bob.transition}>
        <MiniBlob label="entrate" cents={r.income} color="var(--income)" seed={21} onClick={() => onAdd('INCOME')} />
      </motion.div>
      <motion.div style={{ position: 'absolute', bottom: 0, left: 0 }} initial={{ y: 6 }} animate={{ y: [6, -6] }} transition={bob.transition}>
        <MiniBlob label="uscite" cents={r.expense} color="var(--expense)" seed={33} onClick={() => onAdd('EXPENSE')} />
      </motion.div>
    </div>
  )
}

function MiniBlob({ label, cents, color, seed, onClick }: { label: string; cents: number; color: string; seed: number; onClick: () => void }) {
  return (
    <Bouncy onClick={onClick} className="center blob-box" style={{ width: 118, height: 118 }}>
      <BlobLayer color={color} seed={seed} amplitude={0.1} />
      <div className="blob-content col" style={{ alignItems: 'center' }}>
        <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>{label}</span>
        <span className="t-titleSmall" style={{ color: Ink, whiteSpace: 'nowrap' }}>{Money.short(cents)}</span>
        <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>+ aggiungi</span>
      </div>
    </Bouncy>
  )
}

function SpendGauge({ r }: { r: MonthReport }) {
  const ratio = r.income > 0 ? r.expense / r.income : r.expense > 0 ? 1 : 0
  const text = r.income === 0 && r.expense === 0
    ? 'Il mese è appena iniziato: nessun movimento.'
    : r.income === 0 ? 'Nessuna entrata registrata finora.' : `Hai speso il ${percent(ratio)} di quello che è entrato`
  return (
    <SoftCard>
      <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>{text}</span>
      <SoftProgress progress={ratio} height={14} style={{ marginTop: 12 }}
        color={ratio > 1 ? 'var(--expense)' : ratio > 0.8 ? argb(Palette.BUTTER) : 'var(--income)'} />
      <div className="row" style={{ marginTop: 10 }}>
        <span className="t-caption flex1" style={{ color: 'var(--inkSoft)' }}>media {Money.short(r.avgDailyExpense)} al giorno</span>
        {r.fixedExpense > 0 && <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>fisse {Money.short(r.fixedExpense)}</span>}
      </div>
    </SoftCard>
  )
}

function UpcomingBubble({ item }: { item: Recurring }) {
  const soon = item.nextDate - today() <= 3
  return (
    <Bouncy onClick={() => navigate('recurringEdit', { id: item.id })} className="col" style={{ width: 92, flexShrink: 0, alignItems: 'center' }}>
      <RecurringLogo brandKey={item.brandKey} imagePath={item.imagePath} icon={item.icon} color={argb(item.color)} size={64} animated />
      <span className="t-label ellipsis" style={{ marginTop: 8, maxWidth: '100%', color: 'var(--ink)' }}>{item.name}</span>
      <span className="t-caption ellipsis" style={{ maxWidth: '100%', color: 'var(--inkSoft)' }}>{Money.format(item.amountCents)}</span>
      <span className="t-caption ellipsis" style={{ maxWidth: '100%', color: soon ? 'var(--expenseText)' : 'var(--accentText)' }}>{Dates.relative(item.nextDate)}</span>
    </Bouncy>
  )
}
