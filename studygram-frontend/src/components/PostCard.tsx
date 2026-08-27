import { useState } from 'react'
import { posts as postsApi } from '../api'
import type { Post, User } from '../types'
import { timeAgo } from '../utils/format'
import { Avatar, TopicChips } from './ui'
import { CommentSection } from './CommentSection'

/*
 * PostCard - One post in the feed
 *
 * Owns everything about a single post: its helpful button, its comment thread,
 * and its delete action. Keeping that here rather than in the feed means the
 * feed only has to hold a list, and re-rendering one post does not re-render
 * every other one.
 */
export function PostCard({
  post,
  currentUser,
  onUpdate,
  onDelete,
  onTopicClick,
}: {
  post: Post
  currentUser: User
  onUpdate: (post: Post) => void
  onDelete: (postId: number) => void
  onTopicClick?: (topic: string) => void
}) {
  /*
   * A question opens its answers by default when it already has one.
   *
   * An unanswered question is a request for help and its thread is empty; an
   * answered one is the thing people came to read. Making them click to reach
   * the answer would hide the whole point of the post.
   */
  const isQuestion = post.postType === 'QUESTION'
  const [showComments, setShowComments] = useState(isQuestion && post.commentCount > 0)
  const [busy, setBusy] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  /* What replies are called here. Same number, different meaning. */
  const replyWord = isQuestion ? 'Answers' : 'Comments'

  /*
   * Has this user already marked the post helpful?
   *
   * The server sends the usernames of everyone who did, so the button can show
   * its pressed state without a second request per post.
   */
  const markedHelpful = post.helpfulUsers.includes(currentUser.username)

  async function handleHelpful() {
    if (busy) return
    setBusy(true)

    /*
     * OPTIMISTIC UPDATE
     *
     * Update the UI immediately, before the server has replied, so the button
     * responds instantly instead of hesitating for a round trip. If the request
     * fails we put the old value back.
     */
    const previous = post

    onUpdate({
      ...post,
      helpfulCount: post.helpfulCount + (markedHelpful ? -1 : 1),
      helpfulUsers: markedHelpful
        ? post.helpfulUsers.filter((u) => u !== currentUser.username)
        : [...post.helpfulUsers, currentUser.username],
    })

    try {
      const result = await postsApi.toggleHelpful(post.id)
      // Trust the server's numbers over our guess
      onUpdate({
        ...post,
        helpfulCount: result.helpfulCount,
        helpfulUsers: result.helpfulUsers,
      })
    } catch {
      onUpdate(previous)
    } finally {
      setBusy(false)
    }
  }

  async function handleToggleResolved() {
    if (busy) return
    setBusy(true)

    try {
      const updated = await postsApi.toggleResolved(post.id)
      onUpdate({ ...post, resolved: updated.resolved })
    } catch {
      /* Leave the state alone; the button simply did not take. */
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete() {
    setBusy(true)
    try {
      await postsApi.remove(post.id)
      onDelete(post.id)
    } catch {
      setBusy(false)
      setConfirmingDelete(false)
    }
  }

  return (
    <article className={`post-card ${isQuestion ? 'is-question' : ''}`}>
      {isQuestion && (
        <div className="post-kind">
          <span className="kind-badge">
            <span aria-hidden="true">❓</span> Question
          </span>
          {post.resolved && (
            <span className="kind-badge answered">
              <span aria-hidden="true">✓</span> Answered
            </span>
          )}
        </div>
      )}

      <header className="post-head">
        <Avatar name={post.anonymous ? 'Anonymous' : post.authorName} />

        <div className="post-identity">
          <span className="post-author">
            {post.anonymous ? 'Anonymous' : post.authorName}
          </span>
          <span className="post-meta">
            {/* An anonymous post has no username to show - the server sent
                null, so there is nothing here to leak. */}
            {!post.anonymous && post.authorUsername && `@${post.authorUsername} · `}
            {timeAgo(post.createdAt)}
          </span>
        </div>

        {post.anonymous && (
          <span className="badge" title="This post was published anonymously">
            Anonymous
          </span>
        )}
      </header>

      <p className="post-content">{post.content}</p>

      <TopicChips topics={post.topics} onSelect={onTopicClick} />

      <footer className="post-actions">
        <button
          className={`action-btn ${markedHelpful ? 'active' : ''}`}
          onClick={handleHelpful}
          disabled={busy}
          aria-pressed={markedHelpful}
        >
          <span aria-hidden="true">{markedHelpful ? '★' : '☆'}</span>
          Lifesaver
          {post.helpfulCount > 0 && <span className="count">{post.helpfulCount}</span>}
        </button>

        <button
          className={`action-btn ${showComments ? 'active' : ''}`}
          onClick={() => setShowComments((open) => !open)}
          aria-expanded={showComments}
        >
          <span aria-hidden="true">💬</span>
          {replyWord}
          {post.commentCount > 0 && <span className="count">{post.commentCount}</span>}
        </button>

        {/* Only the asker sees this - they are the one who knows if it helped. */}
        {isQuestion && post.ownPost && (
          <button
            className={`action-btn ${post.resolved ? 'active' : ''}`}
            onClick={handleToggleResolved}
            disabled={busy}
            aria-pressed={post.resolved}
            title={
              post.resolved
                ? 'Mark this as still needing an answer'
                : 'Mark this question answered'
            }
          >
            <span aria-hidden="true">✓</span>
            {post.resolved ? 'Answered' : 'Mark answered'}
          </button>
        )}

        {/* `ownPost` is a yes/no answer about the viewer. It works for
            anonymous posts too, without revealing who wrote anyone else's. */}
        {post.ownPost &&
          (confirmingDelete ? (
            <span className="confirm-delete">
              Delete this post?
              <button className="action-btn danger" onClick={handleDelete} disabled={busy}>
                Yes, delete
              </button>
              <button className="action-btn" onClick={() => setConfirmingDelete(false)}>
                Cancel
              </button>
            </span>
          ) : (
            <button
              className="action-btn subtle"
              onClick={() => setConfirmingDelete(true)}
            >
              Delete
            </button>
          ))}
      </footer>

      {showComments && (
        <CommentSection
          postId={post.id}
          currentUser={currentUser}
          isQuestion={isQuestion}
          onCountChange={(delta) =>
            onUpdate({ ...post, commentCount: Math.max(0, post.commentCount + delta) })
          }
        />
      )}
    </article>
  )
}
