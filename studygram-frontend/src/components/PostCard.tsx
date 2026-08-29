import { useState } from 'react'
import { posts as postsApi } from '../api'
import type { Post, User } from '../types'
import { timeAgo } from '../utils/format'
import { Avatar, Message, TopicChips } from './ui'
import { CommentSection } from './CommentSection'
import { TopicPicker } from './TopicPicker'

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

  /*
   * Editing happens in place, on the card.
   *
   * Without this the only way to fix a typo was to delete and repost, which
   * throws away every answer and every mark the post had collected. A very
   * steep price for a missing letter.
   */
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(post.content)
  const [draftTopics, setDraftTopics] = useState<string[]>(post.topics)
  const [editError, setEditError] = useState('')

  function startEditing() {
    /* Start from what is currently saved, not from an older abandoned draft. */
    setDraft(post.content)
    setDraftTopics(post.topics)
    setEditError('')
    setEditing(true)
  }

  async function handleSaveEdit() {
    const content = draft.trim()

    if (!content) {
      setEditError('A post cannot be empty.')
      return
    }

    if (draftTopics.length === 0) {
      setEditError('Keep at least one topic so the right people still see this.')
      return
    }

    /* Nothing actually changed - close without troubling the server. */
    const sameTopics =
      draftTopics.length === post.topics.length &&
      draftTopics.every((t) => post.topics.includes(t))

    if (content === post.content && sameTopics) {
      setEditing(false)
      return
    }

    setBusy(true)
    setEditError('')

    try {
      const updated = await postsApi.update(post.id, { content, topics: draftTopics })
      onUpdate(updated)
      setEditing(false)
    } catch (err) {
      setEditError(err instanceof Error ? err.message : 'Could not save your changes')
    } finally {
      setBusy(false)
    }
  }

  /* What replies are called here. Same number, different meaning. */
  const replyWord = isQuestion ? 'Answers' : 'Comments'

  /*
   * What MARKING a post means depends on what kind of post it is.
   *
   * "Lifesaver" is right on a tip: somebody wrote down the thing that finally
   * made it click, and it saved you. On a QUESTION it is backwards - the
   * question did not save anybody, it is the thing asking to be saved.
   *
   * So questions get "Same here", which is the thing a stuck person actually
   * wants to say and currently has no way to: I do not understand this either.
   * That is worth expressing twice over - it tells the rest of the community
   * this question is worth answering, and it tells the asker they are not the
   * only one, which is the whole promise on the landing page.
   *
   * Same button, same count, same table underneath. Only the wording changes,
   * and nobody sees both on one post.
   */
  const mark = isQuestion
    ? {
        label: 'Same here',
        /*
         * The icon changes SHAPE, not just colour, between the two states.
         * .active only shifts the colour, and colour alone is not a state
         * anybody can rely on - roughly one man in twelve cannot tell these
         * two greens apart.
         */
        idle: '＋',
        marked: '✓',
        hint: "You're stuck on this too",
      }
    : {
        label: 'Lifesaver',
        idle: '☆',
        marked: '★',
        hint: 'This post got you unstuck',
      }

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
            {/*
              Says the post was changed after people may have replied to it.
              An answer written against the old wording otherwise looks like the
              answerer misread the question, when really the question moved.
            */}
            {post.editedAt && (
              <span className="edited-note" title={`Edited ${timeAgo(post.editedAt)}`}>
                {' '}· edited
              </span>
            )}
          </span>
        </div>

        {post.anonymous && (
          <span className="badge" title="This post was published anonymously">
            Anonymous
          </span>
        )}
      </header>

      {editing ? (
        <div className="post-edit">
          <label className="sr-only" htmlFor={`edit-${post.id}`}>
            Edit your post
          </label>
          <textarea
            id={`edit-${post.id}`}
            className="post-edit-text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={4}
            maxLength={2000}
            autoFocus
          />

          <TopicPicker
            selected={draftTopics}
            onChange={setDraftTopics}
            label="Topics"
            max={5}
            placeholder="Add a topic..."
          />

          <Message kind="error" onDismiss={() => setEditError('')}>
            {editError}
          </Message>

          <div className="post-edit-actions">
            <button className="btn btn-small" onClick={handleSaveEdit} disabled={busy}>
              {busy ? 'Saving...' : 'Save changes'}
            </button>
            <button className="link" onClick={() => setEditing(false)} disabled={busy}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <>
          <p className="post-content">{post.content}</p>

          <TopicChips topics={post.topics} onSelect={onTopicClick} />
        </>
      )}

      <footer className="post-actions">
        <button
          className={`action-btn ${markedHelpful ? 'active' : ''}`}
          onClick={handleHelpful}
          disabled={busy}
          aria-pressed={markedHelpful}
          title={mark.hint}
        >
          <span aria-hidden="true">{markedHelpful ? mark.marked : mark.idle}</span>
          {mark.label}
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
        {/* Editing changes only the words and topics - see PostService.updatePost. */}
        {post.ownPost && !editing && !confirmingDelete && (
          <button className="action-btn subtle" onClick={startEditing}>
            Edit
          </button>
        )}

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
