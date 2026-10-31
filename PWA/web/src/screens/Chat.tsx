import { AnimatePresence, motion } from 'framer-motion'
import { Fragment, useEffect, useRef, useState, type CSSProperties } from 'react'
import { api } from '../lib/api.ts'
import { close, navigate } from '../lib/nav.ts'
import { VoiceRecorder } from '../lib/voice.ts'
import { Ink } from '../lib/theme.ts'
import { BlobBox, BlobLayer } from '../components/Blob.tsx'
import { Bouncy, EmptyState, Icon, Pill, PrimaryButton, RoundIconButton, TopBar } from '../components/ui.tsx'
import { spring } from '../components/motion.ts'

const starters = [
  "Com'è andato questo mese?",
  'Dove posso risparmiare?',
  'Quanto ho speso in cibo negli ultimi 3 mesi?',
  'Quanto mi costano gli abbonamenti in un anno?',
  'In quale mese ho speso di più?',
]

/** voice: the user dictated it; only changes how the bubble looks, never what is sent. */
type ChatMessage = { role: 'user' | 'assistant'; content: string; voice?: boolean }

/** Renders the small markdown subset the model is asked to use: **bold** and "- " bullets. */
export function AiText({ text, className, style }: { text: string; className?: string; style?: CSSProperties }) {
  const lines = text.trim().split('\n').map((line) => {
    const trimmed = line.trimStart()
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) return '•  ' + trimmed.slice(2)
    if (trimmed.startsWith('#')) return '**' + trimmed.replace(/^#+/, '').trim() + '**'
    return line.trimEnd()
  })
  return (
    <span className={className} style={{ whiteSpace: 'pre-wrap', ...style }}>
      {lines.map((line, index) => (
        <Fragment key={index}>
          {line.split('**').map((part, i) => (i % 2 === 1 ? <b key={i}>{part}</b> : <Fragment key={i}>{part}</Fragment>))}
          {index < lines.length - 1 && '\n'}
        </Fragment>
      ))}
    </span>
  )
}

/** Three blobs bouncing while the model thinks. */
export function ThinkingDots({ color, style }: { color: string; style?: CSSProperties }) {
  return (
    <div className="row" style={{ gap: 6, ...style }}>
      {[0, 1, 2].map((i) => (
        <motion.div key={i} style={{ width: 9, height: 9, borderRadius: '50%', background: color }}
          initial={{ y: 0, opacity: 0.4 }} animate={{ y: -8, opacity: 1 }}
          transition={{ duration: 0.42, ease: 'linear', repeat: Infinity, repeatType: 'reverse', delay: (i * 140) / 1000 }} />
      ))}
    </div>
  )
}

