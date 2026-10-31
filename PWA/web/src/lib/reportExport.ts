import { monthKey, monthYear, today } from '../../../shared/date.ts'
import { Dates, Money, percent } from '../../../shared/format.ts'
import { argb, Palette, payment } from '../../../shared/model.ts'
import type { MonthReport } from '../../../shared/report.ts'
import { BlobSpec } from './blob.ts'

// Draws the monthly report as a multi page A4 PDF and as a social card image, like ReportExport on Android.

const INK = '#15121F'
const INK_SOFT = '#5E5870'
const PAPER = '#F6F1EA'
const LINE = 'rgba(0,0,0,0.1)'
const color = (c: number, alpha?: number) => argb(c, alpha)

type Ctx = CanvasRenderingContext2D

function font(ctx: Ctx, size: number, weight = 400, fill = INK) {
  ctx.font = `${weight} ${size}px Bricolage, sans-serif`
  ctx.fillStyle = fill
}

function blob(ctx: Ctx, seed: number, cx: number, cy: number, w: number, h: number, fill: string) {
  const path = new Path2D(new BlobSpec(seed, 7, 0.08).path(w, h, 1.3, cx - w / 2, cy - h / 2))
  ctx.fillStyle = fill
  ctx.fill(path)
}

function centered(ctx: Ctx, text: string, cx: number, baseline: number) {
  ctx.fillText(text, cx - ctx.measureText(text).width / 2, baseline)
}

function right(ctx: Ctx, text: string, x: number, baseline: number) {
  ctx.fillText(text, x - ctx.measureText(text).width, baseline)
}

function roundRect(ctx: Ctx, x: number, y: number, w: number, h: number, r: number, fill: string) {
  ctx.fillStyle = fill
  ctx.beginPath()
  ctx.roundRect(x, y, Math.max(0, w), h, Math.min(r, Math.max(0, w) / 2, h / 2))
  ctx.fill()
}

function ellipsize(ctx: Ctx, text: string, width: number): string {
  if (ctx.measureText(text).width <= width) return text
  let out = text
  while (out.length > 0 && ctx.measureText(out + '…').width > width) out = out.slice(0, -1)
  return out + '…'
}

function wrap(ctx: Ctx, text: string, width: number): string[] {
  const lines: string[] = []
  for (const paragraph of text.split('\n')) {
    let line = ''
    for (const word of paragraph.split(' ')) {
      const candidate = line ? `${line} ${word}` : word
      if (ctx.measureText(candidate).width > width && line) {
        lines.push(line)
        line = word
      } else line = candidate
    }
    lines.push(line)
  }
  return lines
}

async function fontsReady() {
  await document.fonts.load('400 12px Bricolage')
  await document.fonts.load('800 12px Bricolage')
}

// --- PDF ------------------------------------------------------------------------------------

const W = 595
const H = 842
const SCALE = 2.5

class Pdf {
  pages: HTMLCanvasElement[] = []
  ctx!: Ctx
  y = 0
  margin = 40
  r: MonthReport

  constructor(r: MonthReport) {
    this.r = r
  }

  render() {
    this.newPage()
    this.header()
    this.tiles()
    this.pagella()
    this.insights()
    this.categories()
    this.budgets()
    this.trend()
    this.movements()
    this.finishPage()
  }

  newPage() {
    const canvas = document.createElement('canvas')
    canvas.width = W * SCALE
    canvas.height = H * SCALE
    this.ctx = canvas.getContext('2d')!
    this.ctx.scale(SCALE, SCALE)
    this.ctx.fillStyle = PAPER
    this.ctx.fillRect(0, 0, W, H)
    this.pages.push(canvas)
    this.y = this.margin
  }

  finishPage() {
    const { ctx, margin } = this
    font(ctx, 8, 400, INK_SOFT)
    ctx.fillText(`Money · ${Dates.monthYear(this.r.month)}`, margin, H - 22)
    right(ctx, String(this.pages.length), W - margin, H - 22)
  }

  ensure(space: number) {
    if (this.y + space > H - 50) {
      this.finishPage()
      this.newPage()
    }
  }

  sectionTitle(text: string) {
    this.ensure(40)
    this.y += 14
    font(this.ctx, 15, 700)
    this.ctx.fillText(text, this.margin, this.y + 14)
    this.y += 26
  }

  paragraph(text: string, size = 10, fill = INK, x = this.margin, w = W - 2 * this.margin): number {
    font(this.ctx, size, 400, fill)
    const lines = wrap(this.ctx, text, w)
    const lineHeight = size * 1.25 + 2
    const height = lines.length * lineHeight
    this.ensure(height)
    font(this.ctx, size, 400, fill)
    lines.forEach((line, i) => this.ctx.fillText(line, x, this.y + size + i * lineHeight))
    return height
  }

