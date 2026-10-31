import { createServer as createHttp, type IncomingMessage, type ServerResponse } from 'node:http'
import { createServer as createHttps } from 'node:https'
import { createReadStream, existsSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { randomBytes, timingSafeEqual, createHash } from 'node:crypto'
import { extname, join, normalize } from 'node:path'
import { parseMonthKey } from '../../shared/date.ts'
import type { Category, Member, Recurring, Transaction, TxType } from '../../shared/model.ts'
import * as data from './db.ts'
import { exportBackup, importBackup } from './backup.ts'
import * as ai from './ai.ts'
import { runRecurring, subscribe, unsubscribe, vapidPublicKey } from './push.ts'

const PORT = Number(process.env.PORT ?? 8080)
const HTTPS_PORT = Number(process.env.HTTPS_PORT ?? 8443)
const PASSWORD = process.env.MONEY_PASSWORD ?? ''
const publicDir = process.env.PUBLIC_DIR ?? join(import.meta.dirname, '..', 'public')
const certDir = join(data.dataDir, 'certs')
const VERSION = JSON.parse(readFileSync(join(import.meta.dirname, '..', 'package.json'), 'utf8')).version as string

class HttpError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

// --- helpers ---------------------------------------------------------------------------------

function json(res: ServerResponse, status: number, body: unknown) {
  const text = JSON.stringify(body)
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(text)
}

async function readBody(req: IncomingMessage, limit = 25 * 1024 * 1024): Promise<Buffer> {
  const chunks: Buffer[] = []
  let size = 0
  for await (const chunk of req) {
    size += (chunk as Buffer).length
    if (size > limit) throw new HttpError(413, 'File troppo grande')
    chunks.push(chunk as Buffer)
  }
  return Buffer.concat(chunks)
}

async function readJson<T>(req: IncomingMessage): Promise<T> {
  const body = await readBody(req)
  try {
    return JSON.parse(body.toString('utf8')) as T
  } catch {
    throw new HttpError(400, 'Richiesta non valida')
  }
}

function cookies(req: IncomingMessage): Record<string, string> {
  const out: Record<string, string> = {}
  for (const part of (req.headers.cookie ?? '').split(';')) {
    const [k, ...v] = part.trim().split('=')
    if (k) out[k] = decodeURIComponent(v.join('='))
  }
  return out
}

const secure = (req: IncomingMessage) =>
  (req.socket as { encrypted?: boolean }).encrypted === true || req.headers['x-forwarded-proto'] === 'https'

// --- auth: one shared password for the household ----------------------------------------------

const SESSION = 'money_session'

function loggedIn(req: IncomingMessage): boolean {
  if (!PASSWORD) return true
  const token = cookies(req)[SESSION]
  if (!token) return false
  return data.db.prepare('SELECT 1 FROM sessions WHERE token = ?').get(token) != null
}

function passwordMatches(candidate: string): boolean {
  const a = createHash('sha256').update(candidate).digest()
  const b = createHash('sha256').update(PASSWORD).digest()
  return timingSafeEqual(a, b)
}

function startSession(req: IncomingMessage, res: ServerResponse) {
  const token = randomBytes(32).toString('hex')
  data.db.prepare('INSERT INTO sessions (token, createdAt) VALUES (?, ?)').run(token, Date.now())
  const flags = `Path=/; HttpOnly; SameSite=Lax; Max-Age=${60 * 60 * 24 * 400}${secure(req) ? '; Secure' : ''}`
  res.setHeader('Set-Cookie', `${SESSION}=${token}; ${flags}`)
}

// --- static files ----------------------------------------------------------------------------

const types: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.ico': 'image/x-icon',
  '.crt': 'application/x-x509-ca-cert', '.txt': 'text/plain; charset=utf-8',
}

function sendFile(req: IncomingMessage, res: ServerResponse, file: string, cache: string) {
  const stat = statSync(file)
  const etag = `"${stat.size.toString(16)}-${stat.mtimeMs.toString(16)}"`
  if (req.headers['if-none-match'] === etag) {
    res.writeHead(304)
    res.end()
    return
  }
  res.writeHead(200, {
    'Content-Type': types[extname(file).toLowerCase()] ?? 'application/octet-stream',
    'Content-Length': stat.size,
    'Cache-Control': cache,
    ETag: etag,
  })
  if (req.method === 'HEAD') res.end()
  else createReadStream(file).pipe(res)
}

