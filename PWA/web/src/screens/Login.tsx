import { AnimatePresence, motion } from 'framer-motion'
import { useState } from 'react'
import { argb, Palette } from '../../../shared/model.ts'
import { api } from '../lib/api.ts'
import { Ink } from '../lib/theme.ts'
import { BackdropBlobs, BlobBox } from '../components/Blob.tsx'
import { Bouncy, Icon, PrimaryButton, TextField } from '../components/ui.tsx'
import { tween } from '../components/motion.ts'

type Mode = 'login' | 'register'

/** Each family member has an account with its own Money: just a username and a password. */
export function LoginScreen({ onDone }: { onDone: (username: string) => void }) {
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const dark = document.documentElement.style.colorScheme === 'dark'
  const register = mode === 'register'
  const ready = !!username.trim() && !!password && (!register || !!repeat) && !busy

  const submit = async () => {
    if (!ready) return
    if (register && password !== repeat) return setError('Le due password non coincidono.')
    setBusy(true)
    setError(null)
    try {
      const result = await api<{ username: string }>(register ? '/api/register' : '/api/login', {
        method: 'POST',
        json: { username: username.trim(), password },
      })
      onDone(result.username)
    } catch (e) {
      setError((e as Error).message)
    }
    setBusy(false)
  }

  const switchMode = () => {
    setMode(register ? 'login' : 'register')
    setError(null)
    setRepeat('')
  }

  return (
    <div style={{ position: 'relative', height: '100%', background: 'var(--bg)', overflow: 'hidden' }}>
      <BackdropBlobs colors={['var(--accent)', 'var(--income)', argb(Palette.ROSE)]} alpha={dark ? 0.1 : 0.22} />
      <form className="col status-pad nav-pad" style={{ position: 'relative', height: '100%', padding: '0 24px', justifyContent: 'center' }}
        onSubmit={(e) => { e.preventDefault(); submit() }}>
        <BlobBox size={160} gradient={register ? ['var(--income)', 'var(--accent)'] : ['var(--accent)', argb(Palette.ROSE)]}
          seed={register ? 4 : 9} amplitude={0.1} style={{ alignSelf: 'center', flexShrink: 0 }}>
          <Icon name={register ? 'savings' : 'lock'} size={64} color={Ink} />
        </BlobBox>
        <AnimatePresence mode="wait" initial={false}>
          <motion.div key={mode} className="col" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
            transition={tween(180)}>
            <span className="t-display" style={{ marginTop: 28, textAlign: 'center', color: 'var(--ink)' }}>
              {register ? 'Crea il tuo Money' : 'Bentornato'}
            </span>
            <span className="t-body" style={{ marginTop: 10, marginBottom: 28, textAlign: 'center', color: 'var(--inkSoft)' }}>
              {register
                ? 'Scegli un nome utente e una password. I tuoi movimenti li vedi solo tu.'
                : 'Entra con il tuo nome utente e la tua password.'}
            </span>
          </motion.div>
        </AnimatePresence>
        <div className="col" style={{ gap: 10 }}>
          <TextField value={username} onChange={setUsername} placeholder="Nome utente" autoComplete="username" />
          <TextField value={password} onChange={setPassword} placeholder="Password" secret
            autoComplete={register ? 'new-password' : 'current-password'} />
          <AnimatePresence initial={false}>
            {register && (
              <motion.div key="repeat" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }} transition={tween(220)} style={{ overflow: 'hidden' }}>
                <TextField value={repeat} onChange={setRepeat} placeholder="Ripeti la password" secret autoComplete="new-password" />
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        {error && <span className="t-label" style={{ marginTop: 10, color: 'var(--expenseText)' }}>{error}</span>}
        <div style={{ height: 20, flexShrink: 0 }} />
        <PrimaryButton text={busy ? (register ? 'Creo l’account…' : 'Accesso…') : register ? 'Crea account' : 'Entra'}
          onClick={submit} enabled={ready} icon={register ? 'check' : 'arrow_forward'} />
        <Bouncy onClick={switchMode} className="t-label" style={{ alignSelf: 'center', marginTop: 14, padding: '10px 14px', borderRadius: 999, color: 'var(--inkSoft)' }}>
          {register ? 'Hai già un account? ' : 'Non hai un account? '}
          <span style={{ color: 'var(--accentText)' }}>{register ? 'Entra' : 'Creane uno'}</span>
        </Bouncy>
        <button type="submit" style={{ display: 'none' }} />
      </form>
    </div>
  )
}
