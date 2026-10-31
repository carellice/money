import { useEffect, useState } from 'react'
import { today } from '../../../shared/date.ts'
import { argb, paletteAll, Palette, type Member } from '../../../shared/model.ts'
import { api } from '../lib/api.ts'
import { navigate, close } from '../lib/nav.ts'
import { disablePush, enablePush, pushSupported } from '../lib/push.ts'
import { download } from '../lib/reportExport.ts'
import { isoDate } from '../../../shared/date.ts'
import { actions, forgetUser, loggedUser, refresh, themeModes, updateSettings, useStore } from '../lib/store.ts'
import { Ink } from '../lib/theme.ts'
import type { UiIcon } from '../lib/icons.gen.ts'
import { Bouncy, ColorSwatches, FormLabel, Icon, NoteField, PrimaryButton, Segmented, SwitchRow, TextField, TopBar } from '../components/ui.tsx'
import { AlertDialog, Sheet, toast } from '../components/overlays.tsx'
import { deviceLockAvailable, registerDeviceLock } from './Lock.tsx'

type UpdateState = 'idle' | 'checking' | 'upToDate' | 'available' | 'failed'

export function SettingsScreen() {
  const settings = useStore((s) => s.settings)
  const members = useStore((s) => s.data?.members ?? [])
  const [lockAvailable, setLockAvailable] = useState(false)
  const [pendingImport, setPendingImport] = useState<File | null>(null)
  const [version, setVersion] = useState('')
  const [accountSheet, setAccountSheet] = useState<'password' | 'delete' | null>(null)
  const [update, setUpdate] = useState<UpdateState>('idle')

  useEffect(() => {
    deviceLockAvailable().then(setLockAvailable)
    api<{ version: string }>('/api/session').then((s) => setVersion(s.version)).catch(() => {})
  }, [])

  const toggleLock = async (enable: boolean) => {
    if (!enable) return updateSettings({ lockCredential: null })
    if (!lockAvailable) return toast('Nessun metodo di sblocco disponibile sul telefono.', true)
    const credential = await registerDeviceLock()
    if (credential) updateSettings({ lockCredential: credential })
    // Passkeys need a host name: an address like 192.168.1.10 can't hold one.
    else toast('Blocco non attivato. Serve HTTPS con un nome (es. Tailscale), non un indirizzo IP.', true)
  }

  const toggleReminders = async (enable: boolean) => {
    if (!enable) return disablePush()
    if (!pushSupported()) return toast('Per i promemoria apri Money in HTTPS e installala come app.', true)
    if (!(await enablePush())) toast('Senza permesso per le notifiche i promemoria non possono arrivare.', true)
  }

  const exportBackup = async () => {
    try {
      const response = await fetch('/api/backup', { credentials: 'same-origin' })
      if (!response.ok) throw new Error()
      download(await response.blob(), `money-backup-${isoDate(today())}.json`)
      toast('Backup esportato', true)
    } catch {
      toast('Esportazione non riuscita', true)
    }
  }

  const importBackup = async (file: File) => {
    try {
      const { count } = await api<{ count: number }>('/api/backup', { method: 'POST', body: await file.text(), headers: { 'Content-Type': 'application/json' } })
      await refresh(true)
      toast(`Backup importato: ${count} movimenti`, true)
    } catch (e) {
      toast(`Importazione non riuscita: ${(e as Error).message}`, true)
    }
  }

  const checkUpdates = async () => {
    const registration = await navigator.serviceWorker?.getRegistration()
    if (update === 'available' && registration?.waiting) {
      navigator.serviceWorker.addEventListener('controllerchange', () => location.reload(), { once: true })
      registration.waiting.postMessage('skipWaiting')
      return
    }
    if (!registration) {
      location.reload()
      return
    }
    setUpdate('checking')
    try {
      await registration.update()
      await new Promise((r) => setTimeout(r, 1200))
      setUpdate(registration.waiting || registration.installing ? 'available' : 'upToDate')
    } catch {
      setUpdate('failed')
    }
  }

  const logout = async () => {
    // This phone stops getting the reminders of the account that leaves it.
    if (settings.remindersEnabled) await disablePush()
    await api('/api/logout', { method: 'POST' }).catch(() => {})
    location.reload()
  }

  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar title="Impostazioni" onBack={() => close('settings')} />
      <div className="scroll">
        <div className="col nav-pad" style={{ padding: '0 20px', gap: 10 }}>
          <FormLabel text="Aspetto" />
          <Segmented options={themeModes.map((m) => m.label)} selected={themeModes.findIndex((m) => m.id === settings.theme)}
            onSelect={(i) => updateSettings({ theme: themeModes[i].id })} />

          <div className="row">
            <FormLabel text="Colore principale" style={{ flex: 1 }} />
            {settings.accent !== Palette.LILAC && (
              <Bouncy onClick={() => updateSettings({ accent: Palette.LILAC })} className="t-label"
                style={{ color: 'var(--accentText)', marginTop: 6, padding: '6px 10px', borderRadius: 999 }}>Ripristina</Bouncy>
            )}
          </div>
          <ColorSwatches colors={paletteAll} selected={settings.accent} onSelect={(accent) => updateSettings({ accent })} />

          <FormLabel text="Privacy" />
          <SwitchRow title="Blocco con impronta" checked={settings.lockCredential != null} onChange={toggleLock}
            subtitle={lockAvailable ? "Chiede impronta, volto o PIN all'apertura." : 'Configura impronta o PIN nelle impostazioni del telefono.'} />

          <FormLabel text="Notifiche" />
          <SwitchRow title="Promemoria rinnovi" subtitle="Avviso prima di abbonamenti e spese fisse." checked={settings.remindersEnabled} onChange={toggleReminders} />

          <FormLabel text="Persone" />
          <MembersSection members={members} />

          <FormLabel text="Organizza" />
          <NavRow icon="category" title="Categorie e budget" subtitle="Sottocategorie, icone, colori e limiti mensili" onClick={() => navigate('categories')} />

          <FormLabel text="Backup" />
          <NavRow icon="cloud_upload" title="Esporta backup" subtitle="Salva tutti i dati in un file JSON" onClick={exportBackup} />
          <NavRow icon="cloud_download" title="Importa backup" subtitle="Sostituisce i dati con quelli del file" onClick={() => {
            const input = document.createElement('input')
            input.type = 'file'
            input.accept = 'application/json,.json,text/plain'
            input.onchange = () => { if (input.files?.[0]) setPendingImport(input.files[0]) }
            input.click()
          }} />
          <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>
            Le foto degli scontrini e i loghi personalizzati non sono inclusi nel backup. Il file è compatibile con l'app Android.
          </span>

          <FormLabel text="Intelligenza artificiale" />
          <NavRow icon="auto_awesome" title="Assistente AI" subtitle="Cloudflare Workers AI: commento del mese, chat, categorie automatiche" onClick={() => navigate('aiSettings')} />

          <FormLabel text="Aggiornamenti" />
          <NavRow icon="system_update" title="Controlla aggiornamenti" onClick={checkUpdates}
            subtitle={
              update === 'checking' ? 'Controllo in corso…'
                : update === 'upToDate' ? `Hai l'ultima versione (${version})`
                  : update === 'available' ? 'Nuova versione disponibile: tocca per aggiornare'
                    : update === 'failed' ? 'Controllo non riuscito'
                      : `Versione installata: ${version}`
            } />

          <FormLabel text="Account" />
          <NavRow icon="lock" title="Cambia password" subtitle={`Sei entrato come ${loggedUser()}`} onClick={() => setAccountSheet('password')} />
          <NavRow icon="logout" title="Esci" subtitle="Su questo dispositivo servirà di nuovo la password" onClick={logout} />
          <NavRow icon="delete" title="Elimina account" subtitle="Cancella per sempre i tuoi movimenti, le spese fisse e gli scontrini"
            onClick={() => setAccountSheet('delete')} />

          <div style={{ height: 16 }} />
          <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>
            Money {version} · Font Bricolage Grotesque (OFL) · Loghi Simple Icons (CC0), marchi dei rispettivi proprietari.
          </span>
          <div style={{ height: 24 }} />
        </div>
      </div>
      {accountSheet && <AccountSheet kind={accountSheet} onDismiss={() => setAccountSheet(null)} />}
      {pendingImport && (
        <AlertDialog title="Importare il backup?" text="Tutti i movimenti, le categorie e le spese fisse attuali verranno sostituiti."
          confirm="Importa" confirmColor="var(--expenseText)" onDismiss={() => setPendingImport(null)}
          onConfirm={() => {
            importBackup(pendingImport)
            setPendingImport(null)
          }} />
      )}
    </div>
  )
}

