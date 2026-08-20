import { useCallback, useEffect, useState } from 'react'
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
 * This stores a user profile, not a credential. Nothing here grants access -
 * the backend does not currently issue session tokens (see the README's
 * "Known limitations"), which is the real gap. Editing this localStorage entry
 * would let you change what the UI *draws*, not what the server *permits*.
 *
 * The proper version is a short-lived JWT issued at login and verified on
 * every request. That is the next significant thing to build.
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

  const logout = useCallback(() => setUser(null), [])

  /* Used after a profile edit, to refresh the cached copy. */
  const updateUser = useCallback((updated: User) => setUser(updated), [])

  return { user, login, logout, updateUser }
}
