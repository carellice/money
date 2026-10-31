import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { BlobSpec, blobTime, onFrame } from '../lib/blob.ts'

type Fill = { color: string } | { gradient: string[] }

type BlobProps = Fill & {
  seed?: number
  amplitude?: number
  animated?: boolean
  speed?: number
}

function useSize(ref: React.RefObject<Element | null>) {
  const [size, setSize] = useState({ w: 0, h: 0 })
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    // SVG elements have no layout size of their own: measure the box they fill.
    const box = (el instanceof SVGElement ? el.parentElement : el) as HTMLElement
    const update = () => {
      setSize((s) => (s.w === box.offsetWidth && s.h === box.offsetHeight ? s : { w: box.offsetWidth, h: box.offsetHeight }))
    }
    update()
    const observer = new ResizeObserver(update)
    observer.observe(box)
    return () => observer.disconnect()
  }, [ref])
  return size
}

/** Draws a living blob filling its parent (Modifier.blob). Only the path changes each frame. */
export function BlobLayer(props: BlobProps) {
  const { seed = 1, amplitude = 0.07, animated = true, speed = 1 } = props
  const svg = useRef<SVGSVGElement>(null)
  const path = useRef<SVGPathElement>(null)
  const size = useSize(svg)
  const spec = useMemo(() => new BlobSpec(seed, 7, amplitude), [seed, amplitude])
  const gradientId = useId()

  useEffect(() => {
    const el = path.current
    if (!el || size.w === 0) return
    const draw = (time: number) => el.setAttribute('d', spec.path(size.w, size.h, animated ? time * speed : 0))
    draw(blobTime())
    if (!animated) return
    let stop: (() => void) | null = null
    // Blobs scrolled out of sight stop animating.
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting && !stop) stop = onFrame(draw)
      else if (!entry.isIntersecting && stop) {
        stop()
        stop = null
      }
    })
    observer.observe(svg.current!)
    return () => {
      observer.disconnect()
      stop?.()
    }
  }, [spec, size.w, size.h, animated, speed])

  const gradient = 'gradient' in props ? props.gradient : null
  return (
    <svg ref={svg} className="blob-svg" aria-hidden>
      {gradient && (
        <defs>
          {/* Brush.linearGradient: from the top left corner to the bottom right one. */}
          <linearGradient id={gradientId} gradientUnits="userSpaceOnUse" x1={0} y1={0} x2={size.w} y2={size.h}>
            {gradient.map((c, i) => <stop key={i} offset={gradient.length === 1 ? 0 : i / (gradient.length - 1)} stopColor={c} />)}
          </linearGradient>
        </defs>
      )}
      <path ref={path} fill={gradient ? `url(#${gradientId})` : (props as { color: string }).color} />
    </svg>
  )
}

type BlobBoxProps = BlobProps & {
  size?: number
  width?: number | string
  height?: number | string
  style?: CSSProperties
  className?: string
  children?: ReactNode
}

/** A box with a blob behind its centered content. */
export function BlobBox({ size, width, height, style, className, children, ...blob }: BlobBoxProps) {
  return (
    <div
      className={'blob-box center ' + (className ?? '')}
      style={{ width: width ?? size, height: height ?? size, flexShrink: 0, ...style }}
    >
      <BlobLayer {...blob} />
      <div className="blob-content center">{children}</div>
    </div>
  )
}

/** A frozen blob outline as a CSS clip path, handy for pictures (BlobShape). */
export function useBlobClip(seed: number, size: number, amplitude = 0.07): string {
  return useMemo(() => `path('${new BlobSpec(seed, 7, amplitude).path(size, size, 0)}')`, [seed, size, amplitude])
}

/** Android pixels to CSS pixels: the app draws some motions in raw pixels on a ~2.75x screen. */
export const PX = 1 / 2.75

/** Soft floating blobs used as page backdrop. */
export function BackdropBlobs({ colors, alpha }: { colors: string[]; alpha: number }) {
  const ref = useRef<SVGSVGElement>(null)
  const paths = useRef<(SVGPathElement | null)[]>([])
  const size = useSize(ref)
  const specs = useMemo(() => colors.map((_, i) => new BlobSpec(100 + i * 7, 7, 0.12)), [colors.length])

  useEffect(() => {
    if (size.w === 0) return
    return onFrame((time) => {
      const t = time * 0.35
      specs.forEach((spec, i) => {
        const w = size.w * (0.7 + 0.15 * i)
        const cx = size.w * (i % 2 === 0 ? 0.05 : 0.95) + Math.sin(t * 0.7 + i) * PX * 30
        const cy = size.h * (0.08 + 0.35 * i) + Math.cos(t * 0.5 + i * 2) * PX * 40
        paths.current[i]?.setAttribute('d', spec.path(w, w, t + i, cx - w / 2, cy - w / 2))
      })
    })
  }, [specs, size.w, size.h])

  return (
    <svg ref={ref} className="blob-svg" aria-hidden style={{ position: 'absolute' }}>
      {colors.map((c, i) => <path key={i} ref={(el) => { paths.current[i] = el }} fill={c} fillOpacity={alpha} />)}
    </svg>
  )
}
