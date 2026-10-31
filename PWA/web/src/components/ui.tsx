import { animate, AnimatePresence, motion, type HTMLMotionProps } from 'framer-motion'
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { Dates, Money } from '../../../shared/format.ts'
import { argb, type Category } from '../../../shared/model.ts'
import type { Month } from '../../../shared/date.ts'
import { appIcons, uiIcons, type UiIcon } from '../lib/icons.gen.ts'
import { findBrand, isLightBrand } from '../lib/brands.ts'
import { hashCode } from '../lib/blob.ts'
import { Ink } from '../lib/theme.ts'
import { BlobBox, useBlobClip } from './Blob.tsx'
import { fastOutSlowIn, haptic, spring, tween } from './motion.ts'

// --- icons -----------------------------------------------------------------------------------

export function Icon({ name, size = 24, color = 'currentColor', style, label }: {
  name: UiIcon
  size?: number
  color?: string
  style?: CSSProperties
  label?: string
}) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color} style={{ flexShrink: 0, ...style }}
      role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}
      dangerouslySetInnerHTML={{ __html: uiIcons[name] }} />
  )
}

/** Icon keys stored in the database (AppIcons). */
export function AppIcon({ name, size = 24, color = 'currentColor', style }: { name: string | null | undefined; size?: number; color?: string; style?: CSSProperties }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color} style={{ flexShrink: 0, ...style }} aria-hidden
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
      style={{ cursor: disabled ? 'default' : 'pointer', ...style }}
      whileTap={disabled ? undefined : { scale: pressedScale }}
      transition={spring(0.45, 500)}
      onPointerDown={() => {
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
  if (imagePath) {
    return <img src={imagePath} alt="" draggable={false} style={{ width: size, height: size, objectFit: 'cover', clipPath: clip, flexShrink: 0 }} />
  }
  if (brand) {
    const tint = isLightBrand(brand) ? Ink : '#FFFFFF'
    return (
      <BlobBox size={size} color={argb(brand.hex)} seed={hashCode(brand.key)} amplitude={0.08} animated={animated}>
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
export function AnimatedAmount(props: Parameters<typeof AmountText>[0]) {
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
  return <AmountText {...props} cents={shown} />
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
  const fg = selected ? Ink : 'var(--ink)'
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
  return <div className={'col ' + (className ?? '')} style={{ borderRadius: 28, background: color, padding, ...style }}>{children}</div>
}

export function SectionTitle({ text, action, onAction, style }: { text: string; action?: string; onAction?: () => void; style?: CSSProperties }) {
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
  return (
    <Bouncy onClick={onClick} className="center" aria-label={label}
      style={{ width: size, height: size, borderRadius: '50%', background, flexShrink: 0, ...style }}>
      <Icon name={icon} size={size * 0.5} color={tint} />
    </Bouncy>
  )
}

export function TopBar({ title, onBack, actions }: { title: string; onBack?: () => void; actions?: ReactNode }) {
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

export function PrimaryButton({ text, onClick, enabled = true, color = 'var(--accent)', icon, style }: {
  text: string
  onClick: () => void
  enabled?: boolean
  color?: string
  icon?: UiIcon
  style?: CSSProperties
}) {
  const fg = enabled ? Ink : 'var(--inkFaint)'
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
  const previous = useRef(month)
  const dir = month >= previous.current ? 1 : -1
  useEffect(() => { previous.current = month }, [month])
  return (
    <div className="row" style={style}>
      <RoundIconButton icon="chevron_left" onClick={() => onChange(month - 1)} size={36} label="Mese precedente" />
      <div style={{ width: 150, position: 'relative', height: 26, overflow: 'hidden' }}>
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
            className={className}
            style={{ position: 'absolute', inset: 0, textAlign: 'center', color: 'var(--ink)', whiteSpace: 'nowrap' }}
          >
            {Dates.monthYear(month)}
          </motion.div>
        </AnimatePresence>
      </div>
      <RoundIconButton icon="chevron_right" onClick={() => onChange(month + 1)} size={36} label="Mese successivo" />
    </div>
  )
}

export function EmptyState({ title, text, icon = 'savings' }: { title: string; text: string; icon?: string }) {
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
  return (
    <div style={{ width: '100%', height, borderRadius: 999, background: track, overflow: 'hidden', ...style }}>
      <motion.div initial={{ width: '0%' }} animate={{ width: `${value * 100}%` }} transition={spring(0.75, 60)}
        style={{ height: '100%', borderRadius: 999, background: color }} />
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
  return <div className="t-label" style={{ color: 'var(--inkSoft)', paddingTop: 14, paddingBottom: 8, ...style }}>{text}</div>
}

/** Material 3 switch with the app colors. */
export function Switch({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
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
  return (
    <Bouncy pressedScale={0.98} onClick={() => onChange(!checked)} className="row"
      style={{ borderRadius: 22, background: 'var(--surface)', padding: '14px 18px', ...style }}>
      <div className="col flex1">
        <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>{title}</span>
        {subtitle && <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>{subtitle}</span>}
      </div>
      <div style={{ width: 12 }} />
      <Switch checked={checked} onChange={onChange} />
    </Bouncy>
  )
}

/** Euro amount input with decimal keyboard. */
export function AmountField({ value, onChange, placeholder = '0,00' }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <label className="row" style={{ borderRadius: 24, background: 'var(--surface)', padding: '14px 20px', cursor: 'text' }}>
      <input className="field t-display flex1" inputMode="decimal" value={value} placeholder={placeholder}
        onChange={(e) => onChange(e.target.value.replace(/[^0-9.,]/g, '').slice(0, 10))} />
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
  return (
    // Padding leaves room for the enlarged selected swatch.
    <div className="hscroll" style={{ gap: 10, padding: 6, margin: '0 -6px' }}>
      {colors.map((value) => {
        const isSelected = value === selected
        return (
          <motion.div key={value} animate={{ scale: isSelected ? 1.15 : 1 }} transition={spring(0.4, 1500)} style={{ flexShrink: 0 }}>
            <Bouncy onClick={() => onSelect(value)} className="center"
              style={{ width: 40, height: 40, borderRadius: '50%', background: argb(value), boxShadow: isSelected ? 'inset 0 0 0 3px var(--ink)' : 'none' }}>
              {isSelected && <Icon name="check" size={18} color={Ink} />}
            </Bouncy>
          </motion.div>
        )
      })}
    </div>
  )
}

export function IconPicker({ selected, color, onSelect }: { selected: string; color: string; onSelect: (k: string) => void }) {
  return (
    <div className="hscroll" style={{ gap: 8 }}>
      {Object.keys(appIcons).map((key) => {
        const isSelected = key === selected
        return (
          <Bouncy key={key} onClick={() => onSelect(key)} className="center"
            style={{ width: 48, height: 48, borderRadius: 16, flexShrink: 0, background: isSelected ? color : 'var(--surface)' }}>
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
      <div style={{ width: 10, height: 10, borderRadius: '50%', background: color }} />
      <span className="t-caption" style={{ marginLeft: 6, color: 'var(--inkSoft)' }}>{text}</span>
    </div>
  )
}

/** Category name + optional subcategory, used by summaries. */
export function categoryLabel(category: Category | undefined, sub: Category | undefined): string {
  return [category?.name, sub?.name].filter(Boolean).join(' · ')
}
