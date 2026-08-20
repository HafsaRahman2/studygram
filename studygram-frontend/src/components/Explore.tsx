import { useEffect, useState } from 'react'
import { communities as communitiesApi } from '../api'
import { useTopics } from '../hooks/useTopics'
import type { Post, User } from '../types'
import { EmptyState, Message, SkeletonPost, Spinner } from './ui'
import { PostCard } from './PostCard'

/*
 * Explore - Browse posts by community
 *
 * This is the Community feature finally switched on. The backend had endpoints
 * for it all along, but the communities table was never populated, so
 * GET /api/communities returned an empty array and nothing was ever built on
 * top of it. CommunitySeeder now fills that table on startup.
 */
export function Explore({ currentUser }: { currentUser: User }) {
  const { byCategory, loading: topicsLoading, error: topicsError } = useTopics()

  const [selected, setSelected] = useState<string | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [loadingPosts, setLoadingPosts] = useState(false)
  const [postsError, setPostsError] = useState('')

  useEffect(() => {
    if (!selected) return

    let cancelled = false
    setLoadingPosts(true)
    setPostsError('')

    communitiesApi
      .postsIn(selected)
      .then((list) => {
        if (!cancelled) setPosts(list)
      })
      .catch((err) => {
        if (!cancelled) {
          setPostsError(err instanceof Error ? err.message : 'Could not load posts')
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingPosts(false)
      })

    return () => {
      cancelled = true
    }
  }, [selected])

  /* ------------------------------------------------- one community's posts */
  if (selected) {
    const community = Object.values(byCategory)
      .flat()
      .find((c) => c.name === selected)

    return (
      <div className="explore">
        <button className="link back-link" onClick={() => setSelected(null)}>
          ← All topics
        </button>

        <header className="community-head">
          <h2>{community?.displayName ?? selected}</h2>
          {community?.description && <p className="muted">{community.description}</p>}
        </header>

        <Message kind="error" onDismiss={() => setPostsError('')}>
          {postsError}
        </Message>

        {loadingPosts && (
          <>
            <SkeletonPost />
            <SkeletonPost />
          </>
        )}

        {!loadingPosts && posts.length === 0 && (
          <EmptyState icon="🌱" title="No posts here yet">
            Be the first to post about {community?.displayName ?? selected}.
          </EmptyState>
        )}

        {!loadingPosts &&
          posts.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              currentUser={currentUser}
              onUpdate={(updated) =>
                setPosts((current) =>
                  current.map((p) => (p.id === updated.id ? updated : p)),
                )
              }
              onDelete={(id) =>
                setPosts((current) => current.filter((p) => p.id !== id))
              }
            />
          ))}
      </div>
    )
  }

  /* ------------------------------------------------------ the topic index */
  return (
    <div className="explore">
      <header className="explore-head">
        <h2>Explore topics</h2>
        <p className="muted">
          Every topic is its own community. Pick one to see what people are
          learning.
        </p>
      </header>

      <Message kind="error">{topicsError}</Message>

      {topicsLoading && <Spinner label="Loading topics" />}

      {Object.entries(byCategory).map(([category, list]) => (
        <section key={category} className="topic-category">
          <h3>{category}</h3>
          <div className="topic-grid">
            {list.map((community) => (
              <button
                key={community.name}
                className="topic-tile"
                onClick={() => setSelected(community.name)}
              >
                {community.displayName}
              </button>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
