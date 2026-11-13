import { animate, AnimatePresence, motion, type HTMLMotionProps } from 'framer-motion'
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Dates, Money } from '../../../shared/format.ts'
import { argb, type Category } from '../../../shared/model.ts'
import type { Month } from '../../../shared/date.ts'
import { appIcons, uiIcons, type UiIcon } from '../lib/icons.gen.ts'
import { findBrand, isLightBrand } from '../lib/brands.ts'
import { hashCode } from '../lib/blob.ts'
import { getStore, updateSettings, useDueffe, useMetro, useNothing, useStore } from '../lib/store.ts'
import { accentShown, DarkInk, Ink, tile } from '../lib/theme.ts'
import { BlobBox, useBlobClip } from './Blob.tsx'
import { fastOutSlowIn, haptic, spring, tween } from './motion.ts'

/** The one easing of the Nothing style: subtle ease-out, no spring. */
export const NothingEase: [number, number, number, number] = [0.25, 0.1, 0.25, 1]

const nothingBorder = '1px solid color-mix(in srgb, var(--inkFaint) 60%, transparent)'

// --- icons -----------------------------------------------------------------------------------

export function Icon({ name, size = 24, color = 'currentColor', style, label }: {
  name: UiIcon
  size?: number
  color?: string
  style?: CSSProperties
  label?: string
}) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color} style={{ flexShrink: 0, fill: color, ...style }}
      role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}
      dangerouslySetInnerHTML={{ __html: uiIcons[name] }} />
  )
}

/** Icon keys stored in the database (AppIcons). */
export function AppIcon({ name, size = 24, color = 'currentColor', style }: { name: string | null | undefined; size?: number; color?: string; style?: CSSProperties }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color} style={{ flexShrink: 0, fill: color, ...style }} aria-hidden
      dangerouslySetInnerHTML={{ __html: appIcons[name ?? ''] ?? appIcons.more }} />
  )
}

// --- press feedback --------------------------------------------------------------------------

type BouncyProps = Omit<HTMLMotionProps<'div'>, 'onClick'> & {
  onClick?: () => void
  onLongClick?: () => void
  pressedScale?: number
  haptics?: boolean
  disabled?: boolean
}

/** Click with a springy press-down and a light haptic tick (Modifier.bouncyClick). */
export function Bouncy({ onClick, onLongClick, pressedScale = 0.92, haptics = true, disabled, style, children, ...rest }: BouncyProps) {
  const timer = useRef<number | null>(null)
  const longFired = useRef(false)
  const metro = useMetro()
  // Nothing: the control dims for a moment, like a mechanical key; no bounce.
  const nothing = useNothing()
  const [tilt, setTilt] = useState({ x: 0, y: 0 })
  const clear = () => {
    if (timer.current != null) window.clearTimeout(timer.current)
    timer.current = null
  }
  return (
    <motion.div
      {...rest}
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled}
      style={{ cursor: disabled ? 'default' : 'pointer', ...(metro ? { transformPerspective: 600 } : null), ...style }}
      whileTap={disabled ? undefined : nothing ? { opacity: 0.55 } : metro ? { scale: 0.96, rotateX: tilt.x, rotateY: tilt.y } : { scale: pressedScale }}
      transition={nothing ? tween(120, NothingEase) : metro ? tween(150) : spring(0.45, 500)}
      onPointerDown={(e) => {
        if (metro) {
          // Windows Phone tilt: leans toward the finger, big tiles less than small buttons.
          const r = e.currentTarget.getBoundingClientRect()
          const k = Math.min(1, Math.max(0.15, 60 / Math.max(r.width, r.height))) * 14
          setTilt({ x: -((e.clientY - r.top) / r.height - 0.5) * 2 * k, y: ((e.clientX - r.left) / r.width - 0.5) * 2 * k })
        }
        longFired.current = false
        if (onLongClick && !disabled) {
          timer.current = window.setTimeout(() => {
            longFired.current = true
            haptic(true)
            onLongClick()
          }, 500)
        }
      }}
      onPointerUp={clear}
      onPointerLeave={clear}
      onPointerCancel={clear}
      onContextMenu={onLongClick ? (e) => e.preventDefault() : undefined}
      onClick={() => {
        if (disabled || longFired.current) return
        if (haptics) haptic()
        onClick?.()
      }}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && !disabled) {
          e.preventDefault()
          onClick?.()
        }
      }}
    >
      {children}
    </motion.div>
  )
}

// --- badges and logos ------------------------------------------------------------------------

export function CategoryBadge({ icon, color, size = 44, seed, animated = false, style }: {
  icon: string
  color: string
  size?: number
  seed?: number
  animated?: boolean
  style?: CSSProperties
}) {
  if (useNothing()) {
    // Flat circle on the surface; the icon keeps the category colour, the one hint of colour in the list.
    return (
      <div className="center" style={{ width: size, height: size, borderRadius: '50%', background: 'var(--surfaceHigh)', flexShrink: 0, ...style }}>
        <AppIcon name={icon} size={size * 0.46} color={color === 'var(--accent)' ? 'var(--ink)' : `color-mix(in srgb, ${color} var(--iconMix), black)`} />
      </div>
    )
  }
  return (
    <BlobBox size={size} color={color} seed={seed ?? hashCode(icon)} amplitude={0.09} animated={animated} style={style}>
      <AppIcon name={icon} size={size * 0.46} color={Ink} />
    </BlobBox>
  )
}