  header() {
    const { ctx, margin, r } = this
    const h = 150
    const y = this.y
    roundRect(ctx, margin, y, W - 2 * margin, h, 28, INK)
    ctx.save()
    ctx.beginPath()
    ctx.roundRect(margin, y, W - 2 * margin, h, 28)
    ctx.clip()
    blob(ctx, 11, W - 120, y + 30, 220, 200, color(Palette.LILAC))
    blob(ctx, 21, W - 230, y + h + 10, 140, 120, color(Palette.ROSE))
    ctx.restore()
    font(ctx, 9, 600, '#B9B2CC')
    ctx.fillText('REPORT MENSILE', margin + 24, y + 36)
    font(ctx, 32, 800, '#F4EFFA')
    ctx.fillText(Dates.monthYear(r.month), margin + 24, y + 76)
    font(ctx, 9, 500, '#B9B2CC')
    ctx.fillText(`Generato il ${Dates.numeric(today())} · ${r.transactions.length} movimenti`, margin + 24, y + 100)
    if (r.pagella) {
      const cx = W - margin - 70
      const cy = y + h / 2
      blob(ctx, 5, cx, cy, 104, 104, color(r.pagella.grade.color))
      font(ctx, 54, 800, INK)
      centered(ctx, r.pagella.grade.letter, cx, cy + 20)
    }
    this.y += h + 18
  }

  tiles() {
    const { ctx, margin, r } = this
    const items: [string, string, number][] = [
      ['Entrate', Money.format(r.income), Palette.MINT],
      ['Uscite', Money.format(r.expense), Palette.CORAL],
      ['Saldo', Money.format(r.balance), Palette.LILAC],
      ['Risparmio', r.savingsRate != null ? percent(r.savingsRate) : '—', Palette.BUTTER],
    ]
    const gap = 10
    const w = (W - 2 * margin - gap * 3) / 4
    items.forEach(([label, value, c], i) => {
      const x = margin + i * (w + gap)
      roundRect(ctx, x, this.y, w, 64, 18, color(c))
      font(ctx, 9, 600)
      ctx.fillText(label, x + 12, this.y + 22)
      font(ctx, 14, 700)
      ctx.fillText(ellipsize(ctx, value, w - 20), x + 12, this.y + 46)
    })
    this.y += 80
  }

  pagella() {
    const p = this.r.pagella
    if (!p) return
    this.sectionTitle(`Pagella: ${p.grade.letter} · ${p.grade.title} (${p.score}/100)`)
    this.y += this.paragraph(p.summary, 11) + 4
  }

  insights() {
    if (!this.r.insights.length) return
    this.sectionTitle('Cosa è successo')
    for (const insight of this.r.insights) {
      const c = insight.positive === true ? Palette.MINT : insight.positive === false ? Palette.CORAL : Palette.LILAC
      this.ensure(18)
      this.ctx.fillStyle = color(c)
      this.ctx.beginPath()
      this.ctx.arc(this.margin + 5, this.y + 7, 4.5, 0, Math.PI * 2)
      this.ctx.fill()
      this.y += this.paragraph(insight.text, 10, INK, this.margin + 16, W - 2 * this.margin - 16) + 5
    }
  }

  categories() {
    const { r, margin } = this
    if (!r.expenseSlices.length) return
    this.sectionTitle('Spese per categoria')
    const donut = 150
    this.ensure(donut + 10)
    const ctx = this.ctx
    const cx = margin + 10 + donut / 2
    const cy = this.y + donut / 2
    const radius = donut / 2 - 13
    ctx.lineWidth = 26
    let start = -90
    for (const s of r.expenseSlices) {
      const sweep = s.share * 360
      ctx.strokeStyle = color(s.category.color)
      ctx.beginPath()
      ctx.arc(cx, cy, radius, (start * Math.PI) / 180, ((start + sweep) * Math.PI) / 180)
      ctx.stroke()
      start += sweep
    }
    font(ctx, 13, 700)
    centered(ctx, Money.short(r.expense), cx, cy + 5)

    const tx = margin + donut + 40
    const rx = W - margin
    let ty = this.y + 4
    font(ctx, 8, 600, INK_SOFT)
    ctx.fillText('CATEGORIA', tx, ty + 8)
    right(ctx, 'IMPORTO', rx - 110, ty + 8)
    right(ctx, '%', rx - 60, ty + 8)
    right(ctx, 'VS PREC.', rx, ty + 8)
    ty += 16
    for (const s of r.expenseSlices.slice(0, 9)) {
      ctx.fillStyle = color(s.category.color)
      ctx.beginPath()
      ctx.arc(tx + 4, ty + 6, 4, 0, Math.PI * 2)
      ctx.fill()
      font(ctx, 10, 500)
      ctx.fillText(s.category.name, tx + 14, ty + 10)
      font(ctx, 10, 600)
      right(ctx, Money.format(s.cents), rx - 110, ty + 10)
      font(ctx, 10)
      right(ctx, percent(s.share), rx - 60, ty + 10)
      const delta = s.prevCents > 0 ? s.cents - s.prevCents : null
      font(ctx, 9, 500, (delta ?? 0) > 0 ? '#D14B3A' : '#1E8A63')
      right(ctx, delta != null ? (delta > 0 ? '+' : '') + Money.short(delta) : 'nuova', rx, ty + 10)
      ty += 16
    }
    this.y = Math.max(this.y + donut, ty) + 6
    for (const s of r.expenseSlices) {
      if (s.subs.length <= 1) continue
      this.ensure(16)
      const text = `${s.category.name}: ` + s.subs.map((x) => `${x.name} ${Money.short(x.cents)}`).join(' · ')
      this.y += this.paragraph(text, 8.5, INK_SOFT) + 3
    }
  }