export function ChatScreen() {
  const [connected, setConnected] = useState<boolean | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [input, setInput] = useState('')
  const [recording, setRecording] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [level, setLevel] = useState(0)
  const recorder = useRef<VoiceRecorder | null>(null)
  const discard = useRef(false)
  const scroller = useRef<HTMLDivElement>(null)

  useEffect(() => {
    api<{ connected: boolean }>('/api/ai').then((s) => setConnected(s.connected)).catch(() => setConnected(false))
  }, [])

  useEffect(() => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight, behavior: 'smooth' })
  }, [messages.length, busy])

  const send = async (text: string, voice = false) => {
    const question = text.trim()
    if (!question || busy) return
    const history = messages.map(({ role, content }) => ({ role, content }))
    setMessages((m) => [...m, { role: 'user', content: question, voice }])
    setError(null)
    setBusy(true)
    try {
      const { text: answer } = await api<{ text: string }>('/api/ai/ask', { method: 'POST', json: { history, question } })
      setMessages((m) => [...m, { role: 'assistant', content: answer }])
    } catch (e) {
      setError((e as Error).message)
    }
    setBusy(false)
  }

  /** Records until stopped; the transcript is then sent like a typed question. */
  const startRecording = async () => {
    if (recording || transcribing || busy) return
    setError(null)
    discard.current = false
    const r = new VoiceRecorder()
    r.onLevel = setLevel
    recorder.current = r
    setRecording(true)
    let wav: Blob
    try {
      wav = await r.record()
    } catch (e) {
      setRecording(false)
      setError((e as Error).message)
      return
    }
    setRecording(false)
    if (discard.current) return
    if (VoiceRecorder.seconds(wav) < 1) {
      setError('Vocale troppo corto: tieni il microfono acceso almeno un secondo')
      return
    }
    setTranscribing(true)
    try {
      const { text } = await api<{ text: string }>('/api/ai/transcribe', { method: 'POST', body: wav, headers: { 'Content-Type': 'audio/wav' } })
      setTranscribing(false)
      send(text, true)
    } catch (e) {
      setTranscribing(false)
      setError((e as Error).message)
    }
  }

  const submit = () => {
    send(input)
    setInput('')
  }

  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar title="Chiedi a Money" onBack={() => close('chat')} actions={
        messages.length > 0 ? <RoundIconButton icon="delete_sweep" onClick={() => { setMessages([]); setError(null) }} label="Nuova chat" /> : undefined
      } />
      {connected === false && (
        <div style={{ padding: 20 }}>
          <EmptyState title="AI non collegata" icon="savings" text="Inserisci Account ID e token di Cloudflare Workers AI per fare domande sulle tue spese." />
          <PrimaryButton text="Configura" onClick={() => navigate('aiSettings')} icon="auto_awesome" />
        </div>
      )}
      {connected && (
        <>
          <div ref={scroller} className="scroll">
            <div className="col" style={{ padding: '8px 16px', gap: 10 }}>
              <div className="col" style={{ alignItems: 'center', padding: '12px 0' }}>
                <BlobBox size={84} color="var(--accent)" seed={61} amplitude={0.1}>
                  <Icon name="auto_awesome" size={38} color={Ink} />
                </BlobBox>
                <span className="t-titleSmall" style={{ marginTop: 10, color: 'var(--ink)' }}>Chiedimi qualcosa sui tuoi soldi</span>
                <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>Leggo i movimenti degli ultimi 12 mesi.</span>
                {messages.length === 0 && (
                  <div className="flow" style={{ justifyContent: 'center', marginTop: 16 }}>
                    {starters.map((s) => <Pill key={s} text={s} selected={false} onClick={() => send(s)} />)}
                  </div>
                )}
              </div>
              {messages.map((m, i) => <Bubble key={i} message={m} />)}
              {busy && (
                <div style={{ alignSelf: 'flex-start', borderRadius: '22px 22px 22px 6px', background: 'var(--surface)', padding: '16px 18px' }}>
                  <ThinkingDots color="var(--accentText)" />
                </div>
              )}
              {error && <span className="t-caption" style={{ color: 'var(--expenseText)', paddingLeft: 6 }}>{error}</span>}
            </div>
          </div>
          {recording || transcribing ? (
            <VoiceBar recording={recording} level={level}
              onCancel={() => { discard.current = true; recorder.current?.stop() }}
              onSend={() => recorder.current?.stop()} />
          ) : (
            <div className="row nav-pad" style={{ alignItems: 'flex-end', padding: '10px 12px' }}>
              <label style={{ flex: 1, borderRadius: 26, background: 'var(--surface)', padding: '15px 18px', cursor: 'text' }}>
                <textarea className="field t-body" rows={1} value={input} placeholder="Scrivi o manda un vocale…" autoCapitalize="sentences" enterKeyHint="send"
                  style={{ display: 'block', maxHeight: 5 * 23 }}
                  onChange={(e) => {
                    setInput(e.target.value)
                    e.target.style.height = '0px'
                    e.target.style.height = e.target.scrollHeight + 'px'
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault()
                      submit()
                    }
                  }} />
              </label>
              <div style={{ width: 8 }} />
              <AnimatePresence>
                {!busy && (
                  <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                    <Bouncy pressedScale={0.85} onClick={() => (input.trim() ? submit() : startRecording())} className="center blob-box" style={{ width: 52, height: 52 }}
                      aria-label={input.trim() ? 'Invia' : 'Registra un vocale'}>
                      <BlobLayer color="var(--accent)" seed={8} amplitude={0.08} />
                      <div className="blob-content center">
                        <AnimatePresence mode="wait" initial={false}>
                          <motion.div key={input.trim() ? 'send' : 'mic'} initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.8 }}>
                            <Icon name={input.trim() ? 'send' : 'mic'} size={22} color={Ink} />
                          </motion.div>
                        </AnimatePresence>
                      </div>
                    </Bouncy>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          )}
        </>
      )}
    </div>
  )
}