/** Logo of a fixed expense: custom picture, preset brand, or category-style icon. */
export function RecurringLogo({ brandKey, imagePath, icon, color, size = 48, animated = false }: {
  brandKey: string | null
  imagePath: string | null
  icon: string
  color: string
  size?: number
  animated?: boolean
}) {
  const brand = findBrand(brandKey)
  const clip = useBlobClip(hashCode(imagePath ?? ''), size)
  const metro = useMetro()
  if (imagePath) {
    return <img src={imagePath} alt="" draggable={false} style={{ width: size, height: size, objectFit: 'cover', clipPath: metro ? undefined : clip, flexShrink: 0 }} />
  }
  if (brand) {
    const tint = isLightBrand(brand) ? DarkInk : '#FFFFFF'
    return (
      <BlobBox size={size} color={argb(brand.hex)} seed={hashCode(brand.key)} amplitude={0.08} animated={animated} keepColor>
        {brand.path ? (
          <svg width={size * 0.44} height={size * 0.44} viewBox="0 0 24 24" aria-label={brand.name}><path d={brand.path} fill={tint} /></svg>
        ) : (
          <span style={{
            color: tint, fontWeight: 800, fontSize: size * ((brand.monogram?.length ?? 1) > 2 ? 0.24 : 0.34),
            lineHeight: 1, whiteSpace: 'nowrap',
          }}>{brand.monogram}</span>
        )}
      </BlobBox>
    )
  }
  return <CategoryBadge icon={icon} color={color} size={size} animated={animated} />
}

// --- amounts ---------------------------------------------------------------------------------

/** "1.240" big plus ",50 €" small. */
export function AmountText({ cents, className, color = 'var(--ink)', signed = false, decimalsScale = 0.5, style }: {
  cents: number
  className: string
  color?: string
  signed?: boolean
  decimalsScale?: number
  style?: CSSProperties
}) {
  const [int, dec] = Money.parts(cents)
  const sign = signed && cents > 0 ? '+' : cents < 0 ? '−' : ''
  return (
    <span className={className} style={{ color, whiteSpace: 'nowrap', ...style }}>
      {sign + int}
      <span style={{ fontSize: `${decimalsScale}em`, opacity: 0.7 }}>{`${dec} €`}</span>
    </span>
  )
}

/** Counts up/down to [cents] whenever it changes. */
export function AnimatedAmount({ masked, ...props }: Parameters<typeof AmountText>[0] & { masked?: boolean }) {
  const [shown, setShown] = useState(0)
  const from = useRef(0)
  useEffect(() => {
    const controls = animate(from.current, props.cents, {
      duration: 0.9,
      ease: fastOutSlowIn,
      onUpdate: (v) => {
        from.current = v
        setShown(Math.trunc(v))
      },
      onComplete: () => setShown(props.cents),
    })
    return () => controls.stop()
  }, [props.cents])
  return (
    <Masked className={props.className ?? ''} color={props.color ?? 'var(--ink)'} hidden={masked}>
      <AmountText {...props} cents={shown} />
    </Masked>
  )
}

/** Shows [children], or soft dots in their place when amounts are hidden; the swap pops with a little scale. */
export function Masked({ className, color, hidden, children }: { className: string; color: string; hidden?: boolean; children: ReactNode }) {
  const hideAmounts = useStore((s) => s.settings.hideAmounts)
  const masked = hidden ?? hideAmounts
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.span key={masked ? 'dots' : 'amount'} style={{ display: 'inline-block' }}
        initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1, transition: spring(0.55, 500) }}
        exit={{ opacity: 0, scale: 0.6, transition: tween(120) }}>
        {masked ? <span className={className} style={{ color, opacity: 0.75, whiteSpace: 'nowrap' }}>••••</span> : children}
      </motion.span>
    </AnimatePresence>
  )
}

/** Text or dots when amounts are hidden, for amounts inside longer strings. */
export function useHideable() {
  const hidden = useStore((s) => s.settings.hideAmounts)
  return (text: string) => hidden ? '••••' : text
}

/** Account balances are hidden with every amount, or on their own. */
export const useBalancesHidden = () => useStore((s) => s.settings.hideAmounts || s.settings.hideBalances)

export const toggleHideBalances = () => updateSettings({ hideBalances: !getStore().settings.hideBalances })

export const toggleHideAmounts = () => updateSettings({ hideAmounts: !getStore().settings.hideAmounts })

/** Eye that hides or reveals amounts everywhere they are maskable. */
export function HideAmountsButton() {
  const hidden = useStore((s) => s.settings.hideAmounts)
  return <RoundIconButton icon={hidden ? 'visibility_off' : 'visibility'} onClick={toggleHideAmounts} label={hidden ? 'Mostra importi' : 'Nascondi importi'} />
}

// --- controls --------------------------------------------------------------------------------

