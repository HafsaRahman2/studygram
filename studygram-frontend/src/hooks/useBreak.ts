import { useCallback, useEffect, useRef, useState } from 'react'
import { breaks } from '../api'
import type { BreakStatus } from '../types'

/*
 * useBreak - State and countdown for the "Take a break" feature
 *
 * HOW THE COUNTDOWN STAYS HONEST
 *
 * The naive way to run a timer is to store `secondsLeft` in state and subtract
 * one every second. It goes wrong in three ways:
 *
 *   - browsers throttle setInterval in background tabs, so the timer runs slow
 *     the moment you switch away
 *   - it stops entirely while the laptop is asleep
 *   - it is trivially editable from the console
 *
 * Instead the server sends an absolute end time, and every tick recomputes
 * `endsAt - now`. Ticks can be late, skipped or throttled and the displayed
 * time is still correct, because the tick only decides *when to re-read the
 * clock*, never what the answer is.
 */
export function useBreak(userId: number | null) {
  const [status, setStatus] = useState<BreakStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  /* Seconds left, recomputed each tick from an absolute target time. */
  const [remaining, setRemaining] = useState(0)

  /*
   * The moment the current countdown reaches zero, as epoch milliseconds.
   *
   * A ref rather than state because changing it should not itself trigger a
   * re-render - the per-second `remaining` update already does that.
   */
  const targetRef = useRef<number | null>(null)

  const refresh = useCallback(async () => {
    if (!userId) return

    try {
      const next = await breaks.status()
      applyStatus(next)
      setError('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load break status')
    } finally {
      setLoading(false)
    }
  }, [userId])

  /*
   * Store a new status and work out what, if anything, we are counting down to.
   */
  function applyStatus(next: BreakStatus) {
    setStatus(next)

    if (next.state === 'ACTIVE' && next.endsAt) {
      /*
       * Parse the server's end time. It arrives without a timezone
       * (2026-08-20T21:15:00), which JavaScript reads as local time - correct
       * here, since the server and the student are on the same clock in this
       * setup. A deployed version should send UTC with an offset and let the
       * browser convert.
       */
      targetRef.current = new Date(next.endsAt).getTime()
      setRemaining(next.secondsRemaining)
      return
    }

    if (next.state === 'COOLDOWN') {
      // No absolute timestamp for this one, so anchor it now.
      targetRef.current = Date.now() + next.secondsUntilAvailable * 1000
      setRemaining(next.secondsUntilAvailable)
      return
    }

    targetRef.current = null
    setRemaining(0)
  }

  /* Load once on mount, and whenever the user changes. */
  useEffect(() => {
    refresh()
  }, [refresh])

  /*
   * The tick.
   *
   * Runs once a second while something is counting down. When it reaches zero
   * we ask the server for a fresh status rather than deciding the new state
   * ourselves - ACTIVE becoming COOLDOWN is the server's call to make.
   */
  useEffect(() => {
    if (!status || status.state === 'AVAILABLE') return

    const interval = setInterval(() => {
      if (targetRef.current === null) return

      const secondsLeft = Math.max(
        0,
        Math.round((targetRef.current - Date.now()) / 1000),
      )

      setRemaining(secondsLeft)

      if (secondsLeft === 0) {
        clearInterval(interval)
        refresh()
      }
    }, 1000)

    /*
     * Clearing the interval when the effect re-runs or the component unmounts
     * is essential. Without it, every state change would start another interval
     * and they would all keep running forever.
     */
    return () => clearInterval(interval)
  }, [status, refresh])

  /*
   * A tab that was in the background may have had its timer throttled, so
   * re-sync with the server the moment it becomes visible again.
   */
  useEffect(() => {
    function handleVisibility() {
      if (document.visibilityState === 'visible') refresh()
    }

    document.addEventListener('visibilitychange', handleVisibility)
    return () => document.removeEventListener('visibilitychange', handleVisibility)
  }, [refresh])

  /* --------------------------------------------------------------- actions */

  const act = useCallback(
    async (action: 'start' | 'extend' | 'end') => {
      if (!userId) return

      try {
        const next = await breaks[action]()
        applyStatus(next)
        setError('')
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Something went wrong')
        // Whatever went wrong, the server knows the truth - go and ask it.
        refresh()
      }
    },
    [userId, refresh],
  )

  return {
    status,
    remaining,
    loading,
    error,
    clearError: () => setError(''),
    start: () => act('start'),
    extend: () => act('extend'),
    end: () => act('end'),
    refresh,
  }
}

/*
 * Seconds -> "4:07". Used for both the break timer and the cooldown.
 */
export function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/*
 * Seconds -> "43 min" / "1h 12m". For the cooldown label, where a ticking
 * seconds display would just be a countdown to something 40 minutes away.
 */
export function formatWait(totalSeconds: number): string {
  const minutes = Math.ceil(totalSeconds / 60)

  if (minutes < 60) return `${minutes} min`

  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}
