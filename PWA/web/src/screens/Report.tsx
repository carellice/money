import { motion } from 'framer-motion'
import { useEffect, useMemo, useState } from 'react'
import { monthEnd, monthKey, monthStart } from '../../../shared/date.ts'
import { Dates, Money, percent } from '../../../shared/format.ts'
import { argb, paletteAll, Palette, payment } from '../../../shared/model.ts'
import { buildReport, type MonthReport, type Pagella, type Slice } from '../../../shared/report.ts'
import { api } from '../lib/api.ts'
import { navigate } from '../lib/nav.ts'
import { cardBlob, download, fileName, pdfBlob, shareOrDownload } from '../lib/reportExport.ts'
import { setMonth, useStore } from '../lib/store.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { BlobBox } from '../components/Blob.tsx'
import { DonutChart, SpendingCalendar, TrendBars } from '../components/Charts.tsx'
import {
  AnimatedAmount, AppIcon, Bouncy, CategoryBadge, EmptyState, Icon, Legend, MonthSwitcher, Pill, SectionTitle, SoftCard, SoftProgress, Staggered,
} from '../components/ui.tsx'
import { TransactionRow } from '../components/TransactionRow.tsx'
import { toast } from '../components/overlays.tsx'
import { AiText, ThinkingDots } from './Chat.tsx'
import { Expand } from './RecurringEdit.tsx'
import { spring } from '../components/motion.ts'

type Commentary = { text: string; upToDate: boolean }

