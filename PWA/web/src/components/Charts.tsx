import { animate, motion } from 'framer-motion'
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { dayOfWeek, monthDays, monthStart, today, type Month } from '../../../shared/date.ts'
import { Dates } from '../../../shared/format.ts'
import type { MonthPoint } from '../../../shared/report.ts'
import { alpha, Ink } from '../lib/theme.ts'
import { Bouncy } from './ui.tsx'
import { fastOutSlowIn, spring } from './motion.ts'

export type DonutSegment = { share: number; color: string }

function arc(cx: number, cy: number, r: number, startDeg: number, sweepDeg: number): string {
  const a0 = (startDeg * Math.PI) / 180
  const a1 = ((startDeg + sweepDeg) * Math.PI) / 180
  const large = sweepDeg > 180 ? 1 : 0
  return `M${cx + r * Math.cos(a0)} ${cy + r * Math.sin(a0)} A${r} ${r} 0 ${large} 1 ${cx + r * Math.cos(a1)} ${cy + r * Math.sin(a1)}`
}

/** Ring chart that sweeps in; the selected segment grows thicker. */
export function DonutChart({ segments, thickness = 26, selected = -1, children }: {
  segments: DonutSegment[]
  thickness?: number
  selected?: number
  children?: ReactNode
}) {
  const box = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState(0)
  const [sweep, setSweep] = useState(0)
  const [grow, setGrow] = useState(0)
  const key = segments.map((s) => `${s.share}:${s.color}`).join('|')

  useLayoutEffect(() => {
    const el = box.current!
    const update = () => setSize(el.offsetWidth)
    update()
    const observer = new ResizeObserver(update)
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    setSweep(0)
    const controls = animate(0, 1, { duration: 1.1, ease: fastOutSlowIn, onUpdate: setSweep })
    return () => controls.stop()
  }, [key])

  useEffect(() => {
    const controls = animate(grow, selected >= 0 ? 1 : 0, { ...spring(0.5, 1500), onUpdate: setGrow })
    return () => controls.stop()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected])

  const stroke = thickness
  const extra = stroke * 0.35
  const inset = stroke / 2 + extra
  const r = size / 2 - inset
  const c = size / 2
  const gap = segments.length > 1 ? 4 : 0
  const total = 360 * sweep
  let start = -90
  const arcs: ReactNode[] = []
  segments.forEach((seg, i) => {
    const angle = seg.share * 360
    const visible = Math.max(0, Math.min(start + angle, -90 + total) - start)
    if (visible > gap && r > 0) {
      const width = i === selected ? stroke + extra * grow : stroke
      const sweepDeg = Math.min(visible - gap, 359.99)
      arcs.push(
        <path key={i} d={arc(c, c, r, start + gap / 2, sweepDeg)} fill="none" stroke={seg.color} strokeWidth={width}
          strokeLinecap={segments.length === 1 ? 'butt' : 'round'} />,
      )
    }
    start += angle
  })

  return (
    <div ref={box} style={{ position: 'relative', width: '78%', aspectRatio: '1', margin: '0 auto' }}>
      {size > 0 && (
        <svg width={size} height={size} style={{ position: 'absolute', inset: 0 }}>
          <circle cx={c} cy={c} r={Math.max(0, r)} fill="none" stroke="var(--surfaceHigh)" strokeWidth={stroke} />
          {arcs}
        </svg>
      )}
      <div className="center" style={{ position: 'absolute', inset: 0 }}>{children}</div>
    </div>
  )
}

/** Income/expense bar pairs per month, growing in one after the other. */
export function TrendBars({ points, highlight, height = 160 }: { points: MonthPoint[]; highlight?: Month; height?: number }) {
  const max = Math.max(1, ...points.map((p) => Math.max(p.income, p.expense)))
  return (
    <div className="row" style={{ height: height + 24, justifyContent: 'space-between', alignItems: 'flex-start' }}>
      {points.map((point, index) => {
        const active = point.month === highlight
        const bar = (value: number, color: string) => (
          <motion.div
            key={`${point.month}-${value}-${color}`}
            initial={{ height: '1.5%' }}
            animate={{ height: `${Math.min(1, Math.max(0.015, value / max)) * 100}%` }}
            transition={{ ...spring(0.6, 120), delay: (index * 70) / 1000 }}
            style={{ width: 12, borderRadius: '8px 8px 3px 3px', background: alpha(color, active ? 1 : 0.55) }}
          />
        )
        return (
          <div key={point.month} className="col flex1" style={{ alignItems: 'center' }}>
            <div className="row" style={{ height, padding: '0 4px', alignItems: 'flex-end', gap: 3 }}>
              {bar(point.income, '#8FE3C0')}
              {bar(point.expense, '#FF8A7A')}
            </div>
            <span className="t-caption" style={{ marginTop: 6, color: active ? 'var(--ink)' : 'var(--inkFaint)', textAlign: 'center' }}>
              {Dates.monthShort(point.month)}
            </span>
          </div>
        )
      })}
    </div>
  )
}

/** Month calendar where each day is a dot sized and tinted by how much was spent. */
export function SpendingCalendar({ month, daily, color, onDay }: {
  month: Month
  daily: number[]
  color: string
  onDay?: (epoch: number) => void
}) {
  const max = Math.max(1, ...daily)
  const offset = dayOfWeek(monthStart(month)) - 1
  const length = monthDays(month)
  const rows = Math.ceil((offset + length) / 7)
  const now = today()
  return (
    <div className="col" style={{ gap: 6 }}>
      <div className="row">
        {['L', 'M', 'M', 'G', 'V', 'S', 'D'].map((d, i) => (
          <span key={i} className="t-caption flex1" style={{ textAlign: 'center', color: 'var(--inkFaint)' }}>{d}</span>
        ))}
      </div>
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="row">
          {Array.from({ length: 7 }, (_, col) => {
            const index = r * 7 + col - offset
            const inMonth = index >= 0 && index < length
            const value = inMonth ? daily[index] : 0
            const intensity = value / max
            const date = monthStart(month) + index
            return (
              <div key={col} className="flex1 center" style={{ aspectRatio: '1', position: 'relative' }}>
                {inMonth && value > 0 && (
                  <motion.div
                    key={`${month}-${value}`}
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    transition={{ ...spring(0.5, 300), delay: (index * 18) / 1000 }}
                    style={{
                      position: 'absolute', width: `${(0.35 + 0.6 * intensity) * 100}%`, height: `${(0.35 + 0.6 * intensity) * 100}%`,
                      borderRadius: '50%', background: alpha(color, 0.35 + 0.65 * intensity),
                    }}
                  />
                )}
                {inMonth && value === 0 && date === now && (
                  <div style={{ position: 'absolute', width: '80%', height: '80%', borderRadius: '50%', background: 'var(--surfaceHigh)' }} />
                )}
                {inMonth && (
                  <Bouncy onClick={() => onDay?.(date)} disabled={!onDay} haptics={!!onDay} className="t-caption"
                    style={{ position: 'relative', color: value > 0 && intensity > 0.3 ? Ink : 'var(--inkSoft)' }}>
                    {index + 1}
                  </Bouncy>
                )}
              </div>
            )
          })}
        </div>
      ))}
    </div>
  )
}
