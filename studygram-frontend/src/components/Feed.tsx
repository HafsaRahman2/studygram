import { useCallback, useEffect, useRef, useState } from 'react'
import { communities as communitiesApi, posts as postsApi } from '../api'
import { useTopics } from '../hooks/useTopics'
import type { Post, PostType, User } from '../types'
import { parseInterests } from '../utils/format'
import { Avatar, EmptyState, Message, SkeletonPost } from './ui'
import { PostCard } from './PostCard'
import { TopicPicker } from './TopicPicker'

/*
 * Feed - The timeline, and now also the only place you browse topics
 *
 * WHAT CHANGED AND WHY
 *
 * There used to be a separate Explore page listing every topic, and clicking
 * one showed that community's posts. But that is what the feed already does
 * when you click a topic chip on a post - the same destination reached two
 * different ways, costing a whole item in the navigation bar.
 *
 * So Explore folded in here as a third mode. The category browsing it offered
 * survives inside the picker; what is gone is the extra page.
 *
 * Nav went from six items to four. Nothing was deleted from the backend - the
 * /api/communities endpoints are unchanged and still used.
 */

type FeedMode = 'all' | 'foryou' | 'topic'

export function Feed({ currentUser }: { currentUser: User }) {
  const [mode, setMode] = useState<FeedMode>('all')
  const [topic, setTopic] = useState<string | null>(null)

  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const hasInterests = parseInterests(currentUser.interests).length > 0

  /*
   * Which posts to show.
   *
   * The topic case now asks the SERVER for that community's posts, rather than
   * filtering whatever happened to already be loaded. The old client-side
   * filter silently only searched the most recent page of posts, so a topic
   * with nothing recent looked empty even when it was not.
   */
  const loadPosts = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      let data: Post[]

      if (mode === 'topic' && topic) {
        data = await communitiesApi.postsIn(topic.toLowerCase())
      } else if (mode === 'foryou') {
        data = await postsApi.personalizedFeed()
      } else {
        data = await postsApi.feed()
      }

      setPosts(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the feed')
    } finally {
      setLoading(false)
    }
  }, [mode, topic])

  useEffect(() => {
    loadPosts()
  }, [loadPosts])

  function showTopic(name: string) {
    setTopic(name)
    setMode('topic')
  }

  function updatePost(updated: Post) {
    setPosts((current) => current.map((p) => (p.id === updated.id ? updated : p)))
  }

  function removePost(postId: number) {
    setPosts((current) => current.filter((p) => p.id !== postId))
  }

  return (
    <div className="feed">
      <Composer
        currentUser={currentUser}
        onPosted={(created) => setPosts((current) => [created, ...current])}
        onPostUpdated={updatePost}
      />

      <div className="tabs" role="tablist">
        <button
          role="tab"
          aria-selected={mode === 'all'}
          className={`tab ${mode === 'all' ? 'active' : ''}`}
          onClick={() => setMode('all')}
        >
          All posts
        </button>

        <button
          role="tab"
          aria-selected={mode === 'foryou'}
          className={`tab ${mode === 'foryou' ? 'active' : ''}`}
          onClick={() => setMode('foryou')}
          disabled={!hasInterests}
          title={
            hasInterests
              ? 'Posts matching your interests'
              : 'Add interests to your profile to use this'
          }
        >
          For you
        </button>

        <TopicMenu
          active={mode === 'topic' ? topic : null}
          onSelect={showTopic}
          onClear={() => {
            setTopic(null)
            setMode('all')
          }}
        />
      </div>

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

      {!loading && posts.length === 0 && (
        <EmptyState
          icon={mode === 'topic' ? '🌱' : mode === 'foryou' ? '✨' : '📝'}
          title={
            mode === 'topic'
              ? `Nothing about ${topic} yet`
              : mode === 'foryou'
                ? 'Nothing matching your interests yet'
                : 'No posts yet'
          }
        >
          {mode === 'topic'
            ? 'Be the first to post about it.'
            : mode === 'foryou'
              ? 'Try All posts, or add more interests to your profile.'
              : 'Share something you learned and get the feed started.'}
        </EmptyState>
      )}

      {!loading &&
        posts.map((post) => (
          <PostCard
            key={post.id}
            post={post}
            currentUser={currentUser}
            onUpdate={updatePost}
            onDelete={removePost}
            onTopicClick={showTopic}
          />
        ))}
    </div>
  )
}

/* -------------------------------------------------------------- Composer */

/*
 * Collapsed by default.
 *
 * The feed used to open with a textarea, a topic picker, a checkbox and a Post
 * button stacked above the first post - four decisions before you had read
 * anything. Most visits to a feed are to read, not to write.
 *
 * So it starts as one line and opens when clicked. The full form is unchanged
 * once expanded; it just stops being the first thing in your way.
 */