function serveStatic(req: IncomingMessage, res: ServerResponse, path: string) {
  const clean = normalize(decodeURIComponent(path)).replace(/^([/\\])+/, '')
  const file = join(publicDir, clean)
  if (file.startsWith(publicDir) && clean && existsSync(file) && statSync(file).isFile()) {
    const immutable = clean.startsWith('assets')
    sendFile(req, res, file, immutable ? 'public, max-age=31536000, immutable' : 'no-cache')
    return
  }
  // Single page app: every other path gets the shell.
  sendFile(req, res, join(publicDir, 'index.html'), 'no-cache')
}

// --- API -------------------------------------------------------------------------------------

type Handler = (req: IncomingMessage, res: ServerResponse, params: string[], url: URL) => Promise<void> | void
const routes: [string, RegExp, Handler][] = []
const route = (method: string, pattern: string, handler: Handler) =>
  routes.push([method, new RegExp('^' + pattern.replace(/:\w+/g, '([^/]+)') + '$'), handler])

route('GET', '/api/session', (req, res) => json(res, 200, { authRequired: !!PASSWORD, loggedIn: loggedIn(req), version: VERSION }))

route('POST', '/api/login', async (req, res) => {
  const { password } = await readJson<{ password?: string }>(req)
  if (!PASSWORD || passwordMatches(String(password ?? ''))) {
    if (PASSWORD) startSession(req, res)
    json(res, 200, { ok: true })
    return
  }
  await new Promise((r) => setTimeout(r, 800))
  throw new HttpError(401, 'Password sbagliata')
})

route('POST', '/api/logout', (req, res) => {
  const token = cookies(req)[SESSION]
  if (token) data.db.prepare('DELETE FROM sessions WHERE token = ?').run(token)
  res.setHeader('Set-Cookie', `${SESSION}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`)
  json(res, 200, { ok: true })
})

route('GET', '/api/state', (_req, res, _p, url) => {
  const rev = data.revision()
  if (url.searchParams.get('since') === String(rev)) {
    json(res, 200, { revision: rev, unchanged: true })
    return
  }
  json(res, 200, { revision: rev, ...data.state() })
})

route('POST', '/api/transactions', async (req, res) => {
  const tx = await readJson<Transaction>(req)
  json(res, 200, data.saveTransaction({ ...tx, createdAt: tx.createdAt || Date.now() }))
})

route('DELETE', '/api/transactions/:id', (_req, res, [id]) => {
  data.deleteTransaction(Number(id))
  json(res, 200, { ok: true })
})

route('POST', '/api/recurring', async (req, res) => {
  const saved = data.saveRecurring(await readJson<Recurring>(req))
  await runRecurring()
  json(res, 200, saved)
})

route('DELETE', '/api/recurring/:id', (_req, res, [id]) => {
  data.deleteRecurring(Number(id))
  json(res, 200, { ok: true })
})

route('POST', '/api/categories', async (req, res) => {
  const { category, subs } = await readJson<{ category: Category; subs: Category[] }>(req)
  data.saveCategory(category, subs ?? [])
  json(res, 200, { ok: true })
})

route('DELETE', '/api/categories/:id', (_req, res, [id]) => {
  if (!data.deleteCategory(Number(id))) throw new HttpError(409, 'Categoria in uso da movimenti o spese fisse: non si può eliminare.')
  json(res, 200, { ok: true })
})

route('POST', '/api/members', async (req, res) => {
  data.saveMember(await readJson<Member>(req))
  json(res, 200, { ok: true })
})

route('DELETE', '/api/members/:id', (_req, res, [id]) => {
  data.deleteMember(Number(id))
  json(res, 200, { ok: true })
})

