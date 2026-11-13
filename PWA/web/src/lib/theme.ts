import { Palette, argb } from '../../../shared/model.ts'

/** Dark ink used on top of every pastel fill, in both themes (brand logos keep it in every style). */
export const DarkInk = '#15121F'
export const INK = 0xff15121f

/** Ink on top of colour fills: dark on the pastels, white on the solid Modern UI tiles (--Ink, set by applyTheme). */
export const Ink = 'var(--Ink)'

export type MoneyColors = {
  isDark: boolean
  metro?: boolean
  nothing?: boolean
  dueffe?: boolean
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

// Modern UI palette: true black or white, flat greys, and the Windows Phone accents
// (cyan, green, red) that carry white text.
const MetroDark: MoneyColors = {
  isDark: true,
  bg: '#000000',
  surface: '#1F1F1F',
  surfaceHigh: '#333333',
  ink: '#FFFFFF',
  inkSoft: '#A6A6A6',
  inkFaint: '#707070',
  line: 'rgba(255,255,255,0.2)',
  income: '#60A917',
  expense: '#E51400',
  accent: '#1BA1E2',
  incomeText: '#8CCB3A',
  expenseText: '#FF5A3C',
  accentText: '#1BA1E2',
}

const MetroLight: MoneyColors = {
  isDark: false,
  bg: '#FFFFFF',
  surface: '#F0F0F0',
  surfaceHigh: '#DDDDDD',
  ink: '#000000',
  inkSoft: '#5E5E5E',
  inkFaint: '#999999',
  line: 'rgba(0,0,0,0.15)',
  income: '#60A917',
  expense: '#E51400',
  accent: '#1BA1E2',
  incomeText: '#3F7A0B',
  expenseText: '#C21100',
  accentText: '#0A7FBF',
}

// Nothing palette: OLED black and white data by night, printed manual (off-white paper, black ink) by day.
// The accent is the display ink itself; red and green only encode money going out and in.
const NothingDark: MoneyColors = {
  isDark: true,
  bg: '#000000',
  surface: '#111111',
  surfaceHigh: '#1A1A1A',
  ink: '#E8E8E8',
  inkSoft: '#999999',
  inkFaint: '#666666',
  line: '#222222',
  income: '#4A9E5C',
  expense: '#D71921',
  accent: '#FFFFFF',
  incomeText: '#5DB870',
  expenseText: '#E5343C',
  accentText: '#FFFFFF',
}

const NothingLight: MoneyColors = {
  isDark: false,
  bg: '#F5F5F5',
  surface: '#FFFFFF',
  surfaceHigh: '#F0F0F0',
  ink: '#1A1A1A',
  inkSoft: '#666666',
  inkFaint: '#999999',
  line: '#E8E8E8',
  income: '#4A9E5C',
  expense: '#D71921',
  accent: '#000000',
  incomeText: '#3A7F49',
  expenseText: '#D71921',
  accentText: '#000000',
}

// Dueffe palette: dark only. Near-black page, warm surfaces, ivory text and one gold accent.
const Dueffe: MoneyColors = {
  isDark: true,
  bg: '#050505',
  surface: '#11100D',
  surfaceHigh: '#1A1711',
  ink: '#FFFAF0',
  inkSoft: '#B8AA8D',
  inkFaint: '#7D735F',
  line: '#FFDC8029',
  income: '#A8F0B0',
  expense: '#FF9B85',
  accent: '#FFD84D',
  incomeText: '#A8F0B0',
  expenseText: '#FF9B85',
  accentText: '#FFD84D',
}

/** The default lilac stands in for Windows Phone cyan in the Modern UI style. */
export const METRO_CYAN = 0xff1ba1e2

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

/** Solid, saturated version of a pastel (ARGB), for Modern UI tiles that carry white text. Same as Color.solid() on Android. */
export function solidArgb(color: number): number {
  const [h, s, l] = colorToHsl(color)
  // Already a solid tile colour (the Windows Phone accents): keep it as it is.
  if (s >= 0.6 && l >= 0.25 && l <= 0.55) return color
  // Grey-ish pastels keep a hint of their hue instead of turning neon.
  return hslToColor(h, s < 0.15 ? s : Math.min(0.9, Math.max(s, 0.72)), 0.44)
}

let metroOn = false

/** True while the Modern UI style is on. */
export const isMetro = () => metroOn

/** Called by App while rendering, so every child renders in the new style already. */
export function setMetro(on: boolean) { metroOn = on }

/** [solidArgb] for a "#RRGGBB" colour in the Modern UI style, unchanged otherwise (CSS variables pass through). */
export function tile(color: string): string {
  if (!metroOn) return color
  if (color.startsWith('#') && color.length === 7) return argb(solidArgb((0xff000000 | parseInt(color.slice(1), 16)) >>> 0))
  // argb() output: "rgb(r,g,b)" or "rgba(r,g,b,a)", the alpha is kept.
  const m = /^rgba?\((\d+),(\d+),(\d+)(?:,([\d.]+))?\)$/.exec(color.replace(/\s/g, ''))
  if (!m) return color
  const solid = solidArgb((0xff000000 | (+m[1] << 16) | (+m[2] << 8) | +m[3]) >>> 0)
  return m[4] == null ? argb(solid) : argb(solid, +m[4])
}

/** How a stored accent is painted in the swatches. */
export function accentShown(c: number): number {
  if (!metroOn) return c
  return c === Palette.LILAC ? METRO_CYAN : solidArgb(c)
}

export function themeColors(dark: boolean, accent: number, metro = false, nothing = false, dueffe = false): MoneyColors {
  // Dueffe has one accent, gold, and is dark only.
  if (dueffe) return { ...Dueffe, dueffe: true }
  // Nothing is monochrome: the chosen colour does not tint it.
  if (nothing) return { ...(dark ? NothingDark : NothingLight), nothing: true }
  const base = metro ? (dark ? MetroDark : MetroLight) : dark ? Dark : Light
  // The default lilac keeps its hand-tuned text shade.
  if (accent === Palette.LILAC) return { ...base, metro } as MoneyColors
  if (metro) {
    const solid = solidArgb(accent)
    const [h, s, l] = colorToHsl(solid)
    return { ...base, metro, accent: argb(solid), accentText: argb(hslToColor(h, s, dark ? Math.min(0.62, l + 0.12) : Math.max(0.3, l - 0.06))) } as MoneyColors
  }
  return { ...base, accent: argb(accent), accentText: accentTextFor(accent, dark) }
}

/** Writes the palette as CSS variables, used by every stylesheet rule. */
export function applyTheme(colors: MoneyColors) {
  metroOn = !!colors.metro
  document.documentElement.classList.toggle('metro', metroOn)
  document.documentElement.classList.toggle('nothing', !!colors.nothing)
  document.documentElement.classList.toggle('dueffe', !!colors.dueffe)
  const root = document.documentElement.style
  // Pastel category icons need more ink on the light paper.
  root.setProperty('--iconMix', colors.isDark ? '100%' : '65%')
  root.setProperty('--Ink', colors.dueffe ? '#161006' : colors.nothing ? colors.bg : metroOn ? '#FFFFFF' : DarkInk)
  root.setProperty('--metroBar', colors.isDark ? '#1F1F1F' : '#DDDDDD')
  for (const [key, value] of Object.entries(colors)) {
    if (typeof value === 'string') root.setProperty(`--${key}`, value)
  }
  // Material colors derived like the Compose color scheme (primary is the deep accent in light mode).
  root.setProperty('--pickerPrimary', colors.isDark ? colors.accent : colors.accentText)
  root.setProperty('--pickerOnPrimary', colors.nothing ? colors.bg : colors.isDark && !metroOn ? DarkInk : '#FFFFFF')
  root.setProperty('--snackAction', colors.isDark ? '#6750A4' : '#D0BCFF')
  document.documentElement.style.colorScheme = colors.isDark ? 'dark' : 'light'
  document.querySelectorAll('meta[name="theme-color"]').forEach((m) => m.setAttribute('content', colors.bg))
}

/** Color with alpha, from a "#RRGGBB" or "rgb(...)" string. */
export function alpha(color: string, a: number): string {
  if (color.startsWith('var(')) return `color-mix(in srgb, ${color} ${Math.round(a * 100)}%, transparent)`
  if (color.startsWith('#')) {
    const n = parseInt(color.slice(1), 16)
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
  }
  const m = /rgba?\(([^)]+)\)/.exec(color)
  if (!m) return color
  const [r, g, b] = m[1].split(',').map((x) => x.trim())
  return `rgba(${r},${g},${b},${a})`
}
