import { useEffect, useState } from 'react'
import { ping } from '../api'

/*
 * useServerWakeup - Explain the cold start instead of showing a blank page
 *
 * THE PROBLEM
 *
 * Free hosting tiers stop a service once it has had no traffic for a while. The
 * next request has to start the container and boot a JVM before anything can
 * answer, which takes roughly 30-60 seconds for a Spring Boot app.
 *
 * For someone opening the link from a CV, that is the entire first impression:
 * a page that does nothing. Most people close the tab well before it resolves,
 * and they close it believing the app is broken rather than asleep.
 *
 * THE FIX
 *
 * Ping the server on load. If it answers quickly - which it will whenever
 * anyone has used the app recently - nothing is shown at all. If it does not,
 * say plainly what is happening and roughly how long it will take.
 *
 * Telling someone "this takes 30 seconds" is the difference between waiting and
 * leaving. The delay is the same either way; only the experience of it changes.
 */

/* Below this, a banner would flash up and vanish - worse than no banner. */
const SHOW_BANNER_AFTER_MS = 2500

export function useServerWakeup() {
  const [waking, setWaking] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false

    /*
     * Only show the banner if the ping is STILL unanswered after the delay.
     * A warm server replies in well under 2.5 seconds, so nothing appears.
     */
    const timer = setTimeout(() => {
      if (!cancelled) setWaking(true)
    }, SHOW_BANNER_AFTER_MS)

    ping()
      .then(() => {
        if (cancelled) return
        clearTimeout(timer)
        setWaking(false)
      })
      .catch(() => {
        if (cancelled) return
        clearTimeout(timer)
        setWaking(false)
        /*
         * The ping genuinely failed rather than being slow - the backend is
         * down, or its URL is wrong. Worth saying so, because every other
         * request is about to fail too and "cannot reach the server" repeated
         * on each screen is a worse way to find out.
         */
        setFailed(true)
      })

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [])

  return { waking, failed }
}