  budgets() {
    const { r, margin } = this
    if (!r.budgets.length) return
    this.sectionTitle('Budget')
    for (const b of r.budgets) {
      this.ensure(26)
      const ctx = this.ctx
      font(ctx, 10, 600)
      ctx.fillText(b.category.name, margin, this.y + 10)
      font(ctx, 10)
      right(ctx, `${Money.short(b.spent)} / ${Money.short(b.budget)}`, W - margin, this.y + 10)
      const barY = this.y + 16
      roundRect(ctx, margin, barY, W - 2 * margin, 6, 3, LINE)
      const c = b.ratio > 1 ? Palette.CORAL : b.ratio > 0.8 ? Palette.BUTTER : Palette.MINT
      roundRect(ctx, margin, barY, (W - 2 * margin) * Math.min(1, Math.max(0, b.ratio)), 6, 3, color(c))
      this.y += 30
    }
  }

  trend() {
    const { r, margin } = this
    this.sectionTitle(`Ultimi ${r.trend.length} mesi`)
    const h = 110
    this.ensure(h + 30)
    const ctx = this.ctx
    const max = Math.max(1, ...r.trend.map((p) => Math.max(p.income, p.expense)))
    const slot = (W - 2 * margin) / r.trend.length
    r.trend.forEach((point, i) => {
      const x = margin + i * slot + slot / 2
      const ih = (h * point.income) / max
      const eh = (h * point.expense) / max
      roundRect(ctx, x - 14, this.y + h - ih, 12, ih, 4, color(Palette.MINT))
      roundRect(ctx, x + 2, this.y + h - eh, 12, eh, 4, color(Palette.CORAL))
      font(ctx, 8.5, point.month === r.month ? 700 : 400)
      centered(ctx, Dates.monthShort(point.month), x, this.y + h + 14)
    })
    this.y += h + 26
    ctx.fillStyle = color(Palette.MINT)
    ctx.beginPath()
    ctx.arc(margin + 4, this.y, 4, 0, Math.PI * 2)
    ctx.fill()
    font(ctx, 8.5)
    ctx.fillText('Entrate', margin + 12, this.y + 3)
    ctx.fillStyle = color(Palette.CORAL)
    ctx.beginPath()
    ctx.arc(margin + 64, this.y, 4, 0, Math.PI * 2)
    ctx.fill()
    font(ctx, 8.5)
    ctx.fillText('Uscite', margin + 72, this.y + 3)
    this.y += 12
  }

  movements() {
    const { r, margin } = this
    if (!r.transactions.length) return
    this.sectionTitle('Tutti i movimenti')
    const cols = [margin, margin + 62, margin + 180, margin + 380]
    const rx = W - margin
    const headerRow = () => {
      font(this.ctx, 8, 600, INK_SOFT)
      this.ctx.fillText('DATA', cols[0], this.y + 8)
      this.ctx.fillText('CATEGORIA', cols[1], this.y + 8)
      this.ctx.fillText('NOTA', cols[2], this.y + 8)
      this.ctx.fillText('METODO', cols[3], this.y + 8)
      right(this.ctx, 'IMPORTO', rx, this.y + 8)
      this.y += 16
    }
    headerRow()
    for (const tx of [...r.transactions].sort((a, b) => a.date - b.date || a.createdAt - b.createdAt)) {
      if (this.y + 16 > H - 50) {
        this.finishPage()
        this.newPage()
        headerRow()
      }
      const ctx = this.ctx
      const cat = r.categories.get(tx.categoryId)
      const sub = tx.subcategoryId != null ? r.categories.get(tx.subcategoryId) : undefined
      font(ctx, 9)
      ctx.fillText(Dates.numeric(tx.date), cols[0], this.y + 9)
      ctx.fillText(ellipsize(ctx, [cat?.name, sub?.name].filter(Boolean).join(' · '), cols[2] - cols[1] - 8), cols[1], this.y + 9)
      ctx.fillText(ellipsize(ctx, tx.note, cols[3] - cols[2] - 8), cols[2], this.y + 9)
      font(ctx, 9, 400, INK_SOFT)
      ctx.fillText(payment(tx.payment).label, cols[3], this.y + 9)
      const income = tx.type === 'INCOME'
      font(ctx, 9, 600, income ? '#1E8A63' : INK)
      right(ctx, Money.format(income ? tx.amountCents : -tx.amountCents, true), rx, this.y + 9)
      ctx.fillStyle = LINE
      ctx.fillRect(margin, this.y + 14, rx - margin, 0.6)
      this.y += 16
    }
  }
}

