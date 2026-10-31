import { animate, AnimatePresence, motion, useMotionValue, useMotionValueEvent } from 'framer-motion'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { argb, paletteAll, Palette } from '../../../shared/model.ts'
import { onFrame } from '../lib/blob.ts'
import { useBackHandler } from '../lib/nav.ts'
import { enablePush, pushSupported } from '../lib/push.ts'
import { themeModes, updateSettings, useStore } from '../lib/store.ts'
import { Ink } from '../lib/theme.ts'
import type { UiIcon } from '../lib/icons.gen.ts'
import { BackdropBlobs, BlobBox, PX } from '../components/Blob.tsx'
import { Bouncy, ColorSwatches, FormLabel, Icon, PrimaryButton, Segmented } from '../components/ui.tsx'
import { spring } from '../components/motion.ts'

type Page = { icon: UiIcon; color: number; title: string; text: string; chips?: string[] }

const pages: Page[] = [
  { icon: 'savings', color: Palette.LILAC, title: 'Ciao, sono Money', text: "Spese ed entrate senza fatica, e con un po' di colore. Tutto resta sul tuo server." },
  {
    icon: 'touch_app', color: Palette.MINT, title: 'Segna in 3 tocchi',
    text: 'Importo, categoria, fatto. Se vuoi aggiungi nota, metodo di pagamento e foto dello scontrino.',
    chips: ['Tastierino rapido', 'Sottocategorie', 'Scontrini'],
  },
  {
    icon: 'autorenew', color: Palette.PEACH, title: 'Fisse e abbonamenti',
    text: 'Affitto, Netflix, palestra: li registro io alla scadenza e ti avviso prima del rinnovo.',
    chips: ['Loghi pronti', 'Promemoria', 'Automatiche'],
  },
  {
    icon: 'donut_large', color: Palette.BUTTER, title: "Il tuo mese, a colpo d'occhio",
    text: 'Pagella da A a F, grafici, calendario e budget. Esporta in PDF o come immagine.',
    chips: ['Pagella', 'Trend 6 mesi', 'Budget'],
  },
  {
    icon: 'auto_awesome', color: Palette.SKY, title: 'Un assistente che ti capisce',
    text: "Collega l'AI da Impostazioni: commenta il mese, risponde sulle tue spese e sceglie la categoria per te.",
    chips: ['Consigli', 'Chat', 'Categoria automatica'],
  },
]

/** Index of the final "make it yours" page, after the feature pages. */
const lastPage = pages.length

