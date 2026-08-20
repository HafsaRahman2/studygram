import { useEffect, useState } from 'react'
import { communities } from '../api'
import type { Community } from '../types'

/*
 * useTopics - Loads the canonical topic list from the backend.
 *
 * The list used to be a hardcoded 65-item array inside App.tsx, duplicated in
 * spirit by an empty `communities` table on the server. Two lists that are
 * supposed to agree but have no mechanical link will eventually disagree.
 *
 * Now the backend seeds the list (see CommunitySeeder.java) and serves it, so
 * there is exactly one source of truth.
 *
 * The result is cached in a module-level variable, so switching between the
 * feed and the profile page does not refetch 64 rows every time.
 */

let cache: Community[] | null = null

export function useTopics() {
  const [topics, setTopics] = useState<Community[]>(cache ?? [])
  const [loading, setLoading] = useState(cache === null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (cache) return

    let cancelled = false

    communities
      .all()
      .then((list) => {
        if (cancelled) return
        cache = list
        setTopics(list)
      })
      .catch(() => {
        if (cancelled) return
        setError('Could not load the topic list.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    /*
     * The cleanup function runs if the component unmounts before the request
     * finishes. Without the `cancelled` guard, React would warn about setting
     * state on a component that no longer exists.
     */
    return () => {
      cancelled = true
    }
  }, [])

  /* Just the display names, for pickers that do not care about categories. */
  const topicNames = topics.map((t) => t.displayName)

  /*
   * Topics grouped by their category, preserving the order the server sent.
   * Lets the picker render labelled sections instead of one long list.
   */
  const byCategory = topics.reduce<Record<string, Community[]>>((groups, topic) => {
    const key = topic.category ?? 'Other'
    ;(groups[key] ??= []).push(topic)
    return groups
  }, {})

  return { topics, topicNames, byCategory, loading, error }
}
