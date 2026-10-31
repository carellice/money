import webpush from 'web-push'
import { monthKey, today, ymd } from '../../shared/date.ts'
import { Dates, Money } from '../../shared/format.ts'
import type { Recurring } from '../../shared/model.ts'
import { aiConfig, aiStatus, monthCommentary, report } from './ai.ts'
import { db, getMeta, processRecurring, setMeta } from './db.ts'

// Web Push replaces Android notifications: renewal reminders and the end of month summary.

let keys = { publicKey: getMeta('vapid_public') ?? '', privateKey: getMeta('vapid_private') ?? '' }
if (!keys.publicKey || !keys.privateKey) {
  keys = webpush.generateVAPIDKeys()
  setMeta('vapid_public', keys.publicKey)
  setMeta('vapid_private', keys.privateKey)
}
webpush.setVapidDetails('https://github.com/carellice/money', keys.publicKey, keys.privateKey)

export const vapidPublicKey = keys.publicKey

type Subscription = { endpoint: string; keys: { p256dh: string; auth: string } }

export function subscribe(subscription: Subscription, reminders: boolean) {
  db.prepare('INSERT INTO push_subs (endpoint, json, reminders) VALUES (?, ?, ?) ON CONFLICT(endpoint) DO UPDATE SET json = excluded.json, reminders = excluded.reminders')
    .run(subscription.endpoint, JSON.stringify(subscription), reminders ? 1 : 0)
}

export function unsubscribe(endpoint: string) {
  db.prepare('DELETE FROM push_subs WHERE endpoint = ?').run(endpoint)
}

type Payload = { title: string; body: string; url: string; tag: string }

async function send(payload: Payload) {
  const rows = db.prepare('SELECT endpoint, json FROM push_subs WHERE reminders = 1').all() as { endpoint: string; json: string }[]
  await Promise.all(rows.map(async (row) => {
    try {
      await webpush.sendNotification(JSON.parse(row.json), JSON.stringify(payload), { TTL: 60 * 60 * 24 })
    } catch (e) {
      const status = (e as { statusCode?: number }).statusCode
      // The browser dropped the subscription: forget it.
      if (status === 404 || status === 410) unsubscribe(row.endpoint)
      else console.warn('Push non inviata:', (e as Error).message)
    }
  }))
}

function reminder(item: Recurring) {
  const text = item.autoRegister ? 'Verrà registrato in automatico.' : 'Ricordati di pagarlo.'
  return send({
    title: `${item.name}: ${Money.format(item.amountCents)} ${Dates.relative(item.nextDate)}`,
    body: `Rinnovo il ${Dates.long(item.nextDate)}. ${text}`,
    url: '/?route=recurring',
    tag: `renewal-${item.id}`,
  })
}

/** In the first days of a month, sums up the month just ended (with the AI comment when configured). */
async function monthlyNotice() {
  const now = today()
  const month = Math.floor(ymd(now).y * 12 + ymd(now).m - 1) - 1
  if (!aiStatus().monthlyNotice || ymd(now).d > 7 || getMeta('ai_last_notice') === monthKey(month)) return
  const hasSubscribers = (db.prepare('SELECT COUNT(*) AS n FROM push_subs WHERE reminders = 1').get() as { n: number }).n > 0
  if (!hasSubscribers) return
  const r = report(month)
  setMeta('ai_last_notice', monthKey(month))
  if (r.isEmpty) return
  let comment: string | null = null
  if (aiConfig()) {
    try { comment = (await monthCommentary(month)).replaceAll('**', '') } catch { comment = null }
  }
  const grade = r.pagella ? `Pagella ${r.pagella.grade.letter} · ` : ''
  await send({
    title: `${grade}${Dates.monthName(month)}: saldo ${Money.format(r.balance)}`,
    body: comment ?? r.pagella?.summary ?? `Entrate ${Money.format(r.income)}, uscite ${Money.format(r.expense)}.`,
    url: `/?route=report&month=${monthKey(month)}`,
    tag: 'monthly',
  })
}

/** Registers due fixed expenses and sends renewal reminders, like the Android RecurringWorker. */
export async function runRecurring() {
  try {
    const reminders = processRecurring()
    for (const item of reminders) await reminder(item)
    await monthlyNotice()
  } catch (e) {
    console.error('Elaborazione spese fisse non riuscita:', e)
  }
}