function Composer({
  currentUser,
  onPosted,
  onPostUpdated,
}: {
  currentUser: User
  onPosted: (post: Post) => void
  onPostUpdated: (post: Post) => void
}) {
  const [open, setOpen] = useState(false)

  /*
   * Asking and sharing are different acts, so the composer asks which one up
   * front rather than guessing. It changes the placeholder, the button, and
   * whether the AI option appears at all - an AI answer makes no sense on
   * "here's what I learned today".
   */
  const [postType, setPostType] = useState<PostType>('QUESTION')

  const [content, setContent] = useState('')
  const [topics, setTopics] = useState<string[]>([])
  const [anonymous, setAnonymous] = useState(false)
  const [askAi, setAskAi] = useState(true)
  const [posting, setPosting] = useState(false)
  const [error, setError] = useState('')

  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const MAX_LENGTH = 2000

  /* Put the cursor in the box when it opens, so the click that opened it is
     the only click needed before typing. */
  useEffect(() => {
    if (open) textareaRef.current?.focus()
  }, [open])

  function reset() {
    setContent('')
    setTopics([])
    setAnonymous(false)
    setAskAi(true)
    setPostType('QUESTION')
    setError('')
    setOpen(false)
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    if (!content.trim()) {
      setError('Write something first.')
      return
    }

    if (topics.length === 0) {
      setError('Pick at least one topic so the right people see this.')
      return
    }

    setPosting(true)

    try {
      const created = await postsApi.create({
        content: content.trim(),
        topics,
        anonymous,
        postType,
      })

      onPosted(created)
      reset()

      /*
       * The post is already on screen; the AI answer is fetched afterwards.
       *
       * Doing it here rather than inside create() is what keeps posting
       * instant - generating an answer takes seconds, and nobody should watch
       * a spinner on their own post while a language model thinks.
       */
      if (postType === 'QUESTION' && askAi) {
        postsApi
          .aiAnswer(created.id)
          .then(() =>
            onPostUpdated({
              ...created,
              hasAiAnswer: true,
              commentCount: created.commentCount + 1,
            }),
          )
          .catch(() => {
            /*
             * The AI being down must not look like the POST failing - the
             * question was published and is perfectly usable without it.
             * People can still answer.
             */
          })
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not publish your post')
    } finally {
      setPosting(false)
    }
  }

  if (!open) {
    return (
      <button className="composer-collapsed" onClick={() => setOpen(true)}>
        <Avatar name={currentUser.name ?? currentUser.username} size={36} />
        <span>Ask a question, or share what you learned...</span>
      </button>
    )
  }

  const isQuestion = postType === 'QUESTION'

  const remaining = MAX_LENGTH - content.length

  return (
    <section className="composer">
      <form onSubmit={handleSubmit}>
        {/* Ask or share - chosen first, because it changes everything below. */}
        <div className="type-switch" role="radiogroup" aria-label="What kind of post">
          <button
            type="button"
            role="radio"
            aria-checked={isQuestion}
            className={`type-option ${isQuestion ? 'active' : ''}`}
            onClick={() => setPostType('QUESTION')}
          >
            <span aria-hidden="true">❓</span> Ask a question
          </button>
          <button
            type="button"
            role="radio"
            aria-checked={!isQuestion}
            className={`type-option ${!isQuestion ? 'active' : ''}`}
            onClick={() => setPostType('SHARE')}
          >
            <span aria-hidden="true">✎</span> Share what you learned
          </button>
        </div>

        <div className="composer-head">
          <Avatar name={currentUser.name ?? currentUser.username} size={36} />
          <textarea
            ref={textareaRef}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder={
              isQuestion
                ? "What are you stuck on? Include what you've already tried."
                : 'A tip, a breakthrough — anything that helps someone else.'
            }
            rows={3}
            maxLength={MAX_LENGTH}
            aria-label={isQuestion ? 'Your question' : 'What you learned'}
          />
        </div>

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

        <Message kind="error" onDismiss={() => setError('')}>
          {error}
        </Message>

        <div className="composer-options">
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

          {/* Only on questions: an answer to "here's what I learned" is odd. */}
          {isQuestion && (
            <label className="checkbox">
              <input
                type="checkbox"
                checked={askAi}
                onChange={(e) => setAskAi(e.target.checked)}
              />
              <span>
                Also get an instant AI answer
                <small>
                  Something to work with straight away. People can still answer.
                </small>
              </span>
            </label>
          )}
        </div>

        <div className="composer-actions">
          <button type="button" className="link" onClick={reset}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={posting || !content.trim()}>
            {posting ? 'Posting...' : isQuestion ? 'Ask' : 'Share'}
          </button>
        </div>
      </form>
    </section>
  )
}

/* ------------------------------------------------------------- TopicMenu */

/*
 * The topic browser that replaced the Explore page.
 *
 * Shows every topic grouped by category - the one genuinely useful thing
 * Explore did - but as a menu inside the feed rather than a separate
 * destination.
 */
function TopicMenu({
  active,
  onSelect,
  onClear,
}: {
  active: string | null
  onSelect: (topic: string) => void
  onClear: () => void
}) {
  const { byCategory, loading } = useTopics()
  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const containerRef = useRef<HTMLDivElement>(null)

  /* Close when clicking anywhere else. */
  useEffect(() => {
    if (!open) return

    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  useEffect(() => {
    if (!open) setSearch('')
  }, [open])

  /* When a topic is showing, the control becomes a removable chip instead. */
  if (active) {
    return (
      <button className="tab filter-pill" onClick={onClear} title="Show all posts again">
        {active} <span aria-hidden="true">×</span>
      </button>
    )
  }

  const term = search.trim().toLowerCase()

  return (
    <div className="topic-menu" ref={containerRef}>
      <button
        className="tab"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        disabled={loading}
      >
        Browse topics <span aria-hidden="true">▾</span>
      </button>

      {open && (
        <div className="topic-menu-dropdown">
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter topics..."
            aria-label="Filter topics"
            autoFocus
          />

          <div className="topic-menu-list">
            {Object.entries(byCategory).map(([category, list]) => {
              const matches = term
                ? list.filter((t) => t.displayName.toLowerCase().includes(term))
                : list

              if (matches.length === 0) return null

              return (
                <div key={category} className="picker-group">
                  <div className="picker-group-label">{category}</div>
                  {matches.map((community) => (
                    <button
                      key={community.name}
                      className="picker-option"
                      onClick={() => {
                        onSelect(community.displayName)
                        setOpen(false)
                      }}
                    >
                      {community.displayName}
                    </button>
                  ))}
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
