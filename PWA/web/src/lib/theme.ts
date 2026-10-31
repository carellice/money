import { Palette, argb } from '../../../shared/model.ts'

/** Dark ink used on top of every pastel fill, in both themes. */
export const Ink = '#15121F'
export const INK = 0xff15121f

export type MoneyColors = {
  isDark: boolean
  bg: string
  surface: string
  surfaceHigh: string
  ink: string
  inkSoft: string
  inkFaint: string
  line: string
  income: string
  expense: string
  accent: string
  incomeText: string
  expenseText: string
  accentText: string
}

const Dark: MoneyColors = {
  isDark: true,
  bg: '#15121F',
  surface: '#1E1A2B',
  surfaceHigh: '#2A2540',
  ink: '#F4EFFA',
  inkSoft: '#B9B2CC',
  inkFaint: '#6F6888',
  line: 'rgba(255,255,255,0.12)',
  income: '#8FE3C0',
  expense: '#FF8A7A',
  accent: '#B8A6FF',
  incomeText: '#8FE3C0',
  expenseText: '#FF9C8E',
  accentText: '#C6B8FF',
}

const Light: MoneyColors = {
  isDark: false,
  bg: '#F6F1EA',
  surface: '#FFFFFF',
  surfaceHigh: '#ECE4F4',
  ink: '#15121F',
  inkSoft: '#433D55',
  inkFaint: '#746D86',
  line: 'rgba(0,0,0,0.08)',
  income: '#8FE3C0',
  expense: '#FF8A7A',
  accent: '#B8A6FF',
  incomeText: '#17714F',
  expenseText: '#B8382A',
  accentText: '#5538CC',
}

// AndroidX ColorUtils.colorToHSL / HSLToColor, so custom accents get the same text shade as on Android.
function colorToHsl(color: number): [number, number, number] {
  const rf = ((color >>> 16) & 0xff) / 255
  const gf = ((color >>> 8) & 0xff) / 255
  const bf = (color & 0xff) / 255
  const max = Math.max(rf, gf, bf)
  const min = Math.min(rf, gf, bf)
  const delta = max - min
  let h: number
  let s: number
  const l = (max + min) / 2
  if (max === min) {
    h = 0
    s = 0
  } else {
    if (max === rf) h = ((gf - bf) / delta) % 6
    else if (max === gf) h = (bf - rf) / delta + 2
    else h = (rf - gf) / delta + 4
    s = delta / (1 - Math.abs(2 * l - 1))
  }
  h = (h * 60) % 360
  if (h < 0) h += 360
  return [h, Math.min(1, Math.max(0, s)), Math.min(1, Math.max(0, l))]
}

function hslToColor(h: number, s: number, l: number): number {
  const c = (1 - Math.abs(2 * l - 1)) * s
  const m = l - 0.5 * c
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const segment = Math.trunc(h / 60)
  let r = 0
  let g = 0
  let b = 0
  switch (segment) {
    case 0: [r, g, b] = [c + m, x + m, m]; break
    case 1: [r, g, b] = [x + m, c + m, m]; break
    case 2: [r, g, b] = [m, c + m, x + m]; break
    case 3: [r, g, b] = [m, x + m, c + m]; break
    case 4: [r, g, b] = [x + m, m, c + m]; break
    default: [r, g, b] = [c + m, m, x + m]
  }
  const ch = (v: number) => Math.min(255, Math.max(0, Math.round(v * 255)))
  return (0xff000000 | (ch(r) << 16) | (ch(g) << 8) | ch(b)) >>> 0
}

/** Readable text variant of a pastel accent: lighter on dark backgrounds, deeper on light ones. */
function accentTextFor(accent: number, dark: boolean): string {
  const [h, s] = colorToHsl(accent)
  return argb(hslToColor(h, Math.max(s, 0.6), dark ? 0.8 : 0.36))
}

export function themeColors(dark: boolean, accent: number): MoneyColors {
  const base = dark ? Dark : Light
  // The default lilac keeps its hand-tuned text shade.
  if (accent === Palette.LILAC) return base
  return { ...base, accent: argb(accent), accentText: accentTextFor(accent, dark) }
}

/** Writes the palette as CSS variables, used by every stylesheet rule. */
export function applyTheme(colors: MoneyColors) {
  const root = document.documentElement.style
  for (const [key, value] of Object.entries(colors)) {
    if (typeof value === 'string') root.setProperty(`--${key}`, value)
  }
  // Material colors derived like the Compose color scheme (primary is the deep accent in light mode).
  root.setProperty('--pickerPrimary', colors.isDark ? colors.accent : colors.accentText)
  root.setProperty('--pickerOnPrimary', colors.isDark ? Ink : '#FFFFFF')
  root.setProperty('--snackAction', colors.isDark ? '#6750A4' : '#D0BCFF')
  document.documentElement.style.colorScheme = colors.isDark ? 'dark' : 'light'
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', colors.bg))
}

/** Color with alpha, from a "#RRGGBB" or "rgb(...)" string. */
export function alpha(color: string, a: number): string {
  if (color.startsWith('#')) {
    const n = parseInt(color.slice(1), 16)
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
  }
  const m = /rgba?\(([^)]+)\)/.exec(color)
  if (!m) return color
  const [r, g, b] = m[1].split(',').map((x) => x.trim())
  return `rgba(${r},${g},${b},${a})`
}