export function Pill({ text, selected, onClick, color = 'var(--accent)', icon, appIcon, leading, style }: {
  text: string
  selected: boolean
  onClick: () => void
  color?: string
  icon?: UiIcon
  appIcon?: string
  leading?: ReactNode
  style?: CSSProperties
}) {
  const metro = useMetro()
  const nothing = useNothing()
  const dueffe = useDueffe()
  const fg = selected ? Ink : 'var(--ink)'
  if (dueffe) {
    // Dueffe chip: Surface pill with a line border, solid gold when selected.
    return (
      <Bouncy onClick={onClick} className="row" style={{
        borderRadius: 999, background: selected ? color : 'var(--surface)', padding: '8px 14px',
        border: `1px solid ${selected ? color : 'var(--line)'}`,
        transition: 'background-color 0.26s, color 0.26s, border-color 0.26s', color: fg, flexShrink: 0, ...style,
      }}>
        {leading && <span style={{ marginRight: 8, display: 'flex' }}>{leading}</span>}
        {icon && <Icon name={icon} size={16} style={{ marginRight: 6 }} />}
        {appIcon && <AppIcon name={appIcon} size={16} style={{ marginRight: 6 }} />}
        <span className="t-label" style={{ whiteSpace: 'nowrap' }}>{text}</span>
      </Bouncy>
    )
  }
  if (nothing) {
    return (
      <Bouncy onClick={onClick} className="row" style={{
        borderRadius: 999, background: selected ? 'var(--accent)' : 'transparent', padding: '7px 14px',
        border: selected ? '1px solid var(--accent)' : nothingBorder,
        transition: 'background-color 0.2s, color 0.2s', color: fg, flexShrink: 0, ...style,
      }}>
        {leading && <span style={{ marginRight: 8, display: 'flex' }}>{leading}</span>}
        {icon && <Icon name={icon} size={15} style={{ marginRight: 6 }} />}
        {appIcon && <AppIcon name={appIcon} size={15} style={{ marginRight: 6 }} />}
        <span className="t-label" style={{ whiteSpace: 'nowrap', textTransform: 'uppercase' }}>{text}</span>
      </Bouncy>
    )
  }
  if (metro) {
    return (
      <Bouncy onClick={onClick} className="row" style={{
        background: selected ? tile(color) : 'transparent', padding: '7px 14px',
        border: `2px solid ${selected ? 'transparent' : 'color-mix(in srgb, var(--ink) 35%, transparent)'}`,
        transition: 'background-color 0.2s', color: fg, flexShrink: 0, ...style,
      }}>
        {leading && <span style={{ marginRight: 8, display: 'flex' }}>{leading}</span>}
        {icon && <Icon name={icon} size={16} style={{ marginRight: 6 }} />}
        {appIcon && <AppIcon name={appIcon} size={16} style={{ marginRight: 6 }} />}
        <span className="t-label" style={{ whiteSpace: 'nowrap', fontWeight: 400, textTransform: 'lowercase' }}>{text}</span>
      </Bouncy>
    )
  }
  return (
    <Bouncy onClick={onClick} className="row" style={{
      borderRadius: 999, background: selected ? color : 'var(--surfaceHigh)', padding: '9px 14px',
      transition: 'background-color 0.25s, color 0.25s', color: fg, flexShrink: 0, ...style,
    }}>
      {leading && <span style={{ marginRight: 8, display: 'flex' }}>{leading}</span>}
      {icon && <Icon name={icon} size={16} style={{ marginRight: 6 }} />}
      {appIcon && <AppIcon name={appIcon} size={16} style={{ marginRight: 6 }} />}
      <span className="t-label" style={{ whiteSpace: 'nowrap' }}>{text}</span>
    </Bouncy>
  )
}