/** Change password, or delete the account with all its data (asks the password again). */
function AccountSheet({ kind, onDismiss }: { kind: 'password' | 'delete'; onDismiss: () => void }) {
  const [current, setCurrent] = useState('')
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const remove = kind === 'delete'
  const ready = !busy && !!current && (remove || (!!password && !!repeat))

  const submit = async () => {
    if (!remove && password !== repeat) return setError('Le due password non coincidono.')
    setBusy(true)
    setError(null)
    try {
      if (remove) {
        const user = loggedUser()
        await disablePush()
        await api('/api/account/delete', { method: 'POST', json: { password: current } })
        forgetUser(user)
        location.reload()
        return
      }
      await api('/api/account/password', { method: 'POST', json: { current, password } })
      toast('Password cambiata', true)
      onDismiss()
    } catch (e) {
      setError((e as Error).message)
    }
    setBusy(false)
  }

  return (
    <Sheet onDismiss={onDismiss}>
      <form className="col" style={{ padding: '0 20px 32px', gap: 10 }} onSubmit={(e) => { e.preventDefault(); if (ready) submit() }}>
        <span className="t-title" style={{ color: 'var(--ink)' }}>{remove ? 'Eliminare l’account?' : 'Cambia password'}</span>
        {remove && (
          <span className="t-body" style={{ color: 'var(--inkSoft)' }}>
            Movimenti, spese fisse, categorie e scontrini di {loggedUser()} verranno cancellati per sempre. Se vuoi tenerli, esporta prima un backup.
          </span>
        )}
        <TextField value={current} onChange={setCurrent} placeholder={remove ? 'Password' : 'Password attuale'} secret autoComplete="current-password" />
        {!remove && (
          <>
            <TextField value={password} onChange={setPassword} placeholder="Nuova password" secret autoComplete="new-password" />
            <TextField value={repeat} onChange={setRepeat} placeholder="Ripeti la nuova password" secret autoComplete="new-password" />
          </>
        )}
        {error && <span className="t-label" style={{ color: 'var(--expenseText)' }}>{error}</span>}
        <div style={{ height: 10 }} />
        <PrimaryButton text={remove ? 'Elimina per sempre' : 'Salva'} icon={remove ? 'delete' : 'check'} enabled={ready} onClick={submit}
          color={remove ? 'var(--expense)' : undefined} />
        <button type="submit" style={{ display: 'none' }} />
      </form>
    </Sheet>
  )
}

