import { dayOfWeek, monthDays, monthOf, monthStart, today as todayEpoch, ymd, type Month } from './date.ts'
import { Dates, Money, percent } from './format.ts'
import { Palette, type Category, type PaymentMethod, type Transaction } from './model.ts'

export type SubSlice = { name: string; cents: number }

export type Slice = {
  category: Category
  cents: number
  share: number
  prevCents: number
  count: number
  subs: SubSlice[]
}

export type MonthPoint = { month: Month; income: number; expense: number }

export type BudgetLine = { category: Category; spent: number; budget: number; ratio: number }

export type Grade = { letter: string; title: string; color: number; min: number }

export const grades: Grade[] = [
  { letter: 'A', title: 'Da manuale', color: Palette.MINT, min: 85 },
  { letter: 'B', title: 'Ottimo lavoro', color: Palette.SKY, min: 70 },
  { letter: 'C', title: 'Nella media', color: Palette.BUTTER, min: 55 },
  { letter: 'D', title: 'Si può fare meglio', color: Palette.ORANGE, min: 40 },
  { letter: 'E', title: 'Mese in salita', color: Palette.CORAL, min: 25 },
  { letter: 'F', title: 'SOS portafoglio', color: Palette.ROSE, min: 0 },
]

export type Pagella = { grade: Grade; score: number; summary: string }

/** positive: true good news, false warning, null neutral. */
export type Insight = { text: string; positive: boolean | null }

export type MonthReport = {
  month: Month
  income: number
  expense: number
  balance: number
  variableExpense: number
  isEmpty: boolean
  savingsRate: number | null
  avgDailyExpense: number
  expenseSlices: Slice[]
  incomeSlices: Slice[]
  /** Expense per day of month, index 0 = day 1. */
  daily: number[]
  fixedExpense: number
  payments: [PaymentMethod, number][]
  top: Transaction[]
  prevIncome: number
  prevExpense: number
  trend: MonthPoint[]
  budgets: BudgetLine[]
  pagella: Pagella | null
  insights: Insight[]
  transactions: Transaction[]
  categories: Map<number, Category>
}

const sum = (list: Transaction[]) => list.reduce((acc, t) => acc + t.amountCents, 0)

function groupBy<K, T>(list: T[], key: (item: T) => K): Map<K, T[]> {
  const map = new Map<K, T[]>()
  for (const item of list) {
    const k = key(item)
    const bucket = map.get(k)
    if (bucket) bucket.push(item)
    else map.set(k, [item])
  }
  return map
}

const byDesc = <T>(value: (item: T) => number) => (a: T, b: T) => value(b) - value(a)