/** Replaces the input row while dictating: live level blob, timer, cancel and send. */
function VoiceBar({ recording, level, onCancel, onSend }: { recording: boolean; level: number; onCancel: () => void; onSend: () => void }) {
  const [seconds, setSeconds] = useState(0)
  useEffect(() => {
    setSeconds(0)
    if (!recording) return
    const timer = setInterval(() => setSeconds((s) => s + 1), 1000)
    return () => clearInterval(timer)
  }, [recording])
  return (
    <div className="row nav-pad" style={{ padding: '10px 12px' }}>
      <div className="row flex1" style={{ borderRadius: 26, background: 'var(--surface)', padding: '15px 18px' }}>
        {recording ? (
          <>
            <motion.div animate={{ scale: 1 + level * 1.6 }} transition={spring(0.5, 600)}>
              <BlobBox size={14} color="var(--expense)" seed={4} amplitude={0.12} />
            </motion.div>
            <span className="t-bodyStrong" style={{ marginLeft: 12, color: 'var(--ink)' }}>{Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}</span>
            <span className="t-body flex1" style={{ marginLeft: 10, color: 'var(--inkSoft)' }}>Ti ascolto…</span>
            <Bouncy onClick={onCancel} className="t-label" style={{ color: 'var(--expenseText)', padding: '4px 6px', borderRadius: 999 }}>Annulla</Bouncy>
          </>
        ) : (
          <>
            <ThinkingDots color="var(--accentText)" />
            <span className="t-body" style={{ marginLeft: 12, color: 'var(--inkSoft)' }}>Trascrivo il vocale…</span>
          </>
        )}
      </div>
      {recording && (
        <>
          <div style={{ width: 8 }} />
          <Bouncy pressedScale={0.85} onClick={onSend} className="center blob-box" style={{ width: 52, height: 52 }} aria-label="Invia vocale">
            <BlobLayer color="var(--accent)" seed={8} amplitude={0.08} />
            <div className="blob-content center"><Icon name="send" size={22} color={Ink} /></div>
          </Bouncy>
        </>
      )}
    </div>
  )
}

function Bubble({ message }: { message: ChatMessage }) {
  const mine = message.role === 'user'
  return (
    <div className="row" style={{ justifyContent: mine ? 'flex-end' : 'flex-start' }}>
      <motion.div initial={{ scale: 0.85 }} animate={{ scale: 1 }} transition={spring(0.55, 500)}
        style={{
          maxWidth: 320, transformOrigin: mine ? '100% 100%' : '0% 100%', padding: '12px 16px',
          borderRadius: mine ? '22px 22px 6px 22px' : '22px 22px 22px 6px',
          background: mine ? 'var(--accent)' : 'var(--surface)', color: mine ? Ink : 'var(--ink)',
          userSelect: 'text', WebkitUserSelect: 'text',
        }}>
        {mine
          ? <span className="t-body" style={{ whiteSpace: 'pre-wrap' }}>{message.voice ? `🎙️ ${message.content}` : message.content}</span>
          : <AiText text={message.content} className="t-body" />}
      </motion.div>
    </div>
  )
}
