import { useEffect, useState } from 'react'
import { auth, github, posts as postsApi, profile as profileApi } from '../api'
import type { Post, User } from '../types'
import { parseInterests } from '../utils/format'
import { Avatar, EmptyState, Message, PasswordInput, Spinner, TopicChips } from './ui'
import { MIN_PASSWORD_LENGTH, passwordStrength } from '../utils/password'

/*
 * The same limits signup enforces, and the same ones UserService now checks.
 *
 * Signup makes you pick between two and five interests, but the profile let you
 * save none - and then "For you" was permanently empty with nothing on screen
 * explaining why the feed had died. It also let you pick ten, which signup
 * would have refused. The rule has to be the same rule wherever you edit them.
 */
const MIN_INTERESTS = 2
const MAX_INTERESTS = 5
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
   * A row on your own profile.
   *
   * `privateToYou` marks contact details, which other people never see. It is a
   * note rather than a state: you are looking at your own profile, so the value
   * itself is shown - hiding your own email from you would be absurd.
   *
   * An empty field still says "Not set" rather than rendering blank, because a
   * blank line reads as broken rather than as unfilled.
   */
  function Row({
    label,
    value,
    privateToYou,
  }: {
    label: string
    value?: string | null
    privateToYou?: boolean
  }) {
    return (
      <div className="profile-row">
        <span className="profile-label">{label}</span>
        <span>
          {value ?? <span className="muted">Not set</span>}
          {value && privateToYou && <span className="private-note">only you</span>}
        </span>
      </div>
    )
  }

  return (
    <section className="card">
      <header className="profile-head">
        <Avatar name={currentUser.name ?? currentUser.username} size={72} />

        <div className="profile-identity">
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
        <Row label="Email" value={currentUser.email} privateToYou />
        <Row label="Education" value={currentUser.education} />
        <Row label="GitHub" value={currentUser.githubUsername} />
      </div>

      <p className="privacy-note">
        Your email is never sent to anyone else. The server leaves it out of the
        response entirely, so there is nothing to find in the page source.
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

  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    if (interests.length < MIN_INTERESTS) {
      onError(`Pick at least ${MIN_INTERESTS} interests so your feed keeps working.`)
      return
    }

    setSaving(true)

    try {
      /*
       * The privacy flags are deliberately NOT sent.
       *
       * The backend only updates fields that arrive non-null, so omitting them
       * preserves whatever is stored. Email and phone are hidden by default
       * (see User.java) and there is no longer a UI for changing that - a safe
       * default replaced seven switches nobody used.
       */
      const updated = await profileApi.update({
        name,
        education: education || undefined,
        // Store interests back as the comma-separated string the API expects
        interests: interests.join(', ') || undefined,
        careerGoal: careerGoal || undefined,
        githubUsername: githubUsername.trim() || undefined,
      })

      onSaved(updated)
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Could not save your profile')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="card">
      <h2>Edit profile</h2>
      <p className="muted">Your email is never shown to other people.</p>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="p-name">Name</label>
          <input
            id="p-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="p-email">Email</label>
          <input id="p-email" type="email" value={currentUser.email ?? ''} disabled />
          <small className="field-hint">
            Only visible to you. Email cannot be changed.
          </small>
        </div>

        <div className="field">
          <label htmlFor="p-edu">Education</label>
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
          <TopicPicker
            selected={interests}
            onChange={setInterests}
            label="Interests"
            max={MAX_INTERESTS}
            placeholder="Search topics you want in your feed..."
          />
          <small className="field-hint">
            These decide what shows up in your <strong>For you</strong> feed.
          </small>
        </div>

        <div className="field">
          <label htmlFor="p-goal">Career goal</label>
          <input
            id="p-goal"
            type="text"
            value={careerGoal}
            onChange={(e) => setCareerGoal(e.target.value)}
            placeholder="e.g. Software Engineer"
          />
        </div>

        <div className="field">
          <label htmlFor="p-gh">GitHub username</label>
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
  const [visible, setVisible] = useState(false)

  /*
   * The same check signup runs, from the same file.
   *
   * This form used to enforce its own rule - "at least 6 characters" - while
   * the server enforces eight and refuses the few hundred most common
   * passwords. So a seven-character password passed here and was rejected
   * there, and the message you got back was not the one you had just been
   * shown. The form was, in effect, giving out wrong information about the
   * rules.
   *
   * passwordStrength mirrors PasswordPolicy.java. One rule, stated in one
   * place, and the two forms cannot drift apart again.
   */
  const strength = passwordStrength(next)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    if (next !== confirm) {
      setError('The two new passwords do not match.')
      return
    }

    if (!strength.acceptable) {
      setError(strength.problem!)
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
            <PasswordInput
              id="cp-current"
              value={current}
              onChange={setCurrent}
              autoComplete="current-password"
              required
              visible={visible}
              onToggleVisible={() => setVisible((v) => !v)}
            />
          </div>

          <div className="field">
            <label htmlFor="cp-new">New password</label>
            <PasswordInput
              id="cp-new"
              value={next}
              onChange={setNext}
              placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
              autoComplete="new-password"
              required
              visible={visible}
              onToggleVisible={() => setVisible((v) => !v)}
            />
            {/* Say what is wrong while they type, not after they submit. */}
            {next.length > 0 && !strength.acceptable && (
              <small className="field-hint">{strength.problem}</small>
            )}
          </div>

          <div className="field">
            <label htmlFor="cp-confirm">Confirm new password</label>
            <PasswordInput
              id="cp-confirm"
              value={confirm}
              onChange={setConfirm}
              autoComplete="new-password"
              required
              visible={visible}
              onToggleVisible={() => setVisible((v) => !v)}
            />
            {confirm.length > 0 && next !== confirm && (
              <small className="field-hint">These do not match.</small>
            )}
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
