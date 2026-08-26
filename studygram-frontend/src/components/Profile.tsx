import { useEffect, useState } from 'react'
import { auth, github, posts as postsApi, profile as profileApi } from '../api'
import type { Post, User } from '../types'
import { parseInterests } from '../utils/format'
import { Avatar, EmptyState, Message, Spinner, TopicChips } from './ui'
import { PostCard } from './PostCard'
import { TopicPicker } from './TopicPicker'

/*
 * Profile - View and edit your own profile
 *
 * Three sections: the profile itself, an optional GitHub showcase, and your
 * posts. Edit mode swaps the first section for a form.
 */
export function Profile({
  currentUser,
  onUserUpdated,
}: {
  currentUser: User
  onUserUpdated: (user: User) => void
}) {
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  return (
    <div className="profile">
      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>
      <Message kind="success" onDismiss={() => setSuccess('')}>
        {success}
      </Message>

      {editing ? (
        <ProfileForm
          currentUser={currentUser}
          onCancel={() => setEditing(false)}
          onSaved={(updated) => {
            onUserUpdated(updated)
            setEditing(false)
            setSuccess('Profile updated.')
          }}
          onError={setError}
        />
      ) : (
        <ProfileView currentUser={currentUser} onEdit={() => setEditing(true)} />
      )}

      <ChangePassword onSuccess={() => setSuccess('Password changed.')} />

      {currentUser.githubUsername && (
        <GitHubCard username={currentUser.githubUsername} />
      )}

      <MyPosts currentUser={currentUser} />
    </div>
  )
}

/* ----------------------------------------------------------- ProfileView */

function ProfileView({
  currentUser,
  onEdit,
}: {
  currentUser: User
  onEdit: () => void
}) {
  const interests = parseInterests(currentUser.interests)

  /*
   * A field can be in three states, and they mean different things:
   *   filled  -> show it
   *   hidden  -> the user chose to hide it (and the server omitted it entirely)
   *   not set -> they simply have not filled it in
   *
   * Collapsing the last two into one blank line would misrepresent both.
   */
  function Row({
    label,
    value,
    hidden,
  }: {
    label: string
    value?: string | null
    hidden?: boolean
  }) {
    return (
      <div className="profile-row">
        <span className="profile-label">{label}</span>
        {hidden ? (
          <span className="muted">Hidden from others</span>
        ) : value ? (
          <span>{value}</span>
        ) : (
          <span className="muted">Not set</span>
        )}
      </div>
    )
  }

  return (
    <section className="card">
      <header className="profile-head">
        <Avatar name={currentUser.name ?? currentUser.username} size={72} />

        <div>
          <h2>{currentUser.name ?? currentUser.username}</h2>
          <p className="muted">@{currentUser.username}</p>
          {currentUser.careerGoal && (
            <p className="career-goal">🎯 {currentUser.careerGoal}</p>
          )}
        </div>

        <button className="btn btn-secondary" onClick={onEdit}>
          Edit profile
        </button>
      </header>

      {interests.length > 0 && (
        <div className="profile-interests">
          <span className="profile-label">Interests</span>
          <TopicChips topics={interests} />
        </div>
      )}

      <div className="profile-rows">
        <Row label="Email" value={currentUser.email} hidden={currentUser.hideEmail} />
        <Row
          label="Phone"
          value={currentUser.phoneNumber}
          hidden={currentUser.hidePhone}
        />
        <Row
          label="Education"
          value={currentUser.education}
          hidden={currentUser.hideEducation}
        />
        <Row
          label="GitHub"
          value={currentUser.githubUsername}
          hidden={currentUser.hideGithub}
        />
      </div>

      <p className="privacy-note">
        Fields marked hidden are never sent to anyone else — the server leaves
        them out of the response entirely, so there is nothing to find in the page
        source.
      </p>
    </section>
  )
}

/* ----------------------------------------------------------- ProfileForm */