function NavRow({ icon, title, subtitle, onClick }: { icon: UiIcon; title: string; subtitle: string; onClick: () => void }) {
  return (
    <Bouncy pressedScale={0.98} onClick={onClick} className="row" style={{ borderRadius: 22, background: 'var(--surface)', padding: '16px 18px' }}>
      <Icon name={icon} size={22} color="var(--accentText)" />
      <div className="col flex1" style={{ marginLeft: 14 }}>
        <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>{title}</span>
        <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{subtitle}</span>
      </div>
      <Icon name="keyboard_arrow_right" color="var(--inkFaint)" />
    </Bouncy>
  )
}

// --- people of the household ------------------------------------------------------------------

/** Round badge with the person's initial. */
export function MemberAvatar({ member, size = 40 }: { member: Member; size?: number }) {
  return (
    <div className="center t-bodyStrong" style={{
      width: size, height: size, borderRadius: '50%', background: argb(member.color), color: Ink, flexShrink: 0,
      fontSize: Math.min(16.5, size * 0.45),
    }}>
      {member.name.slice(0, 1).toUpperCase()}
    </div>
  )
}

function MembersSection({ members }: { members: Member[] }) {
  const [editing, setEditing] = useState<Member | null>(null)
  return (
    <>
      {members.map((m) => (
        <Bouncy key={m.id} pressedScale={0.98} onClick={() => setEditing(m)} className="row"
          style={{ borderRadius: 22, background: 'var(--surface)', padding: '12px 18px' }}>
          <MemberAvatar member={m} />
          <span className="t-bodyStrong flex1" style={{ marginLeft: 14, color: 'var(--ink)' }}>{m.name}</span>
          <Icon name="keyboard_arrow_right" color="var(--inkFaint)" />
        </Bouncy>
      ))}
      <Bouncy pressedScale={0.98} className="row" style={{ borderRadius: 22, background: 'var(--surface)', padding: '16px 18px' }}
        onClick={() => setEditing({ id: 0, name: '', color: paletteAll[members.length % paletteAll.length], sortOrder: 0 })}>
        <Icon name="add" size={22} color="var(--accentText)" />
        <div className="col flex1" style={{ marginLeft: 14 }}>
          <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>Aggiungi persona</span>
          <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>
            {members.length === 0 ? 'Per indicare di chi è ogni spesa o entrata, ad esempio in famiglia' : 'Ogni spesa o entrata andrà assegnata a una persona'}
          </span>
        </div>
      </Bouncy>
      {editing && (
        <MemberEditor original={editing} onDismiss={() => setEditing(null)}
          onSave={(m) => { setEditing(null); actions.saveMember(m).catch((e) => toast(e.message, true)) }}
          onDelete={(m) => { setEditing(null); actions.deleteMember(m).catch((e) => toast(e.message, true)) }} />
      )}
    </>
  )
}

