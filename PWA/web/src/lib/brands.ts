import data from '../../../shared/brandData.json'

// Generated from Simple Icons (CC0), same catalog as the Android app. Brand names and logos are trademarks of their owners.

export type Brand = { key: string; name: string; hex: number; path?: string; monogram?: string }
export type BrandGroup = { name: string; brands: Brand[] }

export const brandGroups = data as BrandGroup[]
const all = brandGroups.flatMap((g) => g.brands)
const byKey = new Map(all.map((b) => [b.key, b]))

export function findBrand(key: string | null | undefined): Brand | undefined {
  return key ? byKey.get(key) : undefined
}

export function groupOf(key: string | null): string | undefined {
  return brandGroups.find((g) => g.brands.some((b) => b.key === key))?.name
}

/** True when the brand color is too light to put a white logo on it. */
export function isLightBrand(brand: Brand): boolean {
  const r = ((brand.hex >>> 16) & 0xff) / 255
  const g = ((brand.hex >>> 8) & 0xff) / 255
  const b = (brand.hex & 0xff) / 255
  return 0.299 * r + 0.587 * g + 0.114 * b > 0.7
}

export function searchBrands(query: string): Brand[] {
  const q = query.trim().toLowerCase()
  // Names starting with the query first, then word starts, then any match.
  const rank = (b: Brand) => {
    const name = b.name.toLowerCase()
    if (!q || name.startsWith(q)) return 0
    if (name.split(/[ +.]/).some((w) => w.startsWith(q))) return 1
    return 2
  }
  return all.filter((b) => !q || b.name.toLowerCase().includes(q)).sort((a, b) => rank(a) - rank(b))
}
