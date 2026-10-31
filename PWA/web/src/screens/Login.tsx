import { useState } from 'react'
import { argb, Palette } from '../../../shared/model.ts'
import { api } from '../lib/api.ts'
import { Ink } from '../lib/theme.ts'
import { BackdropBlobs, BlobBox } from '../components/Blob.tsx'
import { Icon, PrimaryButton, TextField } from '../components/ui.tsx'

/** The household password set during the CasaOS setup; asked once per device. */
export function LoginScreen({ onDone }: { onDone: () => void }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const dark = document.documentElement.style.colorScheme === 'dark'

  const login = async () => {
    setBusy(true)
    setError(null)
    try {
      await api('/api/login', { method: 'POST', json: { password } })
      onDone()
    } catch (e) {
      setError((e as Error).message)
    }
    setBusy(false)
  }

  return (
    <div style={{ position: 'relative', height: '100%', background: 'var(--bg)', overflow: 'hidden' }}>
      <BackdropBlobs colors={['var(--accent)', 'var(--income)', argb(Palette.ROSE)]} alpha={dark ? 0.1 : 0.22} />
      <form className="col status-pad nav-pad" style={{ position: 'relative', height: '100%', padding: '0 24px', justifyContent: 'center' }}
        onSubmit={(e) => { e.preventDefault(); if (password && !busy) login() }}>
        <BlobBox size={160} gradient={['var(--accent)', argb(Palette.ROSE)]} seed={9} amplitude={0.1} style={{ alignSelf: 'center' }}>
          <Icon name="lock" size={64} color={Ink} />
        </BlobBox>
        <span className="t-display" style={{ marginTop: 28, textAlign: 'center', color: 'var(--ink)' }}>Bentornato</span>
        <span className="t-body" style={{ marginTop: 10, marginBottom: 28, textAlign: 'center', color: 'var(--inkSoft)' }}>
          Inserisci la password di Money scelta durante l'installazione.
        </span>
        <TextField value={password} onChange={setPassword} placeholder="Password" secret />
        {error && <span className="t-label" style={{ marginTop: 10, color: 'var(--expenseText)' }}>{error}</span>}
        <div style={{ height: 20 }} />
        <PrimaryButton text={busy ? 'Accesso…' : 'Entra'} onClick={login} enabled={!!password && !busy} icon="arrow_forward" />
        <button type="submit" style={{ display: 'none' }} />
      </form>
    </div>
  )
}
