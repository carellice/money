import { currentMonth, isoDate, monthEnd, monthKey, monthOf, monthStart, plusYears, today, type Month } from '../../shared/date.ts'
import { Dates, Money, percent } from '../../shared/format.ts'
import { frequency, payment, type Category, type Recurring, type Transaction, type TxType } from '../../shared/model.ts'
import { buildReport, type MonthReport } from '../../shared/report.ts'
import { categories, db, getMeta, recurring, setMeta, transactionsBetween } from './db.ts'

export class AiError extends Error {}

export type ChatMessage = { role: string; content: string }

export const aiModels = [
  { id: '@cf/meta/llama-3.3-70b-instruct-fp8-fast', label: 'Llama 3.3 70B', note: 'Bilanciato, buon italiano' },
  { id: '@cf/openai/gpt-oss-120b', label: 'GPT-OSS 120B', note: 'Più ragionato, più lento' },
  { id: '@cf/mistralai/mistral-small-3.1-24b-instruct', label: 'Mistral Small 3.1', note: 'Ottimo con le lingue europee' },
  { id: '@cf/meta/llama-3.1-8b-instruct-fp8', label: 'Llama 3.1 8B', note: 'Veloce ed economico' },
]

const WHISPER = '@cf/openai/whisper-large-v3-turbo'

// --- config (kept on the server: the token never goes back to the browser) -----------------

type AiConfig = { accountId: string; token: string; model: string }

export function aiConfig(): AiConfig | null {
  const accountId = getMeta('ai_account')
  const token = getMeta('ai_token')
  if (!accountId || !token) return null
  return { accountId, token, model: getMeta('ai_model') ?? aiModels[0].id }
}

export function aiStatus() {
  return {
    connected: aiConfig() != null,
    accountId: getMeta('ai_account') ?? '',
    model: getMeta('ai_model') ?? aiModels[0].id,
    hasToken: getMeta('ai_token') != null,
    monthlyNotice: getMeta('ai_monthly') !== 'false',
  }
}

/** [token] empty keeps the stored one. */
export function saveAiConfig(accountId: string, token: string | null, model: string) {
  setMeta('ai_account', accountId.trim())
  setMeta('ai_model', model.trim())
  if (token && token.trim()) setMeta('ai_token', token.trim())
}

export function clearAiConfig() {
  setMeta('ai_account', null)
  setMeta('ai_token', null)
}

export function setMonthlyNotice(enabled: boolean) {
  setMeta('ai_monthly', String(enabled))
}

function config(): AiConfig {
  const c = aiConfig()
  if (!c) throw new AiError('Configura Cloudflare Workers AI nelle impostazioni')
  return c
}

// --- Cloudflare Workers AI client ------------------------------------------------------------