function ProfileForm({
  currentUser,
  onCancel,
  onSaved,
  onError,
}: {
  currentUser: User
  onCancel: () => void
  onSaved: (user: User) => void
  onError: (message: string) => void
}) {
  const [name, setName] = useState(currentUser.name ?? '')
  const [education, setEducation] = useState(currentUser.education ?? '')
  const [careerGoal, setCareerGoal] = useState(currentUser.careerGoal ?? '')
  const [githubUsername, setGithubUsername] = useState(currentUser.githubUsername ?? '')
  const [interests, setInterests] = useState<string[]>(
    parseInterests(currentUser.interests),
  )

  /*
   * All seven privacy switches in one object rather than seven useState calls.
   * Adding an eighth field then means adding one key, not another state hook
   * plus another line in every handler that touches them.
   */
  const [privacy, setPrivacy] = useState({
    hideName: currentUser.hideName,
    hideEmail: currentUser.hideEmail,
    hidePhone: currentUser.hidePhone,
    hideEducation: currentUser.hideEducation,
    hideInterests: currentUser.hideInterests,
    hideCareerGoal: currentUser.hideCareerGoal,
    hideGithub: currentUser.hideGithub,
  })

  const [saving, setSaving] = useState(false)

  function togglePrivacy(key: keyof typeof privacy) {
    setPrivacy((current) => ({ ...current, [key]: !current[key] }))
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)

    try {
      const updated = await profileApi.update({
        name,
        education: education || undefined,
        // Store interests back as the comma-separated string the API expects
        interests: interests.join(', ') || undefined,
        careerGoal: careerGoal || undefined,
        githubUsername: githubUsername.trim() || undefined,
        ...privacy,
      })

      onSaved(updated)
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Could not save your profile')
    } finally {
      setSaving(false)
    }
  }

  /* A field label with its "hide from others" switch beside it. */
  function FieldHeader({
    label,
    privacyKey,
    htmlFor,
  }: {
    label: string
    privacyKey: keyof typeof privacy
    htmlFor?: string
  }) {
    return (
      <div className="field-head">
        <label htmlFor={htmlFor}>{label}</label>
        <label className="switch">
          <input
            type="checkbox"
            checked={privacy[privacyKey]}
            onChange={() => togglePrivacy(privacyKey)}
          />
          <span>Hide</span>
        </label>
      </div>
    )
  }

  return (
    <section className="card">
      <h2>Edit profile</h2>
      <p className="muted">
        Anything you mark <strong>Hide</strong> is withheld by the server, not just
        by this page.
      </p>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <FieldHeader label="Name" privacyKey="hideName" htmlFor="p-name" />
          <input
            id="p-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <FieldHeader label="Email" privacyKey="hideEmail" />
          <input type="email" value={currentUser.email ?? ''} disabled />
          <small className="field-hint">Email cannot be changed.</small>
        </div>

        {currentUser.phoneNumber && (
          <div className="field">
            <FieldHeader label="Phone" privacyKey="hidePhone" />
            <input type="tel" value={currentUser.phoneNumber} disabled />
          </div>
        )}

        <div className="field">
          <FieldHeader label="Education" privacyKey="hideEducation" htmlFor="p-edu" />
          <select
            id="p-edu"
            value={education}
            onChange={(e) => setEducation(e.target.value)}
          >
            <option value="">Select education level</option>
            <option value="school">School</option>
            <option value="college">College</option>
            <option value="university">University</option>
            <option value="other">Other</option>
          </select>
        </div>

        <div className="field">
          <FieldHeader label="Interests" privacyKey="hideInterests" />
          <TopicPicker
            selected={interests}
            onChange={setInterests}
            max={10}
            placeholder="Search topics you want in your feed..."
          />
          <small className="field-hint">
            These decide what shows up in your <strong>For you</strong> feed.
          </small>
        </div>

        <div className="field">
          <FieldHeader label="Career goal" privacyKey="hideCareerGoal" htmlFor="p-goal" />
          <input
            id="p-goal"
            type="text"
            value={careerGoal}
            onChange={(e) => setCareerGoal(e.target.value)}
            placeholder="e.g. Software Engineer"
          />
        </div>

        <div className="field">
          <FieldHeader label="GitHub username" privacyKey="hideGithub" htmlFor="p-gh" />
          <input
            id="p-gh"
            type="text"
            value={githubUsername}
            onChange={(e) => setGithubUsername(e.target.value)}
            placeholder="your-github-username"
          />
          <small className="field-hint">
            Adds your public repositories to your profile.
          </small>
        </div>

        <div className="button-row">
          <button type="submit" className="btn" disabled={saving}>
            {saving ? 'Saving...' : 'Save changes'}
          </button>
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  )
}

/* -------------------------------------------------------- ChangePassword */