/** @param all movements covering at least [month] and the months before it used for the trend. */
export function buildReport(
  month: Month,
  all: Transaction[],
  categories: Category[],
  trendMonths = 6,
  today = todayEpoch(),
): MonthReport {
  const byId = new Map(categories.map((c) => [c.id, c]))
  const byMonth = groupBy(all, (t) => monthOf(t.date))
  const current = byMonth.get(month) ?? []
  const previous = byMonth.get(month - 1) ?? []

  const expenses = current.filter((t) => t.type === 'EXPENSE')
  const incomes = current.filter((t) => t.type === 'INCOME')
  const income = sum(incomes)
  const expense = sum(expenses)
  const prevIncome = sum(previous.filter((t) => t.type === 'INCOME'))
  const prevExpense = sum(previous.filter((t) => t.type === 'EXPENSE'))

  const expenseSlices = slices(expenses, previous.filter((t) => t.type === 'EXPENSE'), byId)
  const incomeSlices = slices(incomes, previous.filter((t) => t.type === 'INCOME'), byId)

  const daily = new Array<number>(monthDays(month)).fill(0)
  expenses.forEach((t) => { daily[ymd(t.date).d - 1] += t.amountCents })

  const thisMonth = monthOf(today)
  const elapsedDays = month === thisMonth ? ymd(today).d : month > thisMonth ? 1 : monthDays(month)

  const budgets: BudgetLine[] = categories
    .filter((c) => c.parentId == null && c.type === 'EXPENSE' && (c.budgetCents ?? 0) > 0)
    .map((cat) => {
      const spent = sum(expenses.filter((t) => t.categoryId === cat.id))
      const budget = cat.budgetCents!
      return { category: cat, spent, budget, ratio: budget === 0 ? 0 : spent / budget }
    })
    .sort(byDesc((b) => b.ratio))

  const trend: MonthPoint[] = []
  for (let back = trendMonths - 1; back >= 0; back--) {
    const m = month - back
    const list = byMonth.get(m) ?? []
    trend.push({ month: m, income: sum(list.filter((t) => t.type === 'INCOME')), expense: sum(list.filter((t) => t.type === 'EXPENSE')) })
  }

  const savingsRate = income > 0 ? (income - expense) / income : null
  const payments: [PaymentMethod, number][] = [...groupBy(expenses, (t) => t.payment)]
    .map(([method, list]): [PaymentMethod, number] => [method, sum(list)])
    .sort(byDesc((p) => p[1]))

  const report: MonthReport = {
    month,
    income,
    expense,
    balance: income - expense,
    variableExpense: 0,
    isEmpty: current.length === 0,
    savingsRate,
    avgDailyExpense: Math.trunc(expense / Math.max(1, elapsedDays)),
    expenseSlices,
    incomeSlices,
    daily,
    fixedExpense: sum(expenses.filter((t) => t.recurringId != null)),
    payments,
    top: [...expenses].sort(byDesc((t) => t.amountCents)).slice(0, 5),
    prevIncome,
    prevExpense,
    trend,
    budgets,
    pagella: null,
    insights: [],
    transactions: current,
    categories: byId,
  }
  report.variableExpense = report.expense - report.fixedExpense
  report.pagella = pagella(report)
  report.insights = insights(report)
  return report
}

function slices(list: Transaction[], previous: Transaction[], byId: Map<number, Category>): Slice[] {
  const total = Math.max(1, sum(list))
  const prevByCat = new Map([...groupBy(previous, (t) => t.categoryId)].map(([k, l]) => [k, sum(l)]))
  const out: Slice[] = []
  for (const [catId, items] of groupBy(list, (t) => t.categoryId)) {
    const category = byId.get(catId)
    if (!category) continue
    const cents = sum(items)
    const subs = [...groupBy(items, (t) => t.subcategoryId)]
      .map(([subId, l]) => ({ name: (subId != null ? byId.get(subId)?.name : undefined) ?? 'Generico', cents: sum(l) }))
      .sort(byDesc((s) => s.cents))
    out.push({ category, cents, share: cents / total, prevCents: prevByCat.get(catId) ?? 0, count: items.length, subs })
  }
  return out.sort(byDesc((s) => s.cents))
}

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v))

function pagella(r: MonthReport): Pagella | null {
  if (r.isEmpty) return null
  let points = 0
  let weight = 0

  // Savings: 0% or less -> 0, 30% or more -> full marks.
  weight += 50
  points += r.income === 0 ? 0 : 50 * clamp((r.savingsRate ?? 0) / 0.3, 0, 1)

  if (r.budgets.length > 0) {
    weight += 30
    // Full marks under budget, linearly down to zero at +50%.
    const respected = r.budgets.reduce((acc, line) => acc + (1 - clamp((line.ratio - 1) / 0.5, 0, 1)), 0)
    points += 30 * (respected / r.budgets.length)
  }

  if (r.prevExpense > 0) {
    weight += 20
    const change = (r.expense - r.prevExpense) / r.prevExpense
    // -10% or better -> full marks, +20% or worse -> zero.
    points += 20 * clamp(1 - (change + 0.1) / 0.3, 0, 1)
  }

  const score = clamp(Math.round((points / weight) * 100), 0, 100)
  const grade = grades.find((g) => score >= g.min)!
  const summaries: Record<string, string> = {
    A: 'Mese esemplare: hai messo da parte tanto e tenuto le spese sotto controllo.',
    B: 'Bel mese: risparmi solidi, qualche margine di miglioramento.',
    C: 'Mese equilibrato, ma i risparmi potrebbero crescere.',
    D: "Le spese hanno corso un po' troppo: occhio alle categorie in rosso.",
    E: 'Hai speso quasi tutto quello che è entrato. Il prossimo mese si recupera.',
    F: 'Le uscite hanno superato le entrate. Serve una stretta.',
  }
  return { grade, score, summary: summaries[grade.letter] }
}