route('POST', '/api/images', async (req, res, _p, url) => {
  const body = await readBody(req, 15 * 1024 * 1024)
  if (body.length < 4 || !(body[0] === 0xff && body[1] === 0xd8)) throw new HttpError(400, 'Immagine non valida')
  const prefix = url.searchParams.get('prefix') === 'logo' ? 'logo' : 'receipt'
  const name = `${prefix}_${Date.now()}_${randomBytes(3).toString('hex')}.jpg`
  writeFileSync(join(data.imagesDir, name), body)
  json(res, 200, { path: `/images/${name}` })
})

route('DELETE', '/api/images', (_req, res, _p, url) => {
  const path = url.searchParams.get('path')
  if (path) data.deleteImage(path)
  json(res, 200, { ok: true })
})

route('GET', '/api/backup', (_req, res) => {
  const text = exportBackup()
  res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  res.end(text)
})

route('POST', '/api/backup', async (req, res) => {
  try {
    const count = importBackup((await readBody(req)).toString('utf8'))
    json(res, 200, { count })
  } catch (e) {
    throw new HttpError(400, (e as Error).message)
  }
})

// Push
route('GET', '/api/push/key', (_req, res) => json(res, 200, { key: vapidPublicKey }))
route('POST', '/api/push/subscribe', async (req, res) => {
  const { subscription, reminders } = await readJson<{ subscription: Parameters<typeof subscribe>[0]; reminders: boolean }>(req)
  if (!subscription?.endpoint) throw new HttpError(400, 'Iscrizione non valida')
  subscribe(subscription, reminders !== false)
  json(res, 200, { ok: true })
})
route('POST', '/api/push/unsubscribe', async (req, res) => {
  const { endpoint } = await readJson<{ endpoint: string }>(req)
  if (endpoint) unsubscribe(endpoint)
  json(res, 200, { ok: true })
})

// AI
const aiCall = async (res: ServerResponse, work: () => Promise<unknown>) => {
  try {
    json(res, 200, await work())
  } catch (e) {
    if (e instanceof ai.AiError) throw new HttpError(502, e.message)
    throw e
  }
}
const monthParam = (value: unknown) => {
  const month = parseMonthKey(String(value ?? ''))
  if (month == null) throw new HttpError(400, 'Mese non valido')
  return month
}

route('GET', '/api/ai', (_req, res) => json(res, 200, { ...ai.aiStatus(), models: ai.aiModels }))
route('PUT', '/api/ai', async (req, res) => {
  const { accountId, token, model } = await readJson<{ accountId: string; token?: string; model: string }>(req)
  ai.saveAiConfig(String(accountId ?? ''), token ?? null, String(model ?? ''))
  json(res, 200, ai.aiStatus())
})
route('DELETE', '/api/ai', (_req, res) => {
  ai.clearAiConfig()
  json(res, 200, ai.aiStatus())
})
route('PUT', '/api/ai/monthly', async (req, res) => {
  const { enabled } = await readJson<{ enabled: boolean }>(req)
  ai.setMonthlyNotice(!!enabled)
  json(res, 200, ai.aiStatus())
})
route('POST', '/api/ai/test', (_req, res) => aiCall(res, async () => ({ text: await ai.testConnection() })))
route('GET', '/api/ai/commentary', (_req, res, _p, url) => json(res, 200, ai.cachedCommentary(monthParam(url.searchParams.get('month')))))
route('POST', '/api/ai/commentary', async (req, res) => {
  const { month } = await readJson<{ month: string }>(req)
  await aiCall(res, async () => ({ text: await ai.monthCommentary(monthParam(month)), upToDate: true }))
})
route('POST', '/api/ai/ask', async (req, res) => {
  const { history, question } = await readJson<{ history: ai.ChatMessage[]; question: string }>(req)
  await aiCall(res, async () => ({ text: await ai.ask(Array.isArray(history) ? history : [], String(question ?? '')) }))
})
route('POST', '/api/ai/suggest', async (req, res) => {
  const { note, type } = await readJson<{ note: string; type: TxType }>(req)
  await aiCall(res, async () => ({ result: await ai.suggestCategory(String(note ?? ''), type === 'INCOME' ? 'INCOME' : 'EXPENSE'), connected: ai.aiConfig() != null }))
})
route('POST', '/api/ai/transcribe', async (req, res) => {
  const wav = await readBody(req, 8 * 1024 * 1024)
  await aiCall(res, async () => ({ text: await ai.transcribe(wav) }))
})

