import { useCallback, useEffect, useState } from 'react'
import { posts as postsApi } from '../api'
import type { Post, User } from '../types'
import { parseInterests } from '../utils/format'
import { Avatar, EmptyState, Message, SkeletonPost } from './ui'
import { PostCard } from './PostCard'
import { TopicPicker } from './TopicPicker'

type FeedTab = 'all' | 'foryou'

/*
 * Feed - The main timeline: a composer at the top, then the posts.
 */
export function Feed({ currentUser }: { currentUser: User }) {
  const [tab, setTab] = useState<FeedTab>('all')
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  /* When a topic chip is clicked, narrow the list to that topic. */
  const [topicFilter, setTopicFilter] = useState<string | null>(null)

  /* Composer state */
  const [content, setContent] = useState('')
  const [topics, setTopics] = useState<string[]>([])
  const [anonymous, setAnonymous] = useState(false)
  const [posting, setPosting] = useState(false)
  const [composerError, setComposerError] = useState('')

  const hasInterests = parseInterests(currentUser.interests).length > 0
  const MAX_LENGTH = 2000

  /*
   * useCallback stops this function being rebuilt on every render, which
   * matters because the effect below lists it as a dependency - without it the
   * effect would re-run constantly and refetch the feed in a loop.
   */
  const loadPosts = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const data =
        tab === 'foryou'
          ? await postsApi.personalizedFeed(currentUser.id)
          : await postsApi.feed(currentUser.id)

      setPosts(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the feed')
    } finally {
      setLoading(false)
    }
  }, [tab, currentUser.id])

  useEffect(() => {
    loadPosts()
  }, [loadPosts])

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setComposerError('')

    if (!content.trim()) {
      setComposerError('Write something first.')
      return
    }

    if (topics.length === 0) {
      setComposerError('Pick at least one topic so the right people see this.')
      return
    }

    setPosting(true)

    try {
      const created = await postsApi.create({
        userId: currentUser.id,
        content: content.trim(),
        topics,
        anonymous,
      })

      // Put the new post straight at the top rather than refetching everything
      setPosts((current) => [created, ...current])
      setContent('')
      setTopics([])
      setAnonymous(false)
    } catch (err) {
      setComposerError(err instanceof Error ? err.message : 'Could not publish your post')
    } finally {
      setPosting(false)
    }
  }

  /* Replace one post in the list, leaving the others untouched. */
  function updatePost(updated: Post) {
    setPosts((current) => current.map((p) => (p.id === updated.id ? updated : p)))
  }

  function removePost(postId: number) {
    setPosts((current) => current.filter((p) => p.id !== postId))
  }

  /* Client-side narrowing by a clicked topic chip. */
  const visiblePosts = topicFilter
    ? posts.filter((p) =>
        p.topics.some((t) => t.toLowerCase() === topicFilter.toLowerCase()),
      )
    : posts

  const remaining = MAX_LENGTH - content.length

  return (
    <div className="feed">
      {/* ------------------------------------------------------- composer */}
      <section className="composer">
        <div className="composer-head">
          <Avatar name={currentUser.name ?? currentUser.username} />
          <div>
            <strong>Share what you learned</strong>
            <p className="composer-hint">
              A tip, a question, a breakthrough — anything that helps someone else.
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="What did you learn today?"
            rows={3}
            maxLength={MAX_LENGTH}
            aria-label="Post content"
          />

          {/* Only warn when the limit is actually close, so the counter is a
              signal rather than permanent decoration. */}
          {remaining < 200 && (
            <div className={`char-count ${remaining < 0 ? 'over' : ''}`}>
              {remaining} characters left
            </div>
          )}

          <TopicPicker
            selected={topics}
            onChange={setTopics}
            label="Topics"
            max={5}
            placeholder="Add a topic so the right people see this..."
          />

          <Message kind="error" onDismiss={() => setComposerError('')}>
            {composerError}
          </Message>

          <div className="composer-actions">
            <label className="checkbox">
              <input
                type="checkbox"
                checked={anonymous}
                onChange={(e) => setAnonymous(e.target.checked)}
              />
              <span>
                Post anonymously
                <small>Your name and username are never sent with the post.</small>
              </span>
            </label>

            <button type="submit" className="btn" disabled={posting || !content.trim()}>
              {posting ? 'Posting...' : 'Post'}
            </button>
          </div>
        </form>
      </section>

      {/* ----------------------------------------------------------- tabs */}
      <div className="tabs" role="tablist">
        <button
          role="tab"
          aria-selected={tab === 'all'}
          className={`tab ${tab === 'all' ? 'active' : ''}`}
          onClick={() => setTab('all')}
        >
          All posts
        </button>

        <button
          role="tab"
          aria-selected={tab === 'foryou'}
          className={`tab ${tab === 'foryou' ? 'active' : ''}`}
          onClick={() => setTab('foryou')}
          disabled={!hasInterests}
          title={
            hasInterests
              ? 'Posts matching your interests'
              : 'Add interests to your profile to use this'
          }
        >
          For you
        </button>

        {topicFilter && (
          <button className="tab filter-pill" onClick={() => setTopicFilter(null)}>
            {topicFilter} <span aria-hidden="true">×</span>
          </button>
        )}
      </div>

      {/* ---------------------------------------------------------- posts */}
      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      {loading && (
        <>
          <SkeletonPost />
          <SkeletonPost />
          <SkeletonPost />
        </>
      )}

      {!loading && visiblePosts.length === 0 && (
        <EmptyState
          icon={topicFilter ? '🔍' : tab === 'foryou' ? '✨' : '📝'}
          title={
            topicFilter
              ? `Nothing about ${topicFilter} yet`
              : tab === 'foryou'
                ? 'Nothing matching your interests yet'
                : 'No posts yet'
          }
        >
          {topicFilter
            ? 'Be the first to post about it.'
            : tab === 'foryou'
              ? 'Try the All posts tab, or add more interests to your profile.'
              : 'Share something you learned and get the feed started.'}
        </EmptyState>
      )}

      {!loading &&
        visiblePosts.map((post) => (
          <PostCard
            key={post.id}
            post={post}
            currentUser={currentUser}
            onUpdate={updatePost}
            onDelete={removePost}
            onTopicClick={setTopicFilter}
          />
        ))}
    </div>
  )
}