export async function pdfBlob(report: MonthReport): Promise<Blob> {
  await fontsReady()
  const pdf = new Pdf(report)
  pdf.render()
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF({ unit: 'pt', format: [W, H], compress: true })
  pdf.pages.forEach((canvas, i) => {
    if (i > 0) doc.addPage([W, H])
    doc.addImage(canvas.toDataURL('image/jpeg', 0.92), 'JPEG', 0, 0, W, H)
  })
  return doc.output('blob')
}

// --- share card -------------------------------------------------------------------------------

export async function cardBlob(report: MonthReport): Promise<Blob> {
  await fontsReady()
  const w = 1080
  const h = 1350
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = INK
  ctx.fillRect(0, 0, w, h)
  blob(ctx, 11, w * 0.95, h * 0.05, 760, 700, color(Palette.LILAC))
  blob(ctx, 21, 0, h * 0.62, 520, 520, color(Palette.MINT, 0x55 / 255))
  blob(ctx, 33, w, h * 0.98, 600, 560, color(Palette.ROSE, 0x66 / 255))

  const light = '#F4EFFA'
  const soft = '#B9B2CC'
  font(ctx, 34, 700, soft)
  ctx.fillText('MONEY', 80, 130)
  font(ctx, 120, 800, light)
  ctx.fillText(Dates.monthName(report.month), 80, 250)
  font(ctx, 52, 600, soft)
  ctx.fillText(String(monthYear(report.month).y), 84, 320)

  if (report.pagella) {
    const p = report.pagella
    blob(ctx, 5, 820, 300, 300, 300, color(p.grade.color))
    font(ctx, 180, 800, INK)
    centered(ctx, p.grade.letter, 820, 360)
    font(ctx, 38, 700, light)
    centered(ctx, p.grade.title, 820, 500)
  }

  font(ctx, 36, 500, soft)
  ctx.fillText('saldo del mese', 80, 560)
  font(ctx, 110, 800, light)
  ctx.fillText(Money.format(report.balance), 80, 680)

  roundRect(ctx, 80, 740, 440, 140, 70, color(Palette.MINT))
  font(ctx, 28, 500, INK)
  ctx.fillText('entrate', 120, 795)
  font(ctx, 40, 700, INK)
  ctx.fillText(Money.short(report.income), 120, 852)
  roundRect(ctx, 560, 740, 440, 140, 70, color(Palette.CORAL))
  font(ctx, 28, 500, INK)
  ctx.fillText('uscite', 600, 795)
  font(ctx, 40, 700, INK)
  ctx.fillText(Money.short(report.expense), 600, 852)

  let y = 960
  for (const s of report.expenseSlices.slice(0, 3)) {
    font(ctx, 36, 600, light)
    ctx.fillText(s.category.name, 80, y)
    font(ctx, 36, 600, soft)
    right(ctx, percent(s.share), 1000, y)
    roundRect(ctx, 80, y + 20, 920, 24, 12, 'rgba(255,255,255,0.13)')
    roundRect(ctx, 80, y + 20, 920 * s.share, 24, 12, color(s.category.color))
    y += 110
  }

  font(ctx, 30, 500, soft)
  ctx.fillText('Il mio mese con Money', 80, h - 70)
  return await new Promise((resolve) => canvas.toBlob((b) => resolve(b!), 'image/png'))
}

export function fileName(report: MonthReport, ext: string) {
  return `Money-${monthKey(report.month)}.${ext}`
}

/** Shares the file with the system sheet when possible, otherwise downloads it. */
export async function shareOrDownload(blob: Blob, name: string, title: string) {
  const file = new File([blob], name, { type: blob.type })
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title })
      return
    } catch (e) {
      if ((e as Error).name === 'AbortError') return
    }
  }
  download(blob, name)
}

export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

