/*
 * Small formatting helpers shared across the UI.
 */

/*
 * Turn a timestamp into something a human reads at a glance.
 *
 * "2h ago" tells you a post is fresh. "20/08/2026" makes you do arithmetic.
 * Anything older than a week falls back to a real date, because "63 days ago"
 * is no easier to parse than the date itself.
 */
export function timeAgo(isoDate: string): string {
  const then = new Date(isoDate).getTime()

  if (Number.isNaN(then)) {
    return ''
  }

  const seconds = Math.floor((Date.now() - then) / 1000)

  if (seconds < 60) return 'just now'

  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`

  const days = Math.floor(hours / 24)
  if (days === 1) return 'yesterday'
  if (days < 7) return `${days}d ago`

  return new Date(isoDate).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/*
 * Build initials for the avatar circle: "Hafsa Rahman" -> "HR".
 *
 * Generating an avatar from the name means every user has one from the moment
 * they sign up, with no upload step and no broken-image placeholders.
 */
export function initials(name: string | null | undefined): string {
  if (!name) return '?'

  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()

  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/*
 * Pick a stable colour for an avatar from its text.
 *
 * The same name must always get the same colour, so we cannot use a random
 * number. Instead we hash the string into a hue: deterministic, evenly spread,
 * and it means two people in a comment thread are visually distinguishable.
 */
export function avatarHue(seed: string | null | undefined): number {
  if (!seed) return 220

  let hash = 0
  for (let i = 0; i < seed.length; i++) {
    hash = seed.charCodeAt(i) + ((hash << 5) - hash)
    hash |= 0 // force back to a 32-bit integer
  }

  return Math.abs(hash) % 360
}

/*
 * Interests are stored as one comma-separated string ("Programming, Physics").
 * This is the browser-side twin of PostService.parseTopics in the backend.
 */
export function parseInterests(value: string | null | undefined): string[] {
  if (!value) return []

  return value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}