function MemberEditor({ original, onDismiss, onSave, onDelete }: {
  original: Member
  onDismiss: () => void
  onSave: (m: Member) => void
  onDelete: (m: Member) => void
}) {
  const [name, setName] = useState(original.name)
  const [color, setColor] = useState(original.color)
  const [confirmDelete, setConfirmDelete] = useState(false)
  return (
    <Sheet onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 32px', gap: 2 }}>
        <div className="row">
          <MemberAvatar member={{ ...original, name: name.trim() || '?', color }} size={64} />
          <span className="t-title" style={{ marginLeft: 14, color: 'var(--ink)' }}>{original.id === 0 ? 'Nuova persona' : 'Modifica persona'}</span>
        </div>
        <FormLabel text="Nome" />
        <NoteField value={name} onChange={setName} placeholder="Es. Giulia" />
        <FormLabel text="Colore" />
        <ColorSwatches colors={paletteAll} selected={color} onSelect={setColor} />
        <div style={{ height: 24 }} />
        <PrimaryButton text="Salva" icon="check" enabled={!!name.trim()} color={argb(color)} onClick={() => onSave({ ...original, name: name.trim(), color })} />
        {original.id !== 0 && (
          <Bouncy onClick={() => setConfirmDelete(true)} className="t-bodyStrong"
            style={{ color: 'var(--expenseText)', textAlign: 'center', padding: 14, borderRadius: 999 }}>Elimina persona</Bouncy>
        )}
      </div>
      {confirmDelete && (
        <AlertDialog title={`Eliminare ${original.name}?`} text="I suoi movimenti restano, ma non saranno più assegnati a nessuno."
          confirm="Elimina" confirmColor="var(--expenseText)" onDismiss={() => setConfirmDelete(false)}
          onConfirm={() => { setConfirmDelete(false); onDelete(original) }} />
      )}
    </Sheet>
  )
}