async function post(c: AiConfig, path: string, body: unknown, timeoutMs: number): Promise<Record<string, unknown>> {
  if (!/^[A-Za-z0-9]{8,64}$/.test(c.accountId)) throw new AiError('Account ID non valido')
  let response: Response
  try {
    response = await fetch(`https://api.cloudflare.com/client/v4/accounts/${c.accountId}/${path}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${c.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    })
  } catch {
    throw new AiError('Connessione non riuscita: controlla internet')
  }
  const text = await response.text()
  if (!response.ok) throw new AiError(errorMessage(response.status, text))
  return JSON.parse(text)
}

function errorMessage(code: number, body: string): string {
  let detail: string | undefined
  try {
    const json = JSON.parse(body)
    detail = json?.errors?.[0]?.message ?? json?.error?.message
  } catch { /* not JSON */ }
  if (code === 401 || code === 403) return 'Token non valido o senza permesso Workers AI'
  if (code === 404) return 'Account ID o modello non trovati'
  if (code === 429) return 'Troppe richieste o limite gratuito raggiunto, riprova più tardi'
  return `Errore Cloudflare ${code}` + (detail ? `: ${detail}` : '')
}

async function complete(c: AiConfig, messages: ChatMessage[], maxTokens = 900, temperature = 0.4): Promise<string> {
  const json = await post(c, 'ai/v1/chat/completions', {
    model: c.model, max_tokens: maxTokens, temperature,
    messages: messages.map((m) => ({ role: m.role, content: m.content })),
  }, 90_000)
  const choices = json.choices as { message?: { content?: string | null } }[] | undefined
  const content = choices?.[0]?.message?.content
  if (!content || !content.trim() || content === 'null') throw new AiError('Il modello non ha restituito testo')
  return content.replace(/<think>[\s\S]*?<\/think>/g, '').trim()
}

/** Speech to text of a WAV recording with Whisper. */
export async function transcribe(wav: Buffer): Promise<string> {
  const json = await post(config(), `ai/run/${WHISPER}`, { audio: wav.toString('base64'), language: 'it' }, 120_000)
  const text = String((json.result as { text?: string } | undefined)?.text ?? '').trim()
  if (!text) throw new AiError('Non ho sentito niente, riprova')
  return text
}

// --- assistant -------------------------------------------------------------------------------

const system = `Sei Money, l'assistente dentro l'app di finanza personale "Money". L'utente è italiano.
Rispondi sempre in italiano, con tono amichevole, concreto e frasi brevi.
Usa solo i dati forniti: se un'informazione manca dillo, non inventare numeri.
Scrivi gli importi in euro nel formato italiano (es. 1.240,50 €).
Parla di budget, abitudini di spesa e risparmio; non consigliare investimenti specifici
(azioni, criptovalute, fondi): in quel caso suggerisci di sentire un consulente abilitato.
Formattazione: testo semplice, al massimo **grassetto** ed elenchi con "- ".`

export function testConnection(): Promise<string> {
  return complete(config(), [{ role: 'user', content: 'Rispondi solo con: Ciao, sono pronto!' }], 30)
}

export function report(month: Month): MonthReport {
  return buildReport(month, transactionsBetween(monthStart(month - 5), monthEnd(month)), categories())
}

function signature(r: MonthReport): string {
  const last = r.transactions.reduce((acc, t) => Math.max(acc, t.createdAt), 0)
  return `${r.transactions.length}|${r.income}|${r.expense}|${last}`
}

/** Last saved commentary, flagged stale when the month's movements changed since. */
export function cachedCommentary(month: Month): { text: string; upToDate: boolean } | null {
  const row = db.prepare('SELECT signature, text FROM ai_cache WHERE month = ?').get(monthKey(month)) as { signature: string; text: string } | undefined
  if (!row) return null
  return { text: row.text, upToDate: row.signature === signature(report(month)) }
}

export async function monthCommentary(month: Month): Promise<string> {
  const r = report(month)
  if (r.isEmpty) throw new AiError('Nessun movimento in questo mese')
  const prompt = `Ecco il resoconto di ${Dates.monthYear(month)}.

${describe(r)}

Scrivi un commento sul mese per l'utente:
1. un paragrafo di 2-3 frasi su com'è andata, citando i numeri più importanti;
2. "**Da tenere d'occhio**" con 1-2 punti;
3. "**Consigli**" con 2-3 consigli concreti e misurabili per il mese prossimo (es. "tieni il delivery sotto 60 €").
Massimo 170 parole.`
  const text = await complete(config(), [{ role: 'system', content: system }, { role: 'user', content: prompt }])
  db.prepare('INSERT INTO ai_cache (month, signature, text) VALUES (?, ?, ?) ON CONFLICT(month) DO UPDATE SET signature = excluded.signature, text = excluded.text')
    .run(monthKey(month), signature(r), text)
  return text
}

export function ask(history: ChatMessage[], question: string): Promise<string> {
  const messages: ChatMessage[] = [
    { role: 'system', content: `${system}\n\nDATI DELL'UTENTE (oggi è ${Dates.numeric(today())}):\n${chatContext()}` },
    ...history.slice(-10).map((m) => ({ role: m.role === 'assistant' ? 'assistant' : 'user', content: String(m.content) })),
    { role: 'user', content: question },
  ]
  return complete(config(), messages, 900)
}

function chatContext(): string {
  const now = today()
  const txs = transactionsBetween(monthStart(currentMonth() - 11), plusYears(now, 1))
  const cats = new Map(categories().map((c) => [c.id, c]))
  const active = recurring().filter((r) => r.active)
  const lines: string[] = []

  lines.push('## Totali per mese (ultimi 12 mesi)')
  const byMonth = new Map<Month, Transaction[]>()
  for (const t of txs) {
    const m = monthOf(t.date)
    byMonth.set(m, [...(byMonth.get(m) ?? []), t])
  }
  for (const m of [...byMonth.keys()].sort((a, b) => a - b)) {
    const list = byMonth.get(m)!
    const sum = (l: Transaction[]) => l.reduce((acc, t) => acc + t.amountCents, 0)
    lines.push(`${monthKey(m)}: entrate ${Money.format(sum(list.filter((t) => t.type === 'INCOME')))}, uscite ${Money.format(sum(list.filter((t) => t.type === 'EXPENSE')))}`)
    const groups = new Map<string, Transaction[]>()
    for (const t of list) {
      const key = `${t.type}|${t.categoryId}`
      groups.set(key, [...(groups.get(key) ?? []), t])
    }
    for (const [key, l] of groups) {
      const [type, catId] = key.split('|')
      const subGroups = new Map<number | null, Transaction[]>()
      for (const t of l) subGroups.set(t.subcategoryId, [...(subGroups.get(t.subcategoryId) ?? []), t])
      const subs = [...subGroups].filter(([sub]) => sub != null).map(([sub, sl]) => `${cats.get(sub!)?.name} ${Money.short(sum(sl))}`)
      let line = `  - ${type === 'INCOME' ? 'entrata' : 'spesa'} ${cats.get(Number(catId))?.name}: ${Money.format(sum(l))}`
      if (subs.length) line += ` (${subs.join(', ')})`
      lines.push(line)
    }
  }

  const recentFrom = monthStart(currentMonth() - 1)
  lines.push('', '## Movimenti del mese corrente e precedente')
  txs.filter((t) => t.date >= recentFrom).sort((a, b) => a.date - b.date).slice(-300).forEach((t) => lines.push(line(t, cats)))

  if (active.length) {
    lines.push('', '## Spese fisse e abbonamenti attivi')
    active.forEach((r) => lines.push(recurringLine(r, cats)))
  }
  const budgets = [...cats.values()].filter((c) => c.budgetCents != null)
  if (budgets.length) {
    lines.push('', '## Budget mensili')
    budgets.forEach((c) => lines.push(`- ${c.name}: ${Money.format(c.budgetCents!)}`))
  }
  return lines.join('\n') + '\n'
}

/** Category for a free text description: past movements with the same note first, then the model. */
export async function suggestCategory(note: string, type: TxType): Promise<[number, number | null] | null> {
  const needle = note.trim().toLowerCase()
  if (!needle) return null
  const cats = categories()
  const valid = new Set(cats.filter((c) => c.type === type).map((c) => c.id))
  const now = today()
  const past = transactionsBetween(plusYears(now, -2), plusYears(now, 1))
    .filter((t) => t.type === type && t.note.trim().toLowerCase() === needle && valid.has(t.categoryId))
  if (past.length) {
    const counts = new Map<string, number>()
    for (const t of past) {
      const key = `${t.categoryId}|${t.subcategoryId ?? ''}`
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
    let best = ''
    let max = 0
    for (const [key, n] of counts) if (n > max) { best = key; max = n }
    const [cat, sub] = best.split('|')
    return [Number(cat), sub ? Number(sub) : null]
  }

  const c = aiConfig()
  if (!c) return null
  const tops = cats.filter((x) => x.type === type && x.parentId == null)
  const list = tops.map((top) => {
    const subs = cats.filter((x) => x.parentId === top.id)
    return `${top.id}: ${top.name}` + subs.map((s) => `\n   ${s.id}: ${top.name} > ${s.name}`).join('')
  }).join('\n')
  const prompt = `Classifica questa ${type === 'EXPENSE' ? 'spesa' : 'entrata'}: "${note}".
Categorie disponibili (id: nome):
${list}
Rispondi SOLO con JSON: {"categoryId": <id categoria principale>, "subcategoryId": <id sottocategoria o null>}`
  const answer = await complete(c, [{ role: 'user', content: prompt }], 60, 0)
  const json = /\{[^{}]*\}/.exec(answer)?.[0]
  if (!json) return null
  let obj: { categoryId?: unknown; subcategoryId?: unknown }
  try { obj = JSON.parse(json) } catch { return null }
  const category = Number(obj.categoryId)
  if (!tops.some((t) => t.id === category)) return null
  const subId = obj.subcategoryId == null ? null : Number(obj.subcategoryId)
  const sub = subId != null && cats.some((x) => x.id === subId && x.parentId === category) ? subId : null
  return [category, sub]
}

function describe(r: MonthReport): string {
  const out: string[] = []
  out.push(`Entrate: ${Money.format(r.income)} (mese prima ${Money.format(r.prevIncome)})`)
  out.push(`Uscite: ${Money.format(r.expense)} (mese prima ${Money.format(r.prevExpense)})`)
  out.push(`Saldo: ${Money.format(r.balance)}` + (r.savingsRate != null ? `, tasso di risparmio ${percent(r.savingsRate)}` : ''))
  out.push(`Spese fisse: ${Money.format(r.fixedExpense)}, media giornaliera ${Money.format(r.avgDailyExpense)}`)
  if (r.pagella) out.push(`Pagella dell'app: ${r.pagella.grade.letter} (${r.pagella.score}/100)`)
  out.push('Spese per categoria:')
  r.expenseSlices.forEach((s) => {
    let line = `- ${s.category.name}: ${Money.format(s.cents)} (${percent(s.share)}), mese prima ${Money.format(s.prevCents)}`
    if (s.subs.length > 1) line += '; dettaglio: ' + s.subs.map((x) => `${x.name} ${Money.short(x.cents)}`).join(', ')
    out.push(line)
  })
  if (r.incomeSlices.length) out.push('Entrate per fonte: ' + r.incomeSlices.map((s) => `${s.category.name} ${Money.short(s.cents)}`).join(', '))
  if (r.budgets.length) out.push('Budget: ' + r.budgets.map((b) => `${b.category.name} ${Money.short(b.spent)} su ${Money.short(b.budget)}`).join(', '))
  out.push('Spese più grandi:')
  r.top.forEach((t) => out.push(line(t, r.categories)))
  return out.join('\n') + '\n'
}

function line(t: Transaction, cats: Map<number, Category>): string {
  const category = [cats.get(t.categoryId)?.name, t.subcategoryId != null ? cats.get(t.subcategoryId)?.name : undefined].filter(Boolean).join(' > ')
  const sign = t.type === 'INCOME' ? '+' : '-'
  const note = t.note.trim() ? ` "${t.note}"` : ''
  const fixed = t.recurringId != null ? ' [fissa]' : ''
  return `- ${isoDate(t.date)} ${sign}${Money.format(t.amountCents)} ${category}${note} (${payment(t.payment).label})${fixed}`
}

function recurringLine(r: Recurring, cats: Map<number, Category>): string {
  return `- ${r.name}: ${Money.format(r.amountCents)} ${frequency(r.frequency).label.toLowerCase()}, categoria ${cats.get(r.categoryId)?.name}, prossimo addebito ${isoDate(r.nextDate)}`
}