function ChangePassword({ onSuccess }: { onSuccess: () => void }) {
  const [open, setOpen] = useState(false)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    if (next !== confirm) {
      setError('The two new passwords do not match.')
      return
    }

    if (next.length < 6) {
      setError('New password must be at least 6 characters.')
      return
    }

    setSaving(true)

    try {
      await auth.changePassword(current, next)
      setCurrent('')
      setNext('')
      setConfirm('')
      setOpen(false)
      onSuccess()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not change your password')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="card">
      <button className="collapse-toggle" onClick={() => setOpen((o) => !o)}>
        <span>Change password</span>
        <span aria-hidden="true">{open ? '−' : '+'}</span>
      </button>

      {open && (
        <form onSubmit={handleSubmit} className="stacked-form">
          <Message kind="error" onDismiss={() => setError('')}>
            {error}
          </Message>

          <div className="field">
            <label htmlFor="cp-current">Current password</label>
            <input
              id="cp-current"
              type="password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>

          <div className="field">
            <label htmlFor="cp-new">New password</label>
            <input
              id="cp-new"
              type="password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              autoComplete="new-password"
              required
            />
          </div>

          <div className="field">
            <label htmlFor="cp-confirm">Confirm new password</label>
            <input
              id="cp-confirm"
              type="password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              autoComplete="new-password"
              required
            />
          </div>

          <button type="submit" className="btn" disabled={saving}>
            {saving ? 'Updating...' : 'Update password'}
          </button>
        </form>
      )}
    </section>
  )
}

/* ------------------------------------------------------------ GitHubCard */

/*
 * Pulls public repositories from the GitHub API via our backend.
 *
 * Failure here is not important enough to interrupt the page - GitHub rate
 * limits unauthenticated requests, and the username might simply be wrong - so
 * the card quietly renders a note instead of an error banner.
 */
function GitHubCard({ username }: { username: string }) {
  const [repos, setRepos] = useState<
    Array<{
      name: string
      description: string | null
      language: string | null
      stars: number
      url: string
    }>
  >([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false

    github
      .repos(username)
      .then((list) => {
        if (!cancelled) setRepos(list.slice(0, 6))
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [username])

  return (
    <section className="card">
      <div className="card-head">
        <h3>GitHub</h3>
        <a
          href={`https://github.com/${username}`}
          target="_blank"
          // noreferrer stops the new tab from seeing where it was opened from,
          // and closes an old security hole where it could navigate this page.
          rel="noreferrer"
          className="link"
        >
          @{username} ↗
        </a>
      </div>

      {loading && <Spinner label="Loading repositories" />}

      {failed && (
        <p className="muted">
          Could not load repositories. GitHub limits how often this can be
          requested without an API key.
        </p>
      )}

      {!loading && !failed && repos.length === 0 && (
        <p className="muted">No public repositories yet.</p>
      )}

      <div className="repo-grid">
        {repos.map((repo) => (
          <a key={repo.name} href={repo.url} target="_blank" rel="noreferrer" className="repo">
            <strong>{repo.name}</strong>
            {repo.description && <p>{repo.description}</p>}
            <div className="repo-meta">
              {repo.language && <span>{repo.language}</span>}
              {repo.stars > 0 && <span>★ {repo.stars}</span>}
            </div>
          </a>
        ))}
      </div>
    </section>
  )
}

/* --------------------------------------------------------------- MyPosts */

function MyPosts({ currentUser }: { currentUser: User }) {
  const [posts, setPosts] = useState<Post[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    postsApi
      .byUser(currentUser.id)
      .then((list) => {
        if (!cancelled) setPosts(list)
      })
      .catch(() => {
        /* handled by the empty state below */
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [currentUser.id])

  return (
    <section className="my-posts">
      <h3>
        Your posts {!loading && posts.length > 0 && <span className="count">{posts.length}</span>}
      </h3>

      {loading && <Spinner label="Loading your posts" />}

      {!loading && posts.length === 0 && (
        <EmptyState icon="📝" title="You have not posted yet">
          Head to the feed and share something you learned.
        </EmptyState>
      )}

      {posts.map((post) => (
        <PostCard
          key={post.id}
          post={post}
          currentUser={currentUser}
          onUpdate={(updated) =>
            setPosts((current) => current.map((p) => (p.id === updated.id ? updated : p)))
          }
          onDelete={(id) => setPosts((current) => current.filter((p) => p.id !== id))}
        />
      ))}
    </section>
  )
}