/** Pill selector with a springy sliding indicator. */
export function Segmented({ options, selected, onSelect, colors, style }: {
  options: string[]
  selected: number
  onSelect: (i: number) => void
  colors?: string[]
  style?: CSSProperties
}) {
  const metro = useMetro()
  if (useNothing()) {
    // Framed pill; the chosen segment is inverted (display ink behind paper-coloured text).
    return (
      <div style={{ borderRadius: 999, border: nothingBorder, padding: 3, ...style }}>
        <div style={{ position: 'relative', height: 38 }}>
          <motion.div initial={false} animate={{ x: `${selected * 100}%` }} transition={tween(220, NothingEase)}
            style={{ position: 'absolute', top: 0, left: 0, height: 38, width: `${100 / options.length}%`, borderRadius: 999, background: 'var(--accent)' }} />
          <div className="row" style={{ position: 'relative', height: 38 }}>
            {options.map((label, i) => (
              <div key={label} className="center flex1 t-caption" role="button" onClick={() => onSelect(i)}
                style={{ height: '100%', cursor: 'pointer', textTransform: 'uppercase', color: i === selected ? Ink : 'var(--inkSoft)', transition: 'color 0.2s', whiteSpace: 'nowrap' }}>
                {label}
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }
  if (metro) {
    // Square cells in a thin frame; the accent block glides under the chosen one.
    return (
      <div style={{ border: '2px solid color-mix(in srgb, var(--ink) 30%, transparent)', padding: 2, ...style }}>
        <div style={{ position: 'relative', height: 42 }}>
          <motion.div initial={false}
            animate={{ x: `${selected * 100}%`, backgroundColor: tile(colors?.[selected] ?? 'var(--accent)') }}
            transition={{ x: tween(260, [0, 0, 0.2, 1]), backgroundColor: tween(200) }}
            style={{ position: 'absolute', top: 0, left: 0, height: 42, width: `${100 / options.length}%` }} />
          <div className="row" style={{ position: 'relative', height: 42 }}>
            {options.map((label, i) => (
              <div key={label} className="center flex1 t-label" role="button" onClick={() => onSelect(i)}
                style={{ height: '100%', cursor: 'pointer', fontWeight: 400, textTransform: 'lowercase', color: i === selected ? '#FFFFFF' : 'var(--inkSoft)', transition: 'color 0.25s', whiteSpace: 'nowrap' }}>
                {label}
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }
  return (
    <div style={{ borderRadius: 999, background: 'var(--surfaceHigh)', padding: 4, ...style }}>
      <div style={{ position: 'relative', height: 40 }}>
        <motion.div
          initial={false}
          animate={{ x: `${selected * 100}%`, backgroundColor: colors?.[selected] ?? 'var(--accent)' }}
          transition={{ x: spring(0.7, 380), backgroundColor: tween(200) }}
          style={{ position: 'absolute', top: 0, left: 0, height: 40, width: `${100 / options.length}%`, borderRadius: 999 }}
        />
        <div className="row" style={{ position: 'relative', height: 40 }}>
          {options.map((label, i) => (
            <div key={label} className="center flex1 t-label" role="button" onClick={() => onSelect(i)}
              style={{ height: '100%', borderRadius: 999, cursor: 'pointer', color: i === selected ? Ink : 'var(--inkSoft)', transition: 'color 0.25s', whiteSpace: 'nowrap' }}>
              {label}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export function SoftCard({ children, color = 'var(--surface)', padding = 20, style, className }: {
  children: ReactNode
  color?: string
  padding?: number
  style?: CSSProperties
  className?: string
}) {
  useMetro()
  if (useDueffe()) {
    // Dueffe card: gradient Surface High to Surface, 1px gold line, radius 26; a colour keeps a solid fill for its dark Ink.
    const plain = color === 'var(--surface)'
    const background = plain ? 'linear-gradient(180deg, var(--surfaceHigh), var(--surface))'
      : `linear-gradient(180deg, ${color}, color-mix(in srgb, ${color} 88%, #000))`
    return (
      <div className={'col ' + (className ?? '')} style={{ borderRadius: 26, background, border: `1px solid ${plain ? 'var(--line)' : 'transparent'}`, padding, ...style } as CSSProperties}>
        {children}
      </div>
    )
  }
  if (useNothing()) {
    // Colour is an event, not a fill: a coloured card stays a flat surface with a signal dot, and its Ink text turns to ink.
    const plain = color === 'var(--surface)'
    return (
      <div className={'col ' + (className ?? '')} style={{ borderRadius: 16, background: 'var(--surface)', border: '1px solid var(--line)', padding, position: 'relative', ...(plain ? null : { '--Ink': 'var(--ink)' }), ...style } as CSSProperties}>
        {!plain && <span aria-hidden style={{ position: 'absolute', top: 12, right: 12, width: 8, height: 8, borderRadius: '50%', background: color }} />}
        {children}
      </div>
    )
  }
  return <div className={'col ' + (className ?? '')} style={{ borderRadius: 28, background: tile(color), padding, ...style }}>{children}</div>
}

export function SectionTitle({ text, action, onAction, style }: { text: string; action?: string; onAction?: () => void; style?: CSSProperties }) {
  const metro = useMetro()
  if (useNothing()) {
    // Instrument-panel label: Space Mono, ALL CAPS, secondary grey; the action in brackets.
    return (
      <div className="row" style={{ paddingTop: 18, paddingBottom: 10, ...style }}>
        <span className="t-label flex1" style={{ color: 'var(--inkSoft)', textTransform: 'uppercase', letterSpacing: '0.1em' }}>{text}</span>
        {action && onAction && (
          <button className="t-label" onClick={onAction} style={{ color: 'var(--ink)', padding: '6px 2px', textTransform: 'uppercase' }}>[ {action} ]</button>
        )}
      </div>
    )
  }
  if (metro) {
    return (
      <div className="row" style={{ paddingTop: 10, paddingBottom: 10, alignItems: 'flex-end', ...style }}>
        <span className="t-title flex1" style={{ color: 'var(--ink)', textTransform: 'lowercase' }}>{text}</span>
        {action && onAction && (
          <button className="t-label" onClick={onAction} style={{ color: 'var(--accentText)', padding: '6px 4px', fontWeight: 400, textTransform: 'lowercase' }}>{action}</button>
        )}
      </div>
    )
  }
  return (
    <div className="row" style={{ paddingTop: 8, paddingBottom: 12, ...style }}>
      <span className="t-titleSmall flex1" style={{ color: 'var(--ink)' }}>{text}</span>
      {action && onAction && (
        <button className="t-label" onClick={onAction} style={{ color: 'var(--accentText)', padding: '6px 10px', borderRadius: 999 }}>{action}</button>
      )}
    </div>
  )
}

export function RoundIconButton({ icon, onClick, background = 'var(--surfaceHigh)', tint = 'var(--ink)', size = 44, label, style }: {
  icon: UiIcon
  onClick: () => void
  background?: string
  tint?: string
  size?: number
  label?: string
  style?: CSSProperties
}) {
  const metro = useMetro()
  if (useDueffe()) {
    const custom = background !== 'var(--surfaceHigh)'
    return (
      <Bouncy onClick={onClick} className="center" aria-label={label}
        style={{ width: size, height: size, borderRadius: '50%', background: custom ? background : 'var(--surface)', border: custom ? 0 : '1px solid var(--line)', flexShrink: 0, ...style }}>
        <Icon name={icon} size={size * 0.5} color={tint} />
      </Bouncy>
    )
  }
  if (useNothing()) {
    const custom = background !== 'var(--surfaceHigh)'
    return (
      <Bouncy onClick={onClick} className="center" aria-label={label}
        style={{ width: size, height: size, borderRadius: '50%', background: custom ? background : 'var(--surface)', border: custom ? 0 : '1px solid var(--line)', flexShrink: 0, ...style }}>
        <Icon name={icon} size={size * 0.46} color={custom ? tint : 'var(--ink)'} />
      </Bouncy>
    )
  }
  if (metro) {
    // Application bar button: an icon in a thin circle, or a solid disc with a custom background.
    const custom = background !== 'var(--surfaceHigh)'
    return (
      <Bouncy onClick={onClick} className="center round" aria-label={label}
        style={{ width: size, height: size, background: custom ? tile(background) : 'transparent', border: custom ? 0 : '2px solid var(--ink)', flexShrink: 0, ...style }}>
        <Icon name={icon} size={size * 0.46} color={custom ? '#FFFFFF' : 'var(--ink)'} />
      </Bouncy>
    )
  }
  return (
    <Bouncy onClick={onClick} className="center" aria-label={label}
      style={{ width: size, height: size, borderRadius: '50%', background, flexShrink: 0, ...style }}>
      <Icon name={icon} size={size * 0.5} color={tint} />
    </Bouncy>
  )
}

export function TopBar({ title, onBack, actions }: { title: string; onBack?: () => void; actions?: ReactNode }) {
  const metro = useMetro()
  if (metro) {
    // Windows Phone page header: the app name in small capitals, then the title, big and lowercase.
    return (
      <div className="col status-pad" style={{ flexShrink: 0 }}>
        <div className="col" style={{ width: '100%', padding: '8px 16px 6px 20px' }}>
          <div className="row" style={{ gap: 12 }}>
            {onBack && <RoundIconButton icon="arrow_back" onClick={onBack} size={36} label="Indietro" />}
            <span className="t-label flex1" style={{ color: 'var(--ink)', letterSpacing: '0.08em' }}>MONEY</span>
            {actions}
          </div>
          <span className="t-hero" style={{ color: 'var(--ink)', fontSize: 50, lineHeight: '58px', textTransform: 'lowercase', whiteSpace: 'nowrap', overflow: 'hidden', marginTop: 2 }}>{title}</span>
        </div>
      </div>
    )
  }
  return (
    <div className="row status-pad" style={{ flexShrink: 0 }}>
      <div className="row" style={{ width: '100%', padding: '10px 16px', gap: 12 }}>
        {onBack && <RoundIconButton icon="arrow_back" onClick={onBack} label="Indietro" />}
        <span className="t-title flex1 ellipsis" style={{ color: 'var(--ink)' }}>{title}</span>
        {actions}
      </div>
    </div>
  )
}

/** Title of a tab page: in Modern UI the app name in small capitals over a big lowercase title, like a pivot. */
export function PageTitle({ text, style }: { text: string; style?: CSSProperties }) {
  const metro = useMetro()
  if (useNothing()) {
    // Small mono app tag over a dot-matrix headline.
    return (
      <div className="col" style={style}>
        <span className="t-caption" style={{ color: 'var(--inkFaint)', letterSpacing: '0.14em' }}>MONEY</span>
        <span className="t-hero" style={{ color: 'var(--ink)', fontSize: 40, lineHeight: '46px', letterSpacing: '0.02em', textTransform: 'uppercase', whiteSpace: 'nowrap' }}>{text}</span>
      </div>
    )
  }
  if (!metro) return <span className="t-display" style={{ color: 'var(--ink)', ...style }}>{text}</span>
  return (
    <div className="col" style={style}>
      <span className="t-label" style={{ color: 'var(--ink)', letterSpacing: '0.08em' }}>MONEY</span>
      <span className="t-hero" style={{ color: 'var(--ink)', fontSize: 50, lineHeight: '56px', textTransform: 'lowercase', whiteSpace: 'nowrap' }}>{text}</span>
    </div>
  )
}

export function PrimaryButton({ text, onClick, enabled = true, color = 'var(--accent)', icon, style }: {
  text: string
  onClick: () => void
  enabled?: boolean
  color?: string
  icon?: UiIcon
  style?: CSSProperties
}) {
  const fg = enabled ? Ink : 'var(--inkFaint)'
  const metro = useMetro()
  if (useDueffe()) {
    // Dueffe main button: gold gradient at 135°, radius 18; disabled is a Surface key with a strong line.
    return (
      <Bouncy onClick={onClick} disabled={!enabled} pressedScale={0.95} className="center"
        style={{ width: '100%', height: 56, borderRadius: 18, background: enabled ? `linear-gradient(135deg, ${color}, color-mix(in srgb, ${color} 30%, #FFB800))` : 'var(--surface)', border: enabled ? 0 : '1px solid #FFD84D57', color: fg, flexShrink: 0, ...style }}>
        {icon && <Icon name={icon} size={20} style={{ marginRight: 8 }} />}
        <span className="t-label" style={{ fontSize: 16 }}>{text}</span>
      </Bouncy>
    )
  }
  if (useNothing()) {
    // Pill in display ink, Space Mono caps; a coloured [color] (expense, income) keeps its signal.
    const neutral = color === 'var(--accent)'
    return (
      <Bouncy onClick={onClick} disabled={!enabled} className="center"
        style={{ width: '100%', height: 54, borderRadius: 999, background: enabled ? color : 'transparent', border: enabled ? 0 : nothingBorder, color: !enabled ? 'var(--inkFaint)' : neutral ? Ink : '#FFFFFF', transition: 'background-color 0.2s', flexShrink: 0, ...style }}>
        {icon && <Icon name={icon} size={18} style={{ marginRight: 8 }} />}
        <span className="t-label" style={{ fontSize: 14, letterSpacing: '0.08em', textTransform: 'uppercase' }}>{text}</span>
      </Bouncy>
    )
  }
  if (metro) {
    return (
      <Bouncy onClick={onClick} disabled={!enabled} className="center"
        style={{ width: '100%', height: 56, background: enabled ? tile(color) : 'transparent', border: enabled ? 0 : '2px solid var(--inkFaint)', color: fg, transition: 'background-color 0.25s', flexShrink: 0, ...style }}>
        {icon && <Icon name={icon} size={20} style={{ marginRight: 8 }} />}
        <span className="t-titleSmall" style={{ textTransform: 'lowercase' }}>{text}</span>
      </Bouncy>
    )
  }
  return (
    <Bouncy onClick={onClick} disabled={!enabled} pressedScale={0.96} className="center"
      style={{ width: '100%', height: 58, borderRadius: 999, background: enabled ? color : 'var(--surfaceHigh)', color: fg, transition: 'background-color 0.25s, color 0.25s', flexShrink: 0, ...style }}>
      {icon && <Icon name={icon} size={20} style={{ marginRight: 8 }} />}
      <span className="t-bodyStrong" style={{ fontSize: 17 }}>{text}</span>
    </Bouncy>
  )
}

export function MonthSwitcher({ month, onChange, className = 't-titleSmall', style }: {
  month: Month
  onChange: (m: Month) => void
  className?: string
  style?: CSSProperties
}) {
  const metro = useMetro()
  const nothing = useNothing()
  const previous = useRef(month)
  const dir = month >= previous.current ? 1 : -1
  useEffect(() => { previous.current = month }, [month])
  return (
    <div className="row" style={style}>
      <RoundIconButton icon="chevron_left" onClick={() => onChange(month - 1)} size={36} label="Mese precedente" />
      <div style={{ width: metro ? 172 : 150, position: 'relative', height: metro ? 30 : 26, overflow: 'hidden' }}>
        <AnimatePresence initial={false} custom={dir}>
          <motion.div
            key={month}
            custom={dir}
            variants={{
              enter: (d: number) => ({ x: `${(d * 50)}%`, opacity: 0 }),
              center: { x: 0, opacity: 1 },
              exit: (d: number) => ({ x: `${-d * 50}%`, opacity: 0 }),
            }}
            initial="enter"
            animate="center"
            exit="exit"
            transition={{ x: spring(1, 400), opacity: tween(220) }}
            className={nothing ? 't-label' : className}
            style={{ position: 'absolute', inset: 0, textAlign: 'center', color: 'var(--ink)', whiteSpace: 'nowrap', ...(nothing ? { lineHeight: '26px', letterSpacing: '0.1em' } : null) }}
          >
            {metro ? Dates.monthYear(month).toLowerCase() : nothing ? Dates.monthYear(month).toUpperCase() : Dates.monthYear(month)}
          </motion.div>
        </AnimatePresence>
      </div>
      <RoundIconButton icon="chevron_right" onClick={() => onChange(month + 1)} size={36} label="Mese successivo" />
    </div>
  )
}

export function EmptyState({ title, text, icon = 'savings' }: { title: string; text: string; icon?: string }) {
  if (useNothing()) {
    // Lots of room, a grey headline and one line of hint, a small dot-matrix square.
    return (
      <div className="col" style={{ alignItems: 'center', padding: '48px 0', width: '100%' }}>
        <div className="dot-grid" style={{ width: 64, height: 64, backgroundSize: '10px 10px', backgroundImage: 'radial-gradient(circle, var(--inkFaint) 1.5px, transparent 1.7px)' }} />
        <span className="t-label" style={{ marginTop: 20, textAlign: 'center', color: 'var(--inkSoft)', textTransform: 'uppercase', letterSpacing: '0.1em' }}>{title}</span>
        <span className="t-body" style={{ marginTop: 8, textAlign: 'center', color: 'var(--inkFaint)', padding: '0 24px' }}>{text}</span>
      </div>
    )
  }
  return (
    <div className="col" style={{ alignItems: 'center', padding: '32px 0', width: '100%' }}>
      <CategoryBadge icon={icon} color="var(--accent)" size={96} animated />
      <span className="t-titleSmall" style={{ marginTop: 16, textAlign: 'center', color: 'var(--ink)' }}>{title}</span>
      <span className="t-body" style={{ marginTop: 6, textAlign: 'center', color: 'var(--inkSoft)', padding: '0 24px' }}>{text}</span>
    </div>
  )
}

/** Thin progress bar with rounded ends; fills with a spring. */
export function SoftProgress({ progress, color, height = 10, track = 'var(--surfaceHigh)', style }: {
  progress: number
  color: string
  height?: number
  track?: string
  style?: CSSProperties
}) {
  const value = Math.min(1, Math.max(0, progress))
  useMetro()
  if (useNothing()) {
    // The signature Nothing bar: discrete square segments with 2px gaps that light up one by one.
    const n = 32
    const lit = Math.ceil(value * n - 0.001)
    return (
      <div className="row" style={{ width: '100%', height: Math.min(20, Math.max(4, height)), gap: 2, ...style }}>
        {Array.from({ length: n }, (_, i) => (
          <motion.div key={i} initial={false} animate={{ backgroundColor: i < lit ? color : 'var(--line)' }}
            transition={{ ...tween(160, NothingEase), delay: i < lit ? i * 0.008 : 0 }} style={{ flex: 1, height: '100%' }} />
        ))}
      </div>
    )
  }
  return (
    <div style={{ width: '100%', height, borderRadius: 999, background: track, overflow: 'hidden', ...style }}>
      <motion.div initial={{ width: '0%' }} animate={{ width: `${value * 100}%` }} transition={spring(0.75, 60)}
        style={{ height: '100%', borderRadius: 999, background: tile(color) }} />
    </div>
  )
}

/** Fades and lifts content in once, staggered by [index]. */
export function Staggered({ index, step = 55, children, style, className }: {
  index: number
  step?: number
  children: ReactNode
  style?: CSSProperties
  className?: string
}) {
  const metro = useMetro()
  if (useNothing()) {
    // Elements fade, they don't slide.
    return (
      <motion.div className={className} style={style} initial={{ opacity: 0 }} animate={{ opacity: 1 }}
        transition={{ ...tween(250, NothingEase), delay: (Math.min(index, 12) * step) / 1000 }}>
        {children}
      </motion.div>
    )
  }
  if (metro) {
    // Items swing in like a turnstile, hinged on their left edge.
    return (
      <motion.div className={className} style={{ transformOrigin: '0% 50%', transformPerspective: 900, ...style }}
        initial={{ opacity: 0, rotateY: 60, x: 40 }}
        animate={{ opacity: 1, rotateY: 0, x: 0 }}
        transition={{ duration: 0.45, ease: [0.1, 0.9, 0.2, 1], delay: (Math.min(index, 12) * step) / 1000 }}>
        {children}
      </motion.div>
    )
  }
  return (
    <motion.div className={className} style={style}
      initial={{ opacity: 0, y: 48 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ ...spring(0.8, 220), delay: (Math.min(index, 12) * step) / 1000 }}>
      {children}
    </motion.div>
  )
}

// --- forms -----------------------------------------------------------------------------------

export function FormLabel({ text, style }: { text: string; style?: CSSProperties }) {
  // Nothing: instrument-panel label in ALL CAPS.
  const caps = useNothing()
  return <div className="t-label" style={{ color: 'var(--inkSoft)', paddingTop: 14, paddingBottom: 8, ...(caps ? { textTransform: 'uppercase' } : null), ...style }}>{text}</div>
}

/** Material 3 switch with the app colors. */
export function Switch({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  const metro = useMetro()
  if (useNothing()) {
    // Physical switch: pill track, round thumb; on = display-ink track with a paper thumb.
    return (
      <div role="switch" aria-checked={checked} onClick={(e) => { e.stopPropagation(); onChange(!checked) }}
        style={{
          width: 52, height: 30, borderRadius: 999, flexShrink: 0, cursor: 'pointer', position: 'relative',
          background: checked ? 'var(--accent)' : 'transparent', border: `1px solid ${checked ? 'var(--accent)' : 'var(--inkFaint)'}`,
          transition: 'background-color 0.2s, border-color 0.2s',
        }}>
        <motion.div initial={false} animate={{ x: checked ? 22 : 0 }} transition={tween(200, NothingEase)}
          style={{ position: 'absolute', top: 3, left: 3, width: 22, height: 22, borderRadius: '50%', background: checked ? Ink : 'var(--inkFaint)', transition: 'background-color 0.2s' }} />
      </div>
    )
  }
  if (metro) {
    // Windows Phone toggle: a framed track that fills with the accent, and a solid square thumb.
    return (
      <div className="col" style={{ alignItems: 'flex-end', flexShrink: 0 }}>
        <div role="switch" aria-checked={checked} onClick={(e) => { e.stopPropagation(); onChange(!checked) }}
          style={{ width: 62, height: 28, border: '2px solid var(--ink)', padding: 4, position: 'relative', cursor: 'pointer' }}>
          <motion.div initial={false} animate={{ width: checked ? '100%' : '0%' }} transition={tween(180)} style={{ height: '100%', background: 'var(--accent)' }} />
          <motion.div initial={false} animate={{ x: checked ? 44 : 0 }} transition={tween(180)}
            style={{ position: 'absolute', top: -2, left: -2, width: 14, height: 28, background: 'var(--ink)' }} />
        </div>
        <span className="t-caption" style={{ color: 'var(--inkSoft)', marginTop: 4 }}>{checked ? 'attivo' : 'disattivo'}</span>
      </div>
    )
  }
  return (
    <div role="switch" aria-checked={checked} onClick={(e) => { e.stopPropagation(); onChange(!checked) }}
      style={{
        width: 52, height: 32, borderRadius: 16, flexShrink: 0, cursor: 'pointer', position: 'relative',
        background: checked ? 'var(--accent)' : 'var(--surfaceHigh)', border: `2px solid ${checked ? 'var(--accent)' : 'var(--surfaceHigh)'}`,
        transition: 'background-color 0.2s, border-color 0.2s',
      }}>
      <motion.div initial={false}
        animate={{ x: checked ? 20 : 0, width: checked ? 24 : 16, height: checked ? 24 : 16, top: checked ? 2 : 6, left: checked ? 2 : 6 }}
        transition={spring(0.7, 500)}
        style={{ position: 'absolute', borderRadius: '50%', background: checked ? Ink : 'var(--inkFaint)' }} />
    </div>
  )
}

export function SwitchRow({ title, subtitle, checked, onChange, style }: {
  title: string
  subtitle?: string
  checked: boolean
  onChange: (v: boolean) => void
  style?: CSSProperties
}) {
  const metro = useMetro()
  const nothing = useNothing()
  return (
    <Bouncy pressedScale={0.98} onClick={() => onChange(!checked)} className="row"
      style={nothing ? { padding: '14px 0', borderBottom: '1px solid var(--line)', ...style } : metro ? { padding: '10px 0', ...style } : { borderRadius: 22, background: 'var(--surface)', padding: '14px 18px', ...style }}>
      <div className="col flex1">
        <span className={metro ? 't-titleSmall' : 't-bodyStrong'} style={{ color: 'var(--ink)' }}>{title}</span>
        {subtitle && <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{subtitle}</span>}
      </div>
      <div style={{ width: 12 }} />
      <Switch checked={checked} onChange={onChange} />
    </Bouncy>
  )
}

/** Euro amount input with decimal keyboard. */
/** Euro amount input; with [operations] it also takes + − × ÷ (see amountMath). */
export function AmountField({ value, onChange, placeholder = '0,00', operations = false }: {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  operations?: boolean
}) {
  return (
    <label className="row" style={{ borderRadius: 24, background: 'var(--surface)', padding: '14px 20px', cursor: 'text' }}>
      <input className="field t-display flex1" inputMode="decimal" value={value} placeholder={placeholder}
        onChange={(e) => onChange(operations
          ? e.target.value.replace(/[^0-9.,+\-−*×x/÷:]/g, '').slice(0, 24)
          : e.target.value.replace(/[^0-9.,]/g, '').slice(0, 10))} />
      <span className="t-title" style={{ color: 'var(--inkSoft)' }}>€</span>
    </label>
  )
}

/** Multi line text field that grows with its content (BasicTextField). */
export function NoteField({ value, onChange, placeholder, style, onFocus, onBlur }: {
  value: string
  onChange: (v: string) => void
  placeholder: string
  style?: CSSProperties
  onFocus?: () => void
  onBlur?: () => void
}) {
  const ref = useRef<HTMLTextAreaElement>(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    el.style.height = '0px'
    el.style.height = el.scrollHeight + 'px'
  }, [value])
  return (
    <label style={{ display: 'block', borderRadius: 20, background: 'var(--surface)', padding: '16px 18px', cursor: 'text', width: '100%', ...style }}>
      <textarea ref={ref} rows={1} className="field t-body" value={value} placeholder={placeholder} autoCapitalize="sentences"
        onChange={(e) => onChange(e.target.value)} onFocus={onFocus} onBlur={onBlur} style={{ display: 'block' }} />
    </label>
  )
}

export function TextField({ value, onChange, placeholder, secret = false, autoComplete, style }: {
  value: string
  onChange: (v: string) => void
  placeholder: string
  secret?: boolean
  /** Lets password managers fill username and password ("username", "current-password"...). */
  autoComplete?: string
  style?: CSSProperties
}) {
  return (
    <label style={{ display: 'block', borderRadius: 20, background: 'var(--surface)', padding: '16px 18px', cursor: 'text', ...style }}>
      <input className="field t-body" type={secret ? 'password' : 'text'} value={value} placeholder={placeholder}
        autoCapitalize="off" autoCorrect="off" spellCheck={false} autoComplete={autoComplete ?? (secret ? 'new-password' : 'off')}
        onChange={(e) => onChange(e.target.value)} />
    </label>
  )
}

export function ColorSwatches({ colors, selected, onSelect }: { colors: number[]; selected: number; onSelect: (c: number) => void }) {
  useMetro()
  return (
    // Padding leaves room for the enlarged selected swatch.
    <div className="hscroll" style={{ gap: 10, padding: 6, margin: '0 -6px' }}>
      {colors.map((value) => {
        const isSelected = value === selected
        return (
          <motion.div key={value} animate={{ scale: isSelected ? 1.15 : 1 }} transition={spring(0.4, 1500)} style={{ flexShrink: 0 }}>
            <Bouncy onClick={() => onSelect(value)} className="center"
              style={{ width: 40, height: 40, borderRadius: '50%', background: argb(accentShown(value)), boxShadow: isSelected ? 'inset 0 0 0 3px var(--ink)' : 'none' }}>
              {isSelected && <Icon name="check" size={18} color={Ink} />}
            </Bouncy>
          </motion.div>
        )
      })}
    </div>
  )
}

export function IconPicker({ selected, color, onSelect }: { selected: string; color: string; onSelect: (k: string) => void }) {
  useMetro()
  return (
    <div className="hscroll" style={{ gap: 8 }}>
      {Object.keys(appIcons).map((key) => {
        const isSelected = key === selected
        return (
          <Bouncy key={key} onClick={() => onSelect(key)} className="center"
            style={{ width: 48, height: 48, borderRadius: 16, flexShrink: 0, background: isSelected ? tile(color) : 'var(--surface)' }}>
            <AppIcon name={key} size={22} color={isSelected ? Ink : 'var(--inkSoft)'} />
          </Bouncy>
        )
      })}
    </div>
  )
}

export function Legend({ text, color }: { text: string; color: string }) {
  return (
    <div className="row">
      <div style={{ width: 10, height: 10, borderRadius: '50%', background: tile(color) }} />
      <span className="t-caption" style={{ marginLeft: 6, color: 'var(--inkSoft)' }}>{text}</span>
    </div>
  )
}

/** Category name + optional subcategory, used by summaries. */
export function categoryLabel(category: Category | undefined, sub: Category | undefined): string {
  return [category?.name, sub?.name].filter(Boolean).join(' · ')
}
