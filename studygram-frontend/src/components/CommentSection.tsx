import { useEffect, useState } from 'react'
import { comments as commentsApi } from '../api'
import type { Comment, User } from '../types'
import { timeAgo } from '../utils/format'
import { Avatar, Message, Spinner } from './ui'

/*
 * CommentSection - The comment thread under one post
 *
 * Each instance owns the comments for its own post. That is a deliberate
 * change: the old version kept a single `newComment` string shared by every
 * post on the page, so typing a reply under one post put the same text into
 * the box under all of them.
 *
 * Comments are loaded when the section is first opened rather than with the
 * feed, so a feed of 50 posts does not fetch 50 comment threads nobody looked at.
 */
export function CommentSection({
  postId,
  currentUser,
  onCountChange,
}: {
  postId: number
  currentUser: User
  onCountChange: (delta: number) => void
}) {
  const [comments, setComments] = useState<Comment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [draft, setDraft] = useState('')
  const [submitting, setSubmitting] = useState(false)

  /* Load the thread once, when this section mounts. */
  useEffect(() => {
    let cancelled = false

    commentsApi
      .forPost(postId, currentUser.id)
      .then((list) => {
        if (!cancelled) setComments(list)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [postId, currentUser.id])

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const content = draft.trim()
    if (!content || submitting) return

    setSubmitting(true)
    setError('')

    try {
      const created = await commentsApi.add({
        userId: currentUser.id,
        postId,
        content,
        anonymous: false, // comments are always attributed, for accountability
      })

      /*
       * Append the comment the server returned rather than refetching the whole
       * thread: one less round trip, and the new comment appears instantly.
       */
      setComments((current) => [...current, created])
      setDraft('')
      onCountChange(+1)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not post your comment')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDelete(commentId: number) {
    // Optimistically remove it, then put it back if the server disagrees.
    const previous = comments
    setComments((current) => current.filter((c) => c.id !== commentId))
    onCountChange(-1)

    try {
      await commentsApi.remove(commentId, currentUser.id)
    } catch (err) {
      setComments(previous)
      onCountChange(+1)
      setError(err instanceof Error ? err.message : 'Could not delete that comment')
    }
  }

  return (
    <div className="comments">
      <form className="comment-form" onSubmit={handleSubmit}>
        <Avatar name={currentUser.name ?? currentUser.username} size={32} />
        <input
          type="text"
          value={draft}
          placeholder="Add a comment..."
          onChange={(e) => setDraft(e.target.value)}
          maxLength={500}
          aria-label="Write a comment"
        />
        <button type="submit" className="btn btn-small" disabled={!draft.trim() || submitting}>
          {submitting ? 'Posting...' : 'Post'}
        </button>
      </form>

      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      {loading && <Spinner label="Loading comments" />}

      {!loading && comments.length === 0 && (
        <p className="comments-empty">No comments yet. Start the conversation.</p>
      )}

      <ul className="comment-list">
        {comments.map((comment) => (
          <li key={comment.id} className="comment">
            <Avatar name={comment.authorName} size={32} />

            <div className="comment-body">
              <div className="comment-head">
                <span className="comment-author">
                  {comment.anonymous ? 'Anonymous' : `@${comment.authorUsername}`}
                </span>
                <span className="comment-time">{timeAgo(comment.createdAt)}</span>
              </div>

              <p className="comment-text">{comment.content}</p>
            </div>

            {/* The server decided whether this viewer may delete - see
                CommentResponse.canDelete. We only draw the button. */}
            {comment.canDelete && (
              <button
                className="icon-btn"
                onClick={() => handleDelete(comment.id)}
                aria-label="Delete comment"
                title="Delete comment"
              >
                ×
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