export function ReportScreen() {
  const month = useStore((s) => s.month)
  const data = useStore((s) => s.data)
  const report = useMemo(() => {
    if (!data) return null
    const txs = data.transactions.filter((t) => t.date >= monthStart(month - 5) && t.date <= monthEnd(month))
    return buildReport(month, txs, data.categories)
  }, [data, month])

  const [aiReady, setAiReady] = useState(false)
  const [commentary, setCommentary] = useState<Commentary | null>(null)
  const [aiBusy, setAiBusy] = useState(false)
  const [aiError, setAiError] = useState<string | null>(null)

  useEffect(() => {
    api<{ connected: boolean }>('/api/ai').then((s) => setAiReady(s.connected)).catch(() => {})
  }, [])

  const txCount = report?.transactions.length ?? 0
  useEffect(() => {
    setAiError(null)
    setCommentary(null)
    api<Commentary | null>(`/api/ai/commentary?month=${monthKey(month)}`).then(setCommentary).catch(() => {})
  }, [month, txCount])

  const generate = async () => {
    setAiBusy(true)
    setAiError(null)
    try {
      setCommentary(await api<Commentary>('/api/ai/commentary', { method: 'POST', json: { month: monthKey(month) } }))
    } catch (e) {
      setAiError((e as Error).message)
    }
    setAiBusy(false)
  }

  const r = report
  return (
    <div className="scroll">
      <div className="col" style={{ padding: '0 20px 156px', gap: 14 }}>
        <div className="col status-pad">
          <div style={{ paddingTop: 12 }}>
            <div className="t-display" style={{ color: 'var(--ink)' }}>Report</div>
            <MonthSwitcher month={month} onChange={setMonth} style={{ marginTop: 8 }} />
          </div>
        </div>
        {r && r.isEmpty && (
          <EmptyState title="Mese vuoto" icon="book" text={`Nessun movimento a ${Dates.monthName(r.month).toLowerCase()}: niente da raccontare.`} />
        )}
        {r && !r.isEmpty && (
          <>
            <div className="hscroll" style={{ gap: 8, margin: '0 -20px', padding: '0 20px' }}>
              <Pill text="Condividi immagine" selected={false} icon="image"
                onClick={async () => shareOrDownload(await cardBlob(r), fileName(r, 'png'), 'Il mio mese con Money')} />
              <Pill text="Condividi PDF" selected={false} icon="picture_as_pdf"
                onClick={async () => shareOrDownload(await pdfBlob(r), fileName(r, 'pdf'), `Report ${Dates.monthYear(r.month)}`)} />
              <Pill text="Salva PDF" selected={false} icon="download"
                onClick={async () => {
                  download(await pdfBlob(r), fileName(r, 'pdf'))
                  toast('PDF salvato')
                }} />
            </div>
            {r.pagella && <Staggered key={`p${month}`} index={0}><PagellaCard p={r.pagella} /></Staggered>}
            <Staggered key={`ai${month}`} index={1}>
              <AiCommentaryCard ready={aiReady} commentary={commentary} busy={aiBusy} error={aiError} onGenerate={generate} />
            </Staggered>
            <Staggered index={1}><StatGrid r={r} /></Staggered>
            {r.insights.length > 0 && (
              <Staggered index={2}>
                <SectionTitle text="Cosa è successo" />
                <SoftCard style={{ gap: 12 }}>
                  {r.insights.map((insight, i) => (
                    <div key={i} className="row" style={{ alignItems: 'flex-start' }}>
                      <BlobBox size={14} seed={i} style={{ marginTop: 4 }}
                        color={insight.positive === true ? 'var(--income)' : insight.positive === false ? 'var(--expense)' : 'var(--accent)'} />
                      <span className="t-body" style={{ marginLeft: 12, color: 'var(--ink)' }}>{insight.text}</span>
                    </div>
                  ))}
                </SoftCard>
              </Staggered>
            )}
            {r.expenseSlices.length > 0 && (
              <Staggered key={`d${month}`} index={3}><CategoryBreakdown slices={r.expenseSlices} total={r.expense} title="Spese per categoria" /></Staggered>
            )}
            <Staggered index={4}>
              <SectionTitle text="Calendario delle spese" />
              <SoftCard>
                <SpendingCalendar month={r.month} daily={r.daily} color="#FF8A7A" />
                <span className="t-caption" style={{ marginTop: 10, color: 'var(--inkFaint)' }}>Più il cerchio è grande, più hai speso quel giorno.</span>
              </SoftCard>
            </Staggered>
            <Staggered index={5}>
              <SectionTitle text={`Ultimi ${r.trend.length} mesi`} />
              <SoftCard>
                <TrendBars points={r.trend} highlight={r.month} />
                <div className="row" style={{ marginTop: 10, gap: 16 }}>
                  <Legend text="Entrate" color="var(--income)" />
                  <Legend text="Uscite" color="var(--expense)" />
                </div>
              </SoftCard>
            </Staggered>
            <Compared r={r} />
            {r.budgets.length > 0 && (
              <Staggered index={7}>
                <SectionTitle text="Budget" />
                <SoftCard style={{ gap: 14 }}>
                  {r.budgets.map((b) => (
                    <div key={b.category.id}>
                      <div className="row">
                        <span className="t-label flex1" style={{ color: 'var(--ink)' }}>{b.category.name}</span>
                        <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{Money.short(b.spent)} / {Money.short(b.budget)}</span>
                      </div>
                      <SoftProgress progress={b.ratio} style={{ marginTop: 6 }}
                        color={b.ratio > 1 ? 'var(--expense)' : b.ratio > 0.8 ? argb(Palette.BUTTER) : 'var(--income)'} />
                    </div>
                  ))}
                </SoftCard>
              </Staggered>
            )}
            <Staggered index={8}>
              <SectionTitle text="Fisse e variabili" />
              <SoftCard>
                <SplitBar share={r.expense > 0 ? r.fixedExpense / r.expense : 0} left="var(--accent)" right={argb(Palette.PEACH)} />
                <div className="row" style={{ marginTop: 10 }}>
                  <Legend text={`Fisse ${Money.short(r.fixedExpense)}`} color="var(--accent)" />
                  <span className="flex1" />
                  <Legend text={`Variabili ${Money.short(r.variableExpense)}`} color={argb(Palette.PEACH)} />
                </div>
              </SoftCard>
            </Staggered>
            {r.payments.length > 0 && (
              <Staggered index={9}>
                <SectionTitle text="Come hai pagato" />
                <SoftCard style={{ gap: 12 }}>
                  {r.payments.map(([method, cents], i) => {
                    const max = Math.max(1, ...r.payments.map((p) => p[1]))
                    return (
                      <div key={method} className="row">
                        <AppIcon name={payment(method).icon} size={18} color="var(--inkSoft)" />
                        <span className="t-label" style={{ width: 110, marginLeft: 10, color: 'var(--ink)' }}>{payment(method).label}</span>
                        <SoftProgress progress={cents / max} color={argb(paletteAll[i % paletteAll.length])} height={8} style={{ flex: 1 }} />
                        <span className="t-caption" style={{ marginLeft: 10, color: 'var(--inkSoft)', whiteSpace: 'nowrap' }}>{Money.short(cents)}</span>
                      </div>
                    )
                  })}
                </SoftCard>
              </Staggered>
            )}
            {r.incomeSlices.length > 0 && (
              <Staggered key={`i${month}`} index={10}><CategoryBreakdown slices={r.incomeSlices} total={r.income} title="Entrate per fonte" /></Staggered>
            )}
            {r.top.length > 0 && (
              <Staggered index={11}>
                <SectionTitle text="Le spese più grandi" />
                {r.top.map((tx) => (
                  <TransactionRow key={tx.id} tx={tx} categories={r.categories} onClick={() => navigate('add', { id: tx.id })} showDate={Dates.short(tx.date)} />
                ))}
              </Staggered>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function AiCommentaryCard({ ready, commentary, busy, error, onGenerate }: {
  ready: boolean
  commentary: Commentary | null
  busy: boolean
  error: string | null
  onGenerate: () => void
}) {
  return (
    <SoftCard>
      <div className="row">
        <BlobBox size={38} color="var(--accent)" seed={61} amplitude={0.1} animated={busy}>
          <Icon name="auto_awesome" size={20} color={Ink} />
        </BlobBox>
        <span className="t-titleSmall" style={{ marginLeft: 12, color: 'var(--ink)' }}>Il commento di Money AI</span>
      </div>
      <div style={{ height: 12 }} />
      {!ready ? (
        <>
          <span className="t-body" style={{ color: 'var(--inkSoft)' }}>Collega Cloudflare Workers AI per un commento scritto sul mese e consigli su misura.</span>
          <div className="row" style={{ marginTop: 12 }}><Pill text="Configura" selected={false} onClick={() => navigate('aiSettings')} icon="settings" /></div>
        </>
      ) : busy ? (
        <div className="row">
          <ThinkingDots color="var(--accentText)" />
          <span className="t-body" style={{ marginLeft: 12, color: 'var(--inkSoft)' }}>Sto leggendo i tuoi movimenti…</span>
        </div>
      ) : commentary ? (
        <>
          <AiText text={commentary.text} className="t-body" style={{ color: 'var(--ink)' }} />
          <div style={{ height: 12 }} />
          {!commentary.upToDate && <span className="t-caption" style={{ color: 'var(--inkFaint)', marginBottom: 8 }}>I movimenti sono cambiati dopo questo commento.</span>}
          <div className="row" style={{ gap: 8 }}>
            <Pill text="Rigenera" selected={false} onClick={onGenerate} icon="refresh" />
            <Pill text="Chiedi a Money" selected={false} onClick={() => navigate('chat')} icon="chat" />
          </div>
        </>
      ) : (
        <>
          <span className="t-body" style={{ color: 'var(--inkSoft)' }}>Fatti raccontare com'è andato il mese e cosa migliorare.</span>
          <div className="row" style={{ marginTop: 12, gap: 8 }}>
            <Pill text="Genera commento" selected onClick={onGenerate} icon="auto_awesome" />
            <Pill text="Chiedi a Money" selected={false} onClick={() => navigate('chat')} icon="chat" />
          </div>
        </>
      )}
      {error && <span className="t-caption" style={{ marginTop: 8, color: 'var(--expenseText)' }}>{error}</span>}
    </SoftCard>
  )
}

function PagellaCard({ p }: { p: Pagella }) {
  return (
    <SoftCard>
      <div className="row">
        <motion.div key={p.grade.letter + p.score} initial={{ scale: 0, rotate: -40 }} animate={{ scale: 1, rotate: 0 }} transition={spring(0.4, 140)}>
          <BlobBox size={124} color={argb(p.grade.color)} seed={5} amplitude={0.1}>
            <span className="t-hero" style={{ fontSize: 72, lineHeight: '72px', color: Ink }}>{p.grade.letter}</span>
          </BlobBox>
        </motion.div>
        <div className="col flex1" style={{ marginLeft: 16 }}>
          <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>PAGELLA DEL MESE</span>
          <span className="t-title" style={{ color: 'var(--ink)' }}>{p.grade.title}</span>
          <span className="t-body" style={{ marginTop: 4, color: 'var(--inkSoft)' }}>{p.summary}</span>
          <span className="t-label" style={{ marginTop: 8, color: 'var(--accentText)' }}>{p.score}/100</span>
        </div>
      </div>
    </SoftCard>
  )
}

function StatGrid({ r }: { r: MonthReport }) {
  return (
    <div className="col" style={{ gap: 12 }}>
      <div className="row" style={{ gap: 12, alignItems: 'stretch' }}>
        <StatTile label="Entrate" cents={r.income} previous={r.prevIncome} color="var(--income)" higherIsGood />
        <StatTile label="Uscite" cents={r.expense} previous={r.prevExpense} color="var(--expense)" higherIsGood={false} />
      </div>
      <div className="row" style={{ gap: 12, alignItems: 'stretch' }}>
        <StatTile label="Risparmiato" cents={r.balance} previous={r.prevIncome - r.prevExpense} color="var(--accent)" higherIsGood />
        <SoftCard color={argb(Palette.BUTTER)} padding={16} style={{ flex: 1, minWidth: 0 }}>
          <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>Media al giorno</span>
          <AnimatedAmount cents={r.avgDailyExpense} className="t-title" color={Ink} />
          <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>{r.savingsRate != null ? `risparmio ${percent(r.savingsRate)}` : 'nessuna entrata'}</span>
        </SoftCard>
      </div>
    </div>
  )
}

function StatTile({ label, cents, previous, color, higherIsGood }: { label: string; cents: number; previous: number; color: string; higherIsGood: boolean }) {
  const delta = previous !== 0 ? (cents - previous) / Math.abs(previous) : 0
  const up = delta >= 0
  return (
    <SoftCard color={color} padding={16} style={{ flex: 1, minWidth: 0 }}>
      <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>{label}</span>
      <AnimatedAmount cents={cents} className="t-title" color={Ink} />
      {previous !== 0 ? (
        <div className="row">
          <Icon name={up ? 'trending_up' : 'trending_down'} size={14} color={alpha(Ink, 0.8)} />
          <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>
            &nbsp;{up ? '+' : '−'}{percent(Math.abs(delta))} · {up === higherIsGood ? 'bene' : 'occhio'}
          </span>
        </div>
      ) : (
        <span className="t-caption" style={{ color: alpha(Ink, 0.8) }}>primo mese</span>
      )}
    </SoftCard>
  )
}

function CategoryBreakdown({ slices, total, title }: { slices: Slice[]; total: number; title: string }) {
  const [selected, setSelected] = useState(-1)
  const [expanded, setExpanded] = useState<number | null>(null)
  const current = slices[selected]
  return (
    <div>
      <SectionTitle text={title} />
      <SoftCard>
        <DonutChart segments={slices.map((s) => ({ share: s.share, color: argb(s.category.color) }))} selected={selected}>
          <div className="col" style={{ alignItems: 'center' }}>
            <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{current?.category.name ?? 'Totale'}</span>
            <AnimatedAmount cents={current?.cents ?? total} className="t-title" color="var(--ink)" />
            {current && <span className="t-label" style={{ color: 'var(--inkSoft)' }}>{percent(current.share)}</span>}
          </div>
        </DonutChart>
        <div style={{ height: 16 }} />
        {slices.map((s, i) => (
          <Bouncy key={s.category.id} pressedScale={0.98} haptics={false}
            onClick={() => {
              setSelected(selected === i ? -1 : i)
              setExpanded(expanded === s.category.id ? null : s.category.id)
            }}
            style={{ borderRadius: 18, padding: 8, background: i === selected ? 'var(--surfaceHigh)' : 'transparent', transition: 'background-color 0.2s' }}>
            <div className="row">
              <CategoryBadge icon={s.category.icon} color={argb(s.category.color)} size={34} />
              <div className="col flex1" style={{ marginLeft: 12 }}>
                <span className="t-label ellipsis" style={{ color: 'var(--ink)' }}>{s.category.name}</span>
                <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>{s.count} movimenti</span>
              </div>
              <div className="col" style={{ alignItems: 'flex-end' }}>
                <span className="t-label" style={{ color: 'var(--ink)', whiteSpace: 'nowrap' }}>{Money.format(s.cents)}</span>
                <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{percent(s.share)}</span>
              </div>
            </div>
            <Expand visible={expanded === s.category.id && s.subs.length > 0}>
              <div className="col" style={{ padding: '8px 0 0 46px' }}>
                {s.subs.map((sub) => (
                  <div key={sub.name} className="row" style={{ padding: '3px 0' }}>
                    <span className="t-caption flex1" style={{ color: 'var(--inkSoft)' }}>{sub.name}</span>
                    <span className="t-caption" style={{ color: 'var(--ink)' }}>{Money.format(sub.cents)}</span>
                  </div>
                ))}
              </div>
            </Expand>
          </Bouncy>
        ))}
      </SoftCard>
    </div>
  )
}

function Compared({ r }: { r: MonthReport }) {
  const compared = r.expenseSlices.filter((s) => s.prevCents > 0 || s.cents > 0)
  if (!(r.prevExpense > 0 && compared.length)) return null
  return (
    <Staggered index={6}>
      <SectionTitle text={`Rispetto a ${Dates.monthName(r.month - 1).toLowerCase()}`} />
      <SoftCard style={{ gap: 12 }}>
        {[...compared].sort((a, b) => Math.abs(b.cents - b.prevCents) - Math.abs(a.cents - a.prevCents)).slice(0, 6).map((s) => {
          const delta = s.cents - s.prevCents
          return (
            <div key={s.category.id} className="row">
              <CategoryBadge icon={s.category.icon} color={argb(s.category.color)} size={30} />
              <span className="t-label flex1 ellipsis" style={{ marginLeft: 12, color: 'var(--ink)' }}>{s.category.name}</span>
              <span className="t-caption" style={{ color: 'var(--inkFaint)', whiteSpace: 'nowrap' }}>{Money.short(s.prevCents)} → </span>
              <span className="t-caption" style={{ color: 'var(--ink)', whiteSpace: 'nowrap' }}>{Money.short(s.cents)}</span>
              <span className="t-label" style={{
                marginLeft: 8, color: Ink, borderRadius: 999, padding: '2px 8px', whiteSpace: 'nowrap',
                background: delta > 0 ? 'var(--expense)' : 'var(--income)',
              }}>{(delta > 0 ? '+' : '') + Money.short(delta)}</span>
            </div>
          )
        })}
      </SoftCard>
    </Staggered>
  )
}

function SplitBar({ share, left, right }: { share: number; left: string; right: string }) {
  return (
    <div className="row" style={{ height: 22, borderRadius: 999, overflow: 'hidden', background: right }}>
      <motion.div initial={{ width: '0%' }} animate={{ width: `${share * 100}%` }} transition={spring(0.7, 80)} style={{ height: '100%', background: left }} />
    </div>
  )
}