// --- server ----------------------------------------------------------------------------------

const open = new Set(['/api/session', '/api/login', '/api/logout'])

async function handle(req: IncomingMessage, res: ServerResponse) {
  const url = new URL(req.url ?? '/', 'http://localhost')
  const path = url.pathname
  try {
    if (path === '/certificato' || path === '/money-ca.crt') {
      const ca = join(certDir, 'money-ca.crt')
      if (!existsSync(ca)) throw new HttpError(404, 'HTTPS locale non attivo')
      if (path === '/money-ca.crt') sendFile(req, res, ca, 'no-cache')
      else {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
        res.end(certificatePage(req))
      }
      return
    }
    if (path.startsWith('/api/')) {
      if (!open.has(path) && !loggedIn(req)) throw new HttpError(401, 'Accesso richiesto')
      for (const [method, pattern, handler] of routes) {
        const match = pattern.exec(path)
        if (match && method === req.method) {
          await handler(req, res, match.slice(1), url)
          return
        }
      }
      throw new HttpError(404, 'Non trovato')
    }
    if (path.startsWith('/images/')) {
      if (!loggedIn(req)) throw new HttpError(401, 'Accesso richiesto')
      const name = path.slice('/images/'.length)
      const file = join(data.imagesDir, name)
      if (!/^[\w.-]+$/.test(name) || !existsSync(file)) throw new HttpError(404, 'Immagine non trovata')
      sendFile(req, res, file, 'private, max-age=31536000, immutable')
      return
    }
    serveStatic(req, res, path)
  } catch (e) {
    const status = e instanceof HttpError ? e.status : 500
    if (status === 500) console.error(e)
    if (!res.headersSent) json(res, status, { error: e instanceof HttpError ? e.message : 'Errore del server' })
    else res.end()
  }
}

function certificatePage(req: IncomingMessage): string {
  const host = (req.headers.host ?? '').split(':')[0]
  return `<!doctype html><html lang="it"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Money · certificato</title><style>body{font-family:system-ui,sans-serif;background:#F6F1EA;color:#15121F;max-width:560px;margin:auto;padding:24px;line-height:1.5}
a.b{display:inline-block;background:#B8A6FF;color:#15121F;padding:14px 22px;border-radius:99px;text-decoration:none;font-weight:600}li{margin:6px 0}</style></head><body>
<h1>Installa il certificato di Money</h1><p>Serve una volta sola per telefono: dopo, Money si apre in HTTPS e si può installare come app.</p>
<p><a class="b" href="/money-ca.crt">Scarica il certificato</a></p>
<h3>Android</h3><ol><li>Scarica il file qui sopra.</li><li>Impostazioni → Sicurezza e privacy → Altre impostazioni di sicurezza → Installa da memoria → <b>Certificato CA</b>.</li><li>Scegli <i>money-ca.crt</i> e conferma.</li></ol>
<h3>iPhone</h3><ol><li>Apri questa pagina con Safari e scarica il file.</li><li>Impostazioni → Profilo scaricato → Installa.</li><li>Impostazioni → Generali → Info → Impostazioni attendibilità certificati → attiva <i>Money CA</i>.</li></ol>
<p>Poi apri <b>https://${host}:${HTTPS_PORT}</b></p></body></html>`
}

data.seedIfEmpty()
await runRecurring()
// Like WorkManager's periodic job: due fixed expenses and reminders, every hour.
setInterval(runRecurring, 60 * 60 * 1000)

createHttp(handle).listen(PORT, () => console.log(`Money ${VERSION} su http://0.0.0.0:${PORT}`))

const cert = join(certDir, 'server.crt')
const key = join(certDir, 'server.key')
if (existsSync(cert) && existsSync(key)) {
  createHttps({ cert: readFileSync(cert), key: readFileSync(key) }, handle)
    .listen(HTTPS_PORT, () => console.log(`Money in HTTPS su https://0.0.0.0:${HTTPS_PORT}`))
}
