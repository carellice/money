// Organic blob outlines, a port of BlobSpec from the Android app. Kotlin's Random(seed) is reproduced
// exactly (XorWow), so every blob has the same shape and wobble as on the phone.

class KotlinRandom {
  private x: number
  private y: number
  private z: number
  private w: number
  private v: number
  private addend: number

  constructor(seed: number) {
    const seed1 = seed | 0
    const seed2 = seed1 >> 31
    this.x = seed1
    this.y = seed2
    this.z = 0
    this.w = 0
    this.v = ~seed1
    this.addend = (seed1 << 10) ^ (seed2 >>> 4)
    for (let i = 0; i < 64; i++) this.nextInt()
  }

  nextInt(): number {
    let t = this.x
    t = t ^ (t >>> 2)
    this.x = this.y
    this.y = this.z
    this.z = this.w
    const v0 = this.v
    this.w = v0
    t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4)
    this.v = t | 0
    this.addend = (this.addend + 362437) | 0
    return (t + this.addend) | 0
  }

  nextFloat(): number {
    return (this.nextInt() >>> 8) / (1 << 24)
  }
}

/** Java's String.hashCode, used by the app for blob seeds. */
export function hashCode(text: string): number {
  let h = 0
  for (let i = 0; i < text.length; i++) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0
  return h
}

const TWO_PI = 2 * Math.PI

/**
 * Deterministic organic outline: [points] control points around an ellipse whose radii
 * breathe over time, joined with a closed Catmull-Rom spline.
 */
export class BlobSpec {
  readonly points: number
  readonly amplitude: number
  private phase: number[]
  private phase2: number[]
  private speed: number[]
  private bias: number[]
  private rotation: number
  private xs: number[]
  private ys: number[]

  constructor(seed: number, points = 7, amplitude = 0.07) {
    this.points = points
    this.amplitude = amplitude
    const random = new KotlinRandom(seed)
    this.phase = Array.from({ length: points }, () => random.nextFloat() * TWO_PI)
    this.phase2 = Array.from({ length: points }, () => random.nextFloat() * TWO_PI)
    this.speed = Array.from({ length: points }, () => 0.6 + random.nextFloat() * 0.8)
    this.bias = Array.from({ length: points }, () => (random.nextFloat() - 0.5) * amplitude)
    this.rotation = random.nextFloat() * TWO_PI
    this.xs = new Array(points).fill(0)
    this.ys = new Array(points).fill(0)
  }

  path(width: number, height: number, time: number, dx = 0, dy = 0): string {
    const { points, amplitude, xs, ys } = this
    const scale = 1 / (1 + amplitude * 1.6)
    const rx = (width / 2) * scale
    const ry = (height / 2) * scale
    const cx = width / 2 + dx
    const cy = height / 2 + dy
    for (let i = 0; i < points; i++) {
      const angle = this.rotation + (TWO_PI * i) / points
      const wobble = Math.sin(time * this.speed[i] + this.phase[i]) * 0.7 + Math.sin(time * this.speed[i] * 0.53 + this.phase2[i]) * 0.3
      const k = 1 + this.bias[i] + amplitude * wobble
      xs[i] = cx + Math.cos(angle) * rx * k
      ys[i] = cy + Math.sin(angle) * ry * k
    }
    const f = (n: number) => n.toFixed(2)
    let d = `M${f(xs[0])} ${f(ys[0])}`
    for (let i = 0; i < points; i++) {
      const p0 = (i - 1 + points) % points
      const p2 = (i + 1) % points
      const p3 = (i + 2) % points
      d += `C${f(xs[i] + (xs[p2] - xs[p0]) / 6)} ${f(ys[i] + (ys[p2] - ys[p0]) / 6)} ${f(xs[p2] - (xs[p3] - xs[i]) / 6)} ${f(ys[p2] - (ys[p3] - ys[i]) / 6)} ${f(xs[p2])} ${f(ys[p2])}`
    }
    return d + 'Z'
  }
}

// --- shared clock --------------------------------------------------------------------------
// Seconds since start, shared by every animated blob so they move in sync (LocalBlobTime).

type Frame = (time: number) => void
const frames = new Set<Frame>()
const start = performance.now()
let running = false

function loop() {
  if (frames.size === 0) {
    running = false
    return
  }
  const time = (performance.now() - start) / 1000
  frames.forEach((frame) => frame(time))
  requestAnimationFrame(loop)
}

export function blobTime(): number {
  return (performance.now() - start) / 1000
}

/** Calls [frame] on every animation frame with the shared clock; returns the unsubscribe. */
export function onFrame(frame: Frame): () => void {
  frames.add(frame)
  if (!running) {
    running = true
    requestAnimationFrame(loop)
  }
  return () => {
    frames.delete(frame)
  }
}
