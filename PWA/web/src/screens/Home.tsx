import { motion } from 'framer-motion'
import { useEffect, useMemo, useRef, useState } from 'react'
import { today } from '../../../shared/date.ts'
import { Dates, Money, percent } from '../../../shared/format.ts'
import { argb, Palette, type Recurring, type TxType } from '../../../shared/model.ts'
import { buildReport, type MonthReport } from '../../../shared/report.ts'
import { homeReselect, navigate, switchTab } from '../lib/nav.ts'
import { setMonth, useMetro, useNothing, useStore } from '../lib/store.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { BackdropBlobs, BlobBox, BlobLayer } from '../components/Blob.tsx'
import {
  AnimatedAmount, Bouncy, CategoryBadge, Icon, Masked, MonthSwitcher, RecurringLogo, RoundIconButton, SectionTitle, SoftCard,
  SoftProgress, Staggered, toggleHideAmounts, useHideable,
} from '../components/ui.tsx'
import { fastOutSlowIn } from '../components/motion.ts'
import { AccountsCard } from './Accounts.tsx'

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
  const hide = useHideable()
  return (
    <>
      <Staggered index={0}><Hero r={r} onAdd={(type) => navigate('add', { type })} /></Staggered>
      <Staggered index={1}><SpendGauge r={r} /></Staggered>
      <Staggered index={2}><AccountsCard /></Staggered>

      {r.budgets.length > 0 && (
        <Staggered index={2}>
          <SectionTitle text="Budget" />
          <div className="hscroll" style={{ gap: 12, margin: '0 -20px', padding: '0 20px' }}>
            {r.budgets.map((line) => (
              <SoftCard key={line.category.id} padding={16} style={{ width: 150, flexShrink: 0 }}>
                <CategoryBadge icon={line.category.icon} color={argb(line.category.color)} size={36} />
                <span className="t-label ellipsis" style={{ marginTop: 10, color: 'var(--ink)' }}>{line.category.name}</span>
                <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{hide(Money.short(line.spent))} di {hide(Money.short(line.budget))}</span>
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
                    <span className="t-label" style={{ color: 'var(--ink)' }}>{hide(Money.format(slice.cents))}</span>
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

/** Live tiles: a wide accent tile that flips now and then to show its back, and two square ones to add money in or out. */
function HeroMetro({ r, onAdd }: { r: MonthReport; onAdd: (type: TxType) => void }) {
  const hidden = useStore((s) => s.settings.hideAmounts)
  const hide = useHideable()
  const [back, setBack] = useState(false)
  useEffect(() => {
    // Like a live tile: rests a while, then turns over on its horizontal axis.
    const id = window.setInterval(() => setBack((b) => !b), 6000)
    return () => window.clearInterval(id)
  }, [])
  const white = '#FFFFFF'
  return (
    <div className="col" style={{ gap: 8, paddingTop: 4 }}>
      <Bouncy onClick={toggleHideAmounts} style={{ height: 176, perspective: 900 }}>
        <motion.div initial={false} animate={{ rotateX: back ? 180 : 0 }} transition={{ duration: 0.7, ease: [0.1, 0.9, 0.2, 1] }}
          style={{ position: 'relative', height: '100%', transformStyle: 'preserve-3d' }}>
          <div className="col" style={{ position: 'absolute', inset: 0, background: 'var(--accent)', padding: 14, justifyContent: 'space-between', backfaceVisibility: 'hidden' }}>
            <AnimatedAmount cents={r.balance} className="t-hero" color={white} decimalsScale={0.45} style={{ fontSize: 54, lineHeight: '58px' }} />
            <div className="row">
              <span className="t-label flex1" style={{ color: white, fontWeight: 400 }}>saldo del mese</span>
              <Icon name={hidden ? 'visibility_off' : 'visibility'} size={20} color={white} label={hidden ? 'Mostra importi' : 'Nascondi importi'} />
            </div>
          </div>
          <div className="col" style={{ position: 'absolute', inset: 0, background: 'var(--accent)', padding: 14, justifyContent: 'space-between', backfaceVisibility: 'hidden', transform: 'rotateX(180deg)' }}>
            <div className="col">
              <span className="t-title" style={{ color: white, fontSize: 34, lineHeight: '38px' }}>
                {r.savingsRate == null ? 'nessuna entrata' : r.balance >= 0 ? `risparmi ${percent(r.savingsRate)}` : 'in rosso'}
              </span>
              <span className="t-body" style={{ color: alpha(white, 0.85) }}>media {hide(Money.short(r.avgDailyExpense))} al giorno</span>
              {r.fixedExpense > 0 && <span className="t-body" style={{ color: alpha(white, 0.85) }}>fisse {hide(Money.short(r.fixedExpense))}</span>}
            </div>
            {/* The month balance stays in sight on the back too: it is the figure that matters most. */}
            <div className="row" style={{ alignItems: 'flex-end' }}>
              <span className="t-label flex1" style={{ color: white, fontWeight: 400 }}>saldo del mese</span>
              <span className="t-title" style={{ color: white, whiteSpace: 'nowrap' }}>{hide(Money.format(r.balance))}</span>
            </div>
          </div>
        </motion.div>
      </Bouncy>
      <div className="row" style={{ gap: 8 }}>
        <MetroTile label="entrate" cents={r.income} color="var(--income)" icon="add" onClick={() => onAdd('INCOME')} />
        <MetroTile label="uscite" cents={r.expense} color="var(--expense)" icon="remove" onClick={() => onAdd('EXPENSE')} />
      </div>
    </div>
  )
}

function MetroTile({ label, cents, color, icon, onClick }: { label: string; cents: number; color: string; icon: 'add' | 'remove'; onClick: () => void }) {
  return (
    <Bouncy onClick={onClick} aria-label={`Aggiungi ${label}`} className="col flex1"
      style={{ aspectRatio: '1', background: color, padding: 12, justifyContent: 'space-between' }}>
      <Icon name={icon} size={34} color="#FFFFFF" />
      <div className="col">
        <Masked className="t-title" color="#FFFFFF">
          <span className="t-title" style={{ color: '#FFFFFF', whiteSpace: 'nowrap' }}>{Money.format(cents)}</span>
        </Masked>
        <span className="t-label" style={{ color: '#FFFFFF', fontWeight: 400 }}>{label}</span>
      </div>
    </Bouncy>
  )
}

/** Nothing hero: the month balance in big dot-matrix figures over a faint dot grid, then two stat cards to add money in or out. */
function HeroNothing({ r, onAdd }: { r: MonthReport; onAdd: (type: TxType) => void }) {
  const hidden = useStore((s) => s.settings.hideAmounts)
  const negative = r.balance < 0
  return (
    <div className="col" style={{ gap: 8, paddingTop: 4 }}>
      <Bouncy onClick={toggleHideAmounts} className="col dot-grid"
        style={{ borderRadius: 16, backgroundColor: 'var(--surface)', backgroundSize: '14px 14px', padding: 20 }}>
        <div className="row">
          <span className="t-label flex1" style={{ color: 'var(--inkSoft)', letterSpacing: '0.1em' }}>SALDO DEL MESE</span>
          <Icon name={hidden ? 'visibility_off' : 'visibility'} size={18} color="var(--inkSoft)" label={hidden ? 'Mostra importi' : 'Nascondi importi'} />
        </div>
        <AnimatedAmount cents={r.balance} className="t-hero" color={negative ? 'var(--expense)' : 'var(--ink)'} decimalsScale={0.45}
          style={{ fontSize: 60, lineHeight: '64px', marginTop: 18 }} />
        <span className="t-label" style={{ marginTop: 10, color: negative ? 'var(--expenseText)' : 'var(--inkSoft)' }}>
          {r.savingsRate == null ? 'NESSUNA ENTRATA' : r.balance >= 0 ? `RISPARMI ${percent(r.savingsRate)}` : 'IN ROSSO'}
        </span>
      </Bouncy>
      <div className="row" style={{ gap: 8 }}>
        <NothingStat label="ENTRATE" cents={r.income} color="var(--incomeText)" onClick={() => onAdd('INCOME')} />
        <NothingStat label="USCITE" cents={r.expense} color="var(--expenseText)" onClick={() => onAdd('EXPENSE')} />
      </div>
    </div>
  )
}

function NothingStat({ label, cents, color, onClick }: { label: string; cents: number; color: string; onClick: () => void }) {
  return (
    <Bouncy onClick={onClick} className="col flex1" style={{ borderRadius: 16, background: 'var(--surface)', padding: 16 }}>
      <div className="row">
        <span className="t-caption flex1" style={{ color: 'var(--inkSoft)' }}>{label}</span>
        <Icon name="add" size={18} color="var(--ink)" label={`Aggiungi ${label.toLowerCase()}`} />
      </div>
      <Masked className="t-amount" color={color}>
        <span className="t-amount" style={{ color, fontSize: 19.8, marginTop: 10, whiteSpace: 'nowrap' }}>{Money.format(cents)}</span>
      </Masked>
    </Bouncy>
  )
}

function Hero({ r, onAdd }: { r: MonthReport; onAdd: (type: TxType) => void }) {
  const nothing = useNothing()
  const metro = useMetro()
  if (nothing) return <HeroNothing r={r} onAdd={onAdd} />
  if (metro) return <HeroMetro r={r} onAdd={onAdd} />
  return <HeroBlob r={r} onAdd={onAdd} />
}

function HeroBlob({ r, onAdd }: { r: MonthReport; onAdd: (type: TxType) => void }) {
  const hidden = useStore((s) => s.settings.hideAmounts)
  const bob = {
    animate: { y: [-6, 6] },
    transition: { duration: 2.6, ease: fastOutSlowIn, repeat: Infinity, repeatType: 'reverse' as const },
  }
  return (
    <div style={{ position: 'relative', height: 310 }}>
      <div className="center" style={{ position: 'absolute', inset: 0 }}>
        <Bouncy onClick={toggleHideAmounts} pressedScale={0.96}>
          <BlobBox size={250} gradient={['var(--accent)', argb(Palette.ROSE), argb(Palette.PEACH)]} seed={11} amplitude={0.08} speed={0.8}>
            <div className="col" style={{ alignItems: 'center' }}>
              <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>saldo del mese</span>
              <AnimatedAmount cents={r.balance} className="t-display" color={Ink} decimalsScale={0.45} />
              {r.savingsRate != null && (
                <span className="t-label" style={{ color: alpha(Ink, 0.8) }}>{r.balance >= 0 ? `risparmi ${percent(r.savingsRate)}` : 'in rosso'}</span>
              )}
              {/* The whole bubble toggles; the eye only says so. */}
              <Icon name={hidden ? 'visibility_off' : 'visibility'} size={20} color={alpha(Ink, 0.7)} style={{ marginTop: 6 }}
                label={hidden ? 'Mostra importi' : 'Nascondi importi'} />
            </div>
          </BlobBox>
        </Bouncy>
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
        <Masked className="t-titleSmall" color={Ink}>
          <span className="t-titleSmall" style={{ color: Ink, whiteSpace: 'nowrap' }}>{Money.format(cents)}</span>
        </Masked>
        <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>+ aggiungi</span>
      </div>
    </Bouncy>
  )
}

function SpendGauge({ r }: { r: MonthReport }) {
  const hide = useHideable()
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
        <span className="t-caption flex1" style={{ color: 'var(--inkSoft)' }}>media {hide(Money.short(r.avgDailyExpense))} al giorno</span>
        {r.fixedExpense > 0 && <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>fisse {hide(Money.short(r.fixedExpense))}</span>}
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
