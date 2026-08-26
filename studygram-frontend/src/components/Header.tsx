import { useEffect, useRef, useState } from 'react'
import type { Page, User } from '../types'
import { Avatar } from './ui'

/*
 * Header - The top bar and main navigation
 *
 * Shows different links depending on whether anyone is logged in, and collapses
 * into a menu button on narrow screens.
 */
export function Header({
  currentUser,
  currentPage,
  onNavigate,
  onLogout,
  breakSlot,
  pendingBuddyCount = 0,
}: {
  currentUser: User | null
  currentPage: Page
  onNavigate: (page: Page) => void
  onLogout: () => void
  /* Shown as a badge on the Buddies link, so requests are noticed. */
  pendingBuddyCount?: number
  /*
   * The "Take a break" button, passed in rather than built here.
   *
   * Its state lives in App (so the countdown keeps running no matter which page
   * you are on), and the header just gives it somewhere to sit. That keeps the
   * header a layout component with no idea what a break is.
   */
  breakSlot?: React.ReactNode
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const navRef = useRef<HTMLElement>(null)

  /* Close the mobile menu after navigating, otherwise it covers the new page. */
  useEffect(() => {
    setMenuOpen(false)
  }, [currentPage])

  /* Escape closes the menu - expected behaviour for anything overlay-shaped. */
  useEffect(() => {
    if (!menuOpen) return

    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setMenuOpen(false)
    }

    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [menuOpen])

  const links: Array<{ page: Page; label: string; badge?: number }> = currentUser
    ? [
        { page: 'feed', label: 'Feed' },
        { page: 'buddies', label: 'Buddies', badge: pendingBuddyCount },
        { page: 'ai', label: 'Assistant' },
      ]
    : []

  return (
    <header className="header">
      <div className="header-inner">
        <button
          className="brand"
          onClick={() => onNavigate(currentUser ? 'feed' : 'home')}
        >
          <span className="brand-mark" aria-hidden="true">
            SG
          </span>
          StudyGram
        </button>

        <button
          className="menu-toggle"
          onClick={() => setMenuOpen((open) => !open)}
          aria-expanded={menuOpen}
          aria-label="Toggle navigation menu"
        >
          <span aria-hidden="true">{menuOpen ? '✕' : '☰'}</span>
        </button>

        <nav ref={navRef} className={`nav ${menuOpen ? 'open' : ''}`}>
          {links.map((link) => (
            <button
              key={link.page}
              className={`nav-link ${currentPage === link.page ? 'active' : ''}`}
              onClick={() => onNavigate(link.page)}
            >
              {link.label}
              {link.badge ? (
                <span
                  className="nav-badge"
                  aria-label={`${link.badge} pending request${link.badge === 1 ? '' : 's'}`}
                >
                  {link.badge}
                </span>
              ) : null}
            </button>
          ))}

          {currentUser ? (
            <div className="nav-user">
              {breakSlot}

              <button
                className={`profile-link ${currentPage === 'profile' ? 'active' : ''}`}
                onClick={() => onNavigate('profile')}
              >
                <Avatar name={currentUser.name ?? currentUser.username} size={28} />
                <span>{currentUser.username}</span>
              </button>

              <button className="nav-link subtle" onClick={onLogout}>
                Log out
              </button>
            </div>
          ) : (
            <div className="nav-user">
              <button className="nav-link" onClick={() => onNavigate('login')}>
                Log in
              </button>
              <button className="btn btn-small" onClick={() => onNavigate('signup')}>
                Sign up
              </button>
            </div>
          )}
        </nav>
      </div>
    </header>
  )
}