export function OnboardingScreen({ onDone }: { onDone: () => void }) {
  const [page, setPage] = useState(0)
  const pager = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const x = useMotionValue(0)
  const [offset, setOffset] = useState(0)
  useMotionValueEvent(x, 'change', (v) => setOffset(width ? -v / width : 0))

  useLayoutEffect(() => {
    const el = pager.current!
    const update = () => setWidth(el.offsetWidth)
    update()
    const observer = new ResizeObserver(update)
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  useEffect(() => { x.set(-page * width) }, [width]) // eslint-disable-line react-hooks/exhaustive-deps

  const goTo = (target: number) => {
    const p = Math.max(0, Math.min(lastPage, target))
    setPage(p)
    animate(x, -p * width, spring(1, 400))
  }

  useBackHandler(page > 0, () => goTo(page - 1))

  const finish = async () => {
    if (pushSupported() && Notification.permission === 'default') await enablePush()
    onDone()
  }

  const dark = document.documentElement.style.colorScheme === 'dark'
  const onLast = page === lastPage
  return (
    <div style={{ position: 'relative', height: '100%', background: 'var(--bg)', overflow: 'hidden' }}>
      <BackdropBlobs colors={['var(--accent)', 'var(--income)', argb(Palette.ROSE)]} alpha={dark ? 0.1 : 0.22} />
      <div className="col status-pad nav-pad" style={{ position: 'relative', height: '100%' }}>
        <div className="row" style={{ height: 56, padding: '0 20px', flexShrink: 0 }}>
          <span className="t-titleSmall flex1" style={{ color: 'var(--ink)' }}>Money</span>
          <Bouncy onClick={() => goTo(lastPage)} disabled={onLast} className="t-label"
            style={{ color: 'var(--inkSoft)', padding: '8px 12px', borderRadius: 999, opacity: onLast ? 0 : 1 }}>Salta</Bouncy>
        </div>

        <div ref={pager} style={{ flex: 1, overflow: 'hidden', position: 'relative', touchAction: 'pan-y' }}>
          <motion.div
            className="row"
            style={{ x, height: '100%', width: width * (lastPage + 1), alignItems: 'stretch' }}
            drag="x"
            dragConstraints={{ left: -lastPage * width, right: 0 }}
            dragElastic={0.15}
            dragMomentum={false}
            onDragEnd={(_, info) => {
              const fraction = -x.get() / width
              let target = Math.round(fraction)
              if (info.velocity.x < -300) target = Math.ceil(fraction)
              else if (info.velocity.x > 300) target = Math.floor(fraction)
              goTo(target)
            }}
          >
            {[...pages, null].map((p, index) => {
              const distance = Math.min(1, Math.abs(offset - index))
              return (
                <div key={index} style={{ width, height: '100%', opacity: 1 - 0.8 * distance, transform: `scale(${1 - 0.1 * distance})` }}>
                  {p ? <FeaturePage page={p} index={index} /> : <PersonalizePage />}
                </div>
              )
            })}
          </motion.div>
        </div>

        <div className="row" style={{ justifyContent: 'center', gap: 6, padding: '16px 0' }}>
          {Array.from({ length: lastPage + 1 }, (_, i) => (
            <motion.div key={i} initial={false} animate={{ width: i === page ? 26 : 8 }} transition={spring(0.6, 400)}
              style={{ height: 8, borderRadius: 4, background: i === page ? 'var(--accent)' : 'color-mix(in srgb, var(--inkFaint) 50%, transparent)' }} />
          ))}
        </div>

        <div style={{ padding: '0 24px 16px', position: 'relative', height: 74 }}>
          <AnimatePresence initial={false}>
            <motion.div key={onLast ? 'start' : 'next'} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
              style={{ position: 'absolute', left: 24, right: 24, top: 0 }}>
              {onLast
                ? <PrimaryButton text="Iniziamo" onClick={finish} icon="arrow_forward" />
                : <PrimaryButton text="Avanti" onClick={() => goTo(page + 1)} color="var(--surfaceHigh)" />}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}

function FeaturePage({ page, index }: { page: Page; index: number }) {
  const orbit = useRef<HTMLDivElement>(null)
  // A small companion blob orbits the main one, each page with its own shape.
  useEffect(() => onFrame((time) => {
    if (orbit.current) orbit.current.style.transform = `translate(${10 * PX * Math.sin(time * 0.9 + index)}px, ${14 * PX * Math.sin(time * 1.3 + index)}px)`
  }), [index])
  return (
    <div className="col" style={{ height: '100%', padding: '0 28px', alignItems: 'center', justifyContent: 'center' }}>
      <div className="center" style={{ position: 'relative', width: 260, height: 260 }}>
        <div ref={orbit} style={{ position: 'absolute', top: 0, right: 0 }}>
          <BlobBox size={70} color={argb(Palette.ROSE, 0.8)} seed={40 + index} amplitude={0.12} />
        </div>
        <BlobBox size={210} gradient={[argb(page.color), 'var(--accent)']} seed={20 + index} amplitude={0.1}>
          <Icon name={page.icon} size={88} color={Ink} />
        </BlobBox>
      </div>
      <span className="t-display" style={{ marginTop: 36, textAlign: 'center', color: 'var(--ink)' }}>{page.title}</span>
      <span className="t-body" style={{ marginTop: 14, textAlign: 'center', color: 'var(--inkSoft)' }}>{page.text}</span>
      {page.chips && (
        <div className="flow" style={{ marginTop: 20, justifyContent: 'center' }}>
          {page.chips.map((chip) => (
            <span key={chip} className="t-caption" style={{ color: Ink, background: argb(page.color), borderRadius: 999, padding: '6px 12px', whiteSpace: 'nowrap' }}>{chip}</span>
          ))}
        </div>
      )}
    </div>
  )
}

function PersonalizePage() {
  const settings = useStore((s) => s.settings)
  return (
    <div className="col" style={{ height: '100%', padding: '0 24px', justifyContent: 'center' }}>
      <BlobBox size={120} color="var(--accent)" seed={31} amplitude={0.1} speed={1.3} style={{ alignSelf: 'center' }}>
        <Icon name="palette" size={52} color={Ink} />
      </BlobBox>
      <span className="t-display" style={{ marginTop: 28, textAlign: 'center', color: 'var(--ink)' }}>Fallo tuo</span>
      <span className="t-body" style={{ marginTop: 10, textAlign: 'center', color: 'var(--inkSoft)' }}>
        Scegli tema e colore. Puoi cambiarli quando vuoi dalle Impostazioni.
      </span>
      <div style={{ height: 28 }} />
      <FormLabel text="Aspetto" />
      <div style={{ height: 8 }} />
      <Segmented options={themeModes.map((m) => m.label)} selected={themeModes.findIndex((m) => m.id === settings.theme)}
        onSelect={(i) => updateSettings({ theme: themeModes[i].id })} />
      <div style={{ height: 20 }} />
      <FormLabel text="Colore principale" />
      <div style={{ height: 4 }} />
      <ColorSwatches colors={paletteAll} selected={settings.accent} onSelect={(accent) => updateSettings({ accent })} />
    </div>
  )
}
