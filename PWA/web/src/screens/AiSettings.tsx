import { useEffect, useState } from 'react'
import { api } from '../lib/api.ts'
import { close } from '../lib/nav.ts'
import { Bouncy, FormLabel, Pill, PrimaryButton, SoftCard, SwitchRow, TextField, TopBar } from '../components/ui.tsx'

type AiStatus = {
  connected: boolean
  accountId: string
  model: string
  hasToken: boolean
  monthlyNotice: boolean
  models: { id: string; label: string; note: string }[]
}

export function AiSettingsScreen() {
  const [status, setStatus] = useState<AiStatus | null>(null)
  const [accountId, setAccountId] = useState('')
  const [token, setToken] = useState('')
  const [model, setModel] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [ok, setOk] = useState<boolean | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<AiStatus>('/api/ai').then((s) => {
      setStatus(s)
      setAccountId(s.accountId)
      setModel(s.model)
    }).catch((e) => setMessage(e.message))
  }, [])

  if (!status) return <div className="col" style={{ height: '100%' }}><TopBar title="Assistente AI" onBack={() => close('aiSettings')} /></div>

  const custom = !status.models.some((m) => m.id === model)
  const canSave = !!accountId.trim() && (!!token.trim() || status.hasToken) && !!model.trim()

  /** Saves, then sends a tiny prompt to check account, token and model together. */
  const saveAndTest = async () => {
    setBusy(true)
    setMessage(null)
    setOk(null)
    try {
      setStatus({ ...status, ...(await api<AiStatus>('/api/ai', { method: 'PUT', json: { accountId, token: token || null, model } })) })
      setToken('')
      const { text } = await api<{ text: string }>('/api/ai/test', { method: 'POST' })
      setMessage(`Connesso. Risposta del modello: “${text.slice(0, 80)}”`)
      setOk(true)
      setStatus((s) => (s ? { ...s, connected: true } : s))
    } catch (e) {
      setMessage((e as Error).message)
      setOk(false)
    }
    setBusy(false)
  }

  const clear = async () => {
    setStatus({ ...status, ...(await api<AiStatus>('/api/ai', { method: 'DELETE' })) })
    setAccountId('')
    setToken('')
    setMessage('Credenziali rimosse dal server.')
    setOk(null)
  }

  const setMonthly = async (enabled: boolean) => {
    setStatus({ ...status, monthlyNotice: enabled })
    await api('/api/ai/monthly', { method: 'PUT', json: { enabled } }).catch(() => {})
  }

  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar title="Assistente AI" onBack={() => close('aiSettings')} />
      <div className="scroll">
        <div className="col nav-pad" style={{ padding: '0 20px' }}>
          <SoftCard color="var(--surfaceHigh)">
            <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>
              {status.connected ? 'Cloudflare Workers AI è collegato.' : 'Collega Cloudflare Workers AI per commento del mese, chat, categorie automatiche e resoconto di fine mese.'}
            </span>
            <span className="t-caption" style={{ marginTop: 6, color: 'var(--inkSoft)' }}>
              Serve un account Cloudflare: copia l'Account ID dalla dashboard e crea un API token con il permesso "Workers AI" (lettura e modifica).
            </span>
            <a className="t-label" href="https://dash.cloudflare.com/profile/api-tokens" target="_blank" rel="noreferrer"
              style={{ marginTop: 8, padding: '4px 0', color: 'var(--accentText)', textDecoration: 'none' }}>Apri la pagina dei token</a>
          </SoftCard>

          <FormLabel text="Account ID" />
          <TextField value={accountId} onChange={(v) => setAccountId(v.trim())} placeholder="es. 1a2b3c…" />

          <FormLabel text="API token" />
          <TextField value={token} onChange={(v) => setToken(v.trim())} secret
            placeholder={status.hasToken ? 'Salvato sul server (lascia vuoto per non cambiarlo)' : 'Incolla il token'} />

          <FormLabel text="Modello" />
          <div className="flow">
            {status.models.map((m) => <Pill key={m.id} text={m.label} selected={model === m.id} onClick={() => setModel(m.id)} />)}
            <Pill text="Altro" selected={custom} onClick={() => { if (!custom) setModel('') }} />
          </div>
          <span className="t-caption" style={{ paddingTop: 6, color: 'var(--inkSoft)' }}>
            {status.models.find((m) => m.id === model)?.note ?? "Scrivi l'ID completo, es. @cf/qwen/…"}
          </span>
          {custom && <TextField value={model} onChange={(v) => setModel(v.trim())} placeholder="@cf/…" style={{ marginTop: 8 }} />}

          <div style={{ height: 20 }} />
          <PrimaryButton text={busy ? 'Verifico…' : 'Salva e prova'} onClick={saveAndTest} enabled={canSave && !busy} icon="refresh" />
          {message && (
            <span className="t-label" style={{ paddingTop: 10, color: ok === true ? 'var(--incomeText)' : ok === false ? 'var(--expenseText)' : 'var(--inkSoft)' }}>{message}</span>
          )}

          <FormLabel text="Funzioni" />
          <SwitchRow title="Resoconto di fine mese" subtitle="Nei primi giorni del mese una notifica racconta il mese appena chiuso."
            checked={status.monthlyNotice} onChange={setMonthly} />

          <span className="t-caption" style={{ paddingTop: 16, color: 'var(--inkFaint)' }}>
            Privacy: quando usi l'AI, il server di Money invia a Cloudflare i totali per categoria e l'elenco dei movimenti (data, importo, categoria, nota, metodo).
            Le foto degli scontrini non vengono mai inviate. Il token resta sul tuo server, non torna mai al browser e non finisce nel backup.
          </span>
          {(status.connected || status.hasToken) && (
            <Bouncy onClick={clear} className="t-bodyStrong" style={{ marginTop: 16, padding: '8px 0', color: 'var(--expenseText)', alignSelf: 'flex-start' }}>
              Rimuovi credenziali
            </Bouncy>
          )}
          <div style={{ height: 32 }} />
        </div>
      </div>
    </div>
  )
}
