import { argb, Palette } from '../../../shared/model.ts'
import { Ink } from '../lib/theme.ts'
import { BlobBox } from '../components/Blob.tsx'
import { Icon, PrimaryButton } from '../components/ui.tsx'
import { useEffect } from 'react'

// The fingerprint lock uses WebAuthn with the phone's own unlock (fingerprint, face or PIN).

const b64 = (bytes: ArrayBuffer) => btoa(String.fromCharCode(...new Uint8Array(bytes)))
const unb64 = (text: string) => Uint8Array.from(atob(text), (c) => c.charCodeAt(0))
const random = (n: number) => crypto.getRandomValues(new Uint8Array(n))

export async function deviceLockAvailable(): Promise<boolean> {
  try {
    return window.isSecureContext && !!window.PublicKeyCredential &&
      await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()
  } catch {
    return false
  }
}

/** Asks for fingerprint or PIN once and returns the credential to use for unlocking. */
export async function registerDeviceLock(): Promise<string | null> {
  try {
    const credential = await navigator.credentials.create({
      publicKey: {
        challenge: random(32),
        rp: { name: 'Money', id: location.hostname },
        user: { id: random(16), name: 'money', displayName: 'Money' },
        pubKeyCredParams: [{ type: 'public-key', alg: -7 }, { type: 'public-key', alg: -257 }],
        authenticatorSelection: { authenticatorAttachment: 'platform', userVerification: 'required', residentKey: 'discouraged' },
        timeout: 60_000,
      },
    }) as PublicKeyCredential | null
    return credential ? b64(credential.rawId) : null
  } catch {
    return null
  }
}

export async function unlockWithDevice(credential: string | null): Promise<boolean> {
  if (!credential || !(await deviceLockAvailable())) return true
  try {
    const result = await navigator.credentials.get({
      publicKey: {
        challenge: random(32),
        allowCredentials: [{ type: 'public-key', id: unb64(credential) }],
        userVerification: 'required',
        timeout: 60_000,
      },
    })
    return result != null
  } catch {
    return false
  }
}

export function LockScreen({ onUnlockRequest }: { onUnlockRequest: () => void }) {
  useEffect(() => { onUnlockRequest() }, []) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div className="center" style={{ position: 'relative', height: '100%', background: 'var(--bg)' }}>
      <div className="col" style={{ alignItems: 'center' }}>
        <BlobBox size={200} gradient={['var(--accent)', argb(Palette.ROSE)]} seed={9} amplitude={0.1}>
          <Icon name="fingerprint" size={84} color={Ink} />
        </BlobBox>
        <span className="t-title" style={{ marginTop: 24, color: 'var(--ink)' }}>Money è bloccata</span>
        <span className="t-body" style={{ color: 'var(--inkSoft)' }}>I tuoi conti restano tra te e te.</span>
      </div>
      <div className="nav-pad" style={{ position: 'absolute', left: 0, right: 0, bottom: 0 }}>
        <div style={{ padding: 24 }}>
          <PrimaryButton text="Sblocca" onClick={onUnlockRequest} icon="fingerprint" />
        </div>
      </div>
    </div>
  )
}
