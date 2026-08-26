import { useEffect, useState } from 'react'
import './App.css'

import { setUnauthorizedHandler } from './api'
import { useAuth } from './hooks/useAuth'
import { useBreak } from './hooks/useBreak'
import type { Page } from './types'

import { Header } from './components/Header'
import { BreakButton, TakeABreak } from './components/TakeABreak'
import { Home } from './components/Home'
import { Login, Signup, ForgotPassword } from './components/Auth'
import { Feed } from './components/Feed'
import { Explore } from './components/Explore'
import { AiAssistant } from './components/AiAssistant'
import { Profile } from './components/Profile'

/*
 * App - The root component
 *
 * This file used to be 1,700 lines holding roughly fifty useState variables and
 * every screen in the application. It now does two things: keep track of which
 * page is showing, and keep track of who is logged in.
 *
 * Everything else lives in components/, where each screen owns its own state.
 * That is not just tidiness - in the old version, typing a single character in
 * the login box re-rendered the entire feed, the AI conversation and the profile
 * form, because it was all one component sharing one state object.
 *
 * ON ROUTING
 *
 * Navigation is a string in state rather than react-router. For seven screens
 * with no shareable URLs that is a reasonable trade: no extra dependency, and
 * the whole navigation model fits in the switch statement below. The cost is
 * real though, and worth naming: you cannot link someone to a specific post,
 * and the browser Back button does not move between pages. Adding react-router
 * would fix both, and is the natural next step.
 */
export default function App() {
  const { user, login, logout, updateUser } = useAuth()

  /*
   * Start logged-in users on the feed and everyone else on the landing page.
   * Reading `user` here (rather than always starting at 'home') means a
   * returning visitor whose session was restored from localStorage lands
   * straight on the feed instead of a marketing page they have already read.
   */
  const [page, setPage] = useState<Page>(user ? 'feed' : 'home')

  /*
   * Break state lives here, at the root, for two reasons: the countdown has to
   * keep running while you move between pages, and the break screen covers the
   * whole app rather than being one more page you can navigate away from.
   */
  const breakState = useBreak(user?.id ?? null)
  const [breakOpen, setBreakOpen] = useState(false)

  /*
   * If a break is already running when the app loads - you refreshed, or came
   * back on another device - drop straight back into it rather than pretending
   * nothing is happening.
   */
  useEffect(() => {
    if (breakState.status?.state === 'ACTIVE') {
      setBreakOpen(true)
    }
    // Only when the state itself changes, not on every tick of the countdown.
  }, [breakState.status?.state])

  async function handleOpenBreak() {
    if (breakState.status?.state === 'AVAILABLE') {
      await breakState.start()
    }
    setBreakOpen(true)
  }

  async function handleEndBreak() {
    await breakState.end()
    setBreakOpen(false)
  }

  /*
   * Scroll back to the top when the page changes.
   *
   * Without this, moving from halfway down a long feed to the profile page
   * leaves you halfway down the profile page, which feels broken.
   */
  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [page])

  function handleLogout() {
    logout()
    setPage('home')
  }

  /*
   * Handle the session expiring underneath us.
   *
   * api.ts calls this whenever the server rejects our token. Rather than every
   * screen showing its own "Authentication required" error while the header
   * still shows an avatar, the whole app drops back to the login page once.
   *
   * Registered in an effect (not during render) because it is a side effect on
   * a module outside React, and cleaned up on unmount so a stale callback can
   * never fire into a component that no longer exists.
   */
  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout()
      setPage('login')
    })

    return () => setUnauthorizedHandler(null)
  }, [logout])

  /*
   * Screens that require a login.
   *
   * This is a UI guard, not a security boundary. It stops a logged-out user
   * seeing a broken empty feed; it is not what protects the data. Every
   * endpoint on the server checks permissions for itself, which is where that
   * job belongs - a check that lives only in the browser can be walked around
   * by anyone willing to open the console.
   */
  function renderPage() {
    const requiresAuth: Page[] = ['feed', 'explore', 'ai', 'profile']

    if (requiresAuth.includes(page) && !user) {
      return <Login onLoggedIn={handleLogin} onNavigate={setPage} />
    }

    switch (page) {
      case 'login':
        return <Login onLoggedIn={handleLogin} onNavigate={setPage} />

      case 'signup':
        return <Signup onNavigate={setPage} />

      case 'forgot-password':
        return <ForgotPassword onNavigate={setPage} />

      case 'feed':
        return <Feed currentUser={user!} />

      case 'explore':
        return <Explore currentUser={user!} />

      case 'ai':
        return <AiAssistant currentUser={user!} />

      case 'profile':
        return <Profile currentUser={user!} onUserUpdated={updateUser} />

      default:
        return <Home onNavigate={setPage} />
    }
  }

  function handleLogin(loggedInUser: Parameters<typeof login>[0]) {
    login(loggedInUser)
    setPage('feed')
  }

  return (
    <div className="app">
      <Header
        currentUser={user}
        currentPage={page}
        onNavigate={setPage}
        onLogout={handleLogout}
        breakSlot={
          user && (
            <BreakButton
              status={breakState.status}
              remaining={breakState.remaining}
              onOpen={handleOpenBreak}
            />
          )
        }
      />

      {/* The break screen covers everything. It is not a page you navigate to,
          because a break you can click away from is not really a break. */}
      {breakOpen && breakState.status?.state === 'ACTIVE' && (
        <TakeABreak
          status={breakState.status}
          remaining={breakState.remaining}
          onExtend={breakState.extend}
          onEnd={handleEndBreak}
          onMinimize={() => setBreakOpen(false)}
        />
      )}

      {/* The `narrow` class constrains reading width on the content-heavy
          screens; the landing page spans the full width. */}
      <main className={`main ${page === 'home' ? '' : 'narrow'}`}>{renderPage()}</main>

      <footer className="footer">
        <p>
          StudyGram — React + TypeScript on Spring Boot and PostgreSQL.{' '}
          <a
            href="https://github.com/hafsarahman"
            target="_blank"
            rel="noreferrer"
            className="link"
          >
            Source on GitHub
          </a>
        </p>
      </footer>
    </div>
  )
}
