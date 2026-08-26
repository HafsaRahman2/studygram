import { useCallback, useEffect, useState } from 'react'
import { getAuthToken, profile, setAuthToken } from '../api'
import type { User } from '../types'

/*
 * useAuth - Keeps the logged-in user, and keeps them logged in across refreshes
 *
 * THE PROBLEM THIS SOLVES
 *
 * The user used to be stored in plain useState. React state lives in memory,
 * and memory is wiped when the page reloads - so pressing F5, or following a
 * link and coming back, silently logged you out. It was the single most
 * jarring thing about using the app.
 *
 * localStorage is a small key/value store the browser keeps on disk, per site.
 * Writing the user there on login and reading it back on startup makes the
 * session survive a refresh.
 *
 * A NOTE ON SECURITY
 *
 * What is stored HERE is a user profile, not a credential. The credential is
 * the JWT, kept separately in api.ts, and that is what actually grants access.
 *
 * Editing this localStorage entry changes what the UI *draws*, never what the
 * server *permits* - every endpoint identifies the caller from the signed
 * token and re-checks ownership for itself. Rewriting your cached profile to
 * claim somebody else's id gets you a differently-worded page and exactly zero
 * extra permissions.
 *
 * The remaining gap is that the token lives in localStorage, where page
 * JavaScript can read it. See the note in api.ts for that trade-off.
 *
 * WHY A CUSTOM HOOK
 *
 * A hook is just a function that uses other hooks. Putting this logic in one
 * means App.tsx says `const { user, login, logout } = useAuth()` instead of
 * carrying the storage details around itself.
 */

const STORAGE_KEY = 'studygram.user'

/*
 * Read the saved user out of localStorage.
 *
 * Wrapped in try/catch because localStorage can fail for reasons that have
 * nothing to do with us: private browsing modes disable it, and a half-written
 * or hand-edited value will not parse. Any failure just means "nobody is logged
 * in", which is a perfectly good state to start from.
 */
function loadStoredUser(): User | null {
  try {
    /*
     * A stored profile is only a session if there is also a token to go with
     * it. Without this check, clearing the token (or having it expire) would
     * leave the app looking logged in while every request came back 401.
     *
     * The token is the session; the profile is just a cached copy of who it
     * belongs to.
     */
    if (!getAuthToken()) return null

    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export function useAuth() {
  /*
   * Passing a FUNCTION to useState (rather than a value) makes React call it
   * only on the very first render. Writing useState(loadStoredUser()) would
   * re-read localStorage on every single render and throw the result away.
   */
  const [user, setUser] = useState<User | null>(loadStoredUser)

  /*
   * Keep localStorage in step with state whenever the user changes.
   * One effect handles login, logout and profile edits alike.
   */
  useEffect(() => {
    try {
      if (user) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(user))
      } else {
        localStorage.removeItem(STORAGE_KEY)
      }
    } catch {
      // Storage unavailable (private mode, quota). The app still works for
      // this tab; it just will not remember across a refresh.
    }
  }, [user])

  /*
   * Refresh the cached profile once on startup.
   *
   * The copy in localStorage is a snapshot from whenever you last logged in. If
   * anything changed since - you edited your profile on your phone, or the
   * server changed a default - this tab would keep showing the stale version
   * indefinitely, because nothing ever re-reads it.
   *
   * One request on load fixes that. A failure is ignored on purpose: the cached
   * copy is still perfectly usable, and if the token has actually expired the
   * 401 handler in api.ts logs us out anyway.
   *
   * The empty dependency array means this runs once per mount, not on every
   * change to `user` - which would loop, since it sets `user`.
   */
  useEffect(() => {
    const stored = loadStoredUser()
    if (!stored) return

    let cancelled = false

    profile
      .get(stored.username)
      .then((fresh) => {
        if (!cancelled) setUser(fresh)
      })
      .catch(() => {
        /* Keep the cached copy. */
      })

    return () => {
      cancelled = true
    }
  }, [])

  /*
   * Log out in one tab, log out in all of them.
   *
   * The 'storage' event fires in OTHER tabs of the same site when localStorage
   * changes. Without this, logging out in one tab would leave another tab
   * looking logged in.
   */
  useEffect(() => {
    function handleStorageChange(event: StorageEvent) {
      if (event.key === STORAGE_KEY) {
        setUser(event.newValue ? (JSON.parse(event.newValue) as User) : null)
      }
    }

    window.addEventListener('storage', handleStorageChange)
    return () => window.removeEventListener('storage', handleStorageChange)
  }, [])

  /*
   * useCallback keeps these functions identical between renders, so components
   * that receive them as props do not re-render for no reason.
   */
  const login = useCallback((loggedInUser: User) => setUser(loggedInUser), [])

  /*
   * Logging out throws the token away as well as the profile.
   *
   * Clearing only the profile would leave a working credential sitting in
   * localStorage on what might be a shared computer.
   */
  const logout = useCallback(() => {
    setAuthToken(null)
    setUser(null)
  }, [])

  /* Used after a profile edit, to refresh the cached copy. */
  const updateUser = useCallback((updated: User) => setUser(updated), [])

  return { user, login, logout, updateUser }
}