function insights(r: MonthReport): Insight[] {
  if (r.isEmpty) return []
  const out: Insight[] = []
  const prevName = Dates.monthName(r.month - 1).toLowerCase()

  if (r.income === 0 && r.expense > 0) out.push({ text: 'Nessuna entrata registrata: le spese pesano tutte sui risparmi.', positive: false })
  else if (r.balance >= 0 && r.income > 0) {
    out.push({ text: `Hai messo da parte ${Money.format(r.balance)}, il ${percent(r.savingsRate ?? 0)} di quello che hai guadagnato.`, positive: true })
  } else if (r.balance < 0) out.push({ text: `Hai speso ${Money.format(-r.balance)} più di quanto è entrato.`, positive: false })

  const top = r.expenseSlices[0]
  if (top) out.push({ text: `La voce più pesante è ${top.category.name}: ${percent(top.share)} delle uscite.`, positive: null })

  const changes = r.expenseSlices
    .filter((s) => s.prevCents > 0)
    .map((s) => [s, s.cents - s.prevCents] as const)
    .filter(([s, delta]) => Math.abs(delta) >= 2000 && Math.abs(delta) / s.prevCents >= 0.1)
  let up: (typeof changes)[number] | undefined
  let down: (typeof changes)[number] | undefined
  for (const c of changes) {
    if (!up || c[1] > up[1]) up = c
    if (!down || c[1] < down[1]) down = c
  }
  if (up && up[1] > 0) {
    const [s, delta] = up
    out.push({ text: `${s.category.name} è salita del ${percent(delta / s.prevCents)} rispetto a ${prevName} (+${Money.short(delta)}).`, positive: false })
  }
  if (down && down[1] < 0) {
    const [s, delta] = down
    out.push({ text: `Hai ridotto ${s.category.name} del ${percent(-delta / s.prevCents)} rispetto a ${prevName} (${Money.short(delta)}).`, positive: true })
  }

  if (r.budgets.length > 0) {
    const over = r.budgets.filter((b) => b.spent > b.budget)
    if (over.length === 0) out.push({ text: 'Tutti i budget rispettati. Grande!', positive: true })
    over.slice(0, 2).forEach((b) => out.push({ text: `Budget di ${b.category.name} sforato di ${Money.short(b.spent - b.budget)}.`, positive: false }))
  }

  if (r.expense > 0 && r.fixedExpense > 0) {
    out.push({ text: `Le spese fisse valgono il ${percent(r.fixedExpense / r.expense)} delle uscite.`, positive: null })
  }

  let maxIndex = -1
  r.daily.forEach((v, i) => { if (maxIndex < 0 || v > r.daily[maxIndex]) maxIndex = i })
  if (maxIndex >= 0 && r.daily[maxIndex] > 0) {
    const day = monthStart(r.month) + maxIndex
    out.push({ text: `Il giorno più caro è stato ${Dates.weekday(day)} ${maxIndex + 1} (${Money.short(r.daily[maxIndex])}).`, positive: null })
  }

  const weekend = sum(r.transactions.filter((t) => t.type === 'EXPENSE' && dayOfWeek(t.date) >= 6))
  if (r.expense > 0 && weekend / r.expense > 0.4) {
    out.push({ text: `Il ${percent(weekend / r.expense)} delle spese è nel weekend.`, positive: null })
  }
  return out
}

