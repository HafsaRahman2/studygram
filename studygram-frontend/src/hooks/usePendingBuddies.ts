import { useCallback, useEffect, useState } from 'react'
import { buddies } from '../api'

/*
 * usePendingBuddies - How many people are waiting for you to answer
 *
 * Powers the badge on the Buddies nav link. A request nobody sees is a request
 * nobody answers, and the whole feature dies quietly if the only way to notice
 * one is to visit the page speculatively.
 *
 * Deliberately fetches only the count, not the list. The Buddies page loads
 * the real data when it opens; the badge just needs a number, and duplicating
 * the full fetch here would mean two requests for the same thing on every page
 * load.
 */
export function usePendingBuddies(userId: number | null) {
  const [count, setCount] = useState(0)

  const refresh = useCallback(async () => {
    if (!userId) {
      setCount(0)
      return
    }

    try {
      const pending = await buddies.pending()
      setCount(pending.length)
    } catch {
      /*
       * A badge is not worth an error message. If this fails - offline, or the
       * token just expired - the count simply stays where it was, and the 401
       * handler in api.ts deals with a dead session.
       */
    }
  }, [userId])

  useEffect(() => {
    refresh()
  }, [refresh])

  /*
   * Re-check when the tab regains focus.
   *
   * Someone may have sent you a request while you were in another tab. Polling
   * on a timer would be the obvious fix and the wrong one: it costs a request
   * every few seconds forever, for something that changes a handful of times a
   * week. Checking when you come back is nearly as good and nearly free.
   */
  useEffect(() => {
    function handleVisibility() {
      if (document.visibilityState === 'visible') refresh()
    }

    document.addEventListener('visibilitychange', handleVisibility)
    return () => document.removeEventListener('visibilitychange', handleVisibility)
  }, [refresh])

  return { count, refresh }
}
