import { useState } from 'react'
import { auth, setAuthToken } from '../api'
import type { Page, User } from '../types'
import { Message, PasswordInput } from './ui'

/*
 * The three screens you can reach while logged out: Login, Signup and the
 * password reset flow. Grouped in one file because they share a layout, a
 * visual style, and a set of links between each other.
 */

const MIN_PASSWORD_LENGTH = 6

/* ----------------------------------------------------------------- Login */

export function Login({
  onLoggedIn,
  onNavigate,
}: {
  onLoggedIn: (user: User) => void
  onNavigate: (page: Page) => void
}) {
  const [emailOrPhone, setEmailOrPhone] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')
    setSubmitting(true)

    try {
      const result = await auth.login(emailOrPhone, password)

      /*
       * Store the token BEFORE telling the app we are logged in, so the very
       * first authenticated request (loading the feed) already carries it.
       */
      setAuthToken(result.token)
      onLoggedIn(result.user)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-card">
      <h2>Welcome back</h2>
      <p className="auth-sub">Log in to pick up where you left off.</p>

      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="login-id">Email or phone</label>
          <input
            id="login-id"
            type="text"
            value={emailOrPhone}
            onChange={(e) => setEmailOrPhone(e.target.value)}
            placeholder="you@example.com"
            autoComplete="username"
            required
          />
        </div>

        <div className="field">
          <label htmlFor="login-password">Password</label>
          <PasswordInput
            id="login-password"
            value={password}
            onChange={setPassword}
            placeholder="Your password"
            autoComplete="current-password"
            required
            visible={showPassword}
            onToggleVisible={() => setShowPassword((v) => !v)}
          />
        </div>

        <button type="submit" className="btn btn-block" disabled={submitting}>
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
      </form>

      <div className="auth-links">
        <button className="link" onClick={() => onNavigate('forgot-password')}>
          Forgot your password?
        </button>
        <span>
          New here?{' '}
          <button className="link" onClick={() => onNavigate('signup')}>
            Create an account
          </button>
        </span>
      </div>
    </div>
  )
}

/* ---------------------------------------------------------------- Signup */

export function Signup({
  onLoggedIn,
  onNavigate,
}: {
  onLoggedIn: (user: User) => void
  onNavigate: (page: Page) => void
}) {
  const [name, setName] = useState('')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    if (password !== confirm) {
      setError('The two passwords do not match.')
      return
    }

    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }

    setSubmitting(true)

    try {
      const result = await auth.signup({
        name,
        username,
        email,
        phoneNumber: phone || null,
        password,
      })

      /*
       * Sign up and you are in.
       *
       * This used to show "Account created, you can log in now" and send you to
       * the login form - so you typed your password twice to register, then a
       * third time to actually get in. The server has returned a token since
       * JWTs were added; the frontend was simply throwing it away.
       */
      setAuthToken(result.token)
      onLoggedIn(result.user)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Signup failed')
    } finally {
      setSubmitting(false)
    }
  }

  /*
   * Live feedback on the password rather than waiting for a failed submit.
   * Telling someone their password is too short after they filled in six other
   * fields is a needlessly annoying way to find out.
   */
  const passwordTooShort = password.length > 0 && password.length < MIN_PASSWORD_LENGTH
  const passwordsMismatch = confirm.length > 0 && password !== confirm

  return (
    <div className="auth-card">
      <h2>Create your account</h2>
      <p className="auth-sub">
        You can add your interests, education and GitHub afterwards.
      </p>

      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="signup-name">Full name</label>
          <input
            id="signup-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoComplete="name"
            required
          />
        </div>

        <div className="field">
          <label htmlFor="signup-username">Username</label>
          <input
            id="signup-username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="how others will see you"
            autoComplete="username"
            required
          />
        </div>

        <div className="field">
          <label htmlFor="signup-email">Email</label>
          <input
            id="signup-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </div>

        <div className="field">
          <label htmlFor="signup-phone">
            Phone <span className="optional">optional</span>
          </label>
          <input
            id="signup-phone"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            autoComplete="tel"
          />
        </div>

        <div className="field">
          <label htmlFor="signup-password">Password</label>
          <PasswordInput
            id="signup-password"
            value={password}
            onChange={setPassword}
            placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
            autoComplete="new-password"
            required
            visible={showPassword}
            onToggleVisible={() => setShowPassword((v) => !v)}
          />
          {passwordTooShort && (
            <small className="field-error">
              {MIN_PASSWORD_LENGTH - password.length} more characters needed
            </small>
          )}
        </div>

        <div className="field">
          <label htmlFor="signup-confirm">Confirm password</label>
          <PasswordInput
            id="signup-confirm"
            value={confirm}
            onChange={setConfirm}
            placeholder="Type it again"
            autoComplete="new-password"
            required
            visible={showPassword}
            onToggleVisible={() => setShowPassword((v) => !v)}
          />
          {passwordsMismatch && (
            <small className="field-error">Passwords do not match</small>
          )}
        </div>

        <button type="submit" className="btn btn-block" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Create account'}
        </button>
      </form>

      <div className="auth-links">
        <span>
          Already have an account?{' '}
          <button className="link" onClick={() => onNavigate('login')}>
            Log in
          </button>
        </span>
      </div>
    </div>
  )
}

/* -------------------------------------------------------- ForgotPassword */

/*
 * The two-step reset flow.
 *
 * Step 1 asks for an email and the server creates a single-use token.
 * Step 2 exchanges that token for a new password.
 *
 * The old version did it in one step - email plus new password - which meant
 * anyone who knew your email address could take your account. Splitting it in
 * two is the whole point: the token proves you can read the inbox.
 *
 * There is no mail server in this project, so the backend logs the token to its
 * console instead. The UI says so plainly rather than pretending an email went out.
 */
export function ForgotPassword({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [step, setStep] = useState<'request' | 'redeem'>('request')

  const [email, setEmail] = useState('')
  const [token, setToken] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleRequest(event: React.FormEvent) {
    event.preventDefault()
    setError('')
    setSubmitting(true)

    try {
      await auth.requestPasswordReset(email)
      // Always advance, even for an unknown email. The server deliberately
      // does not tell us whether the account exists, and neither do we.
      setStep('redeem')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRedeem(event: React.FormEvent) {
    event.preventDefault()
    setError('')

    if (password !== confirm) {
      setError('The two passwords do not match.')
      return
    }

    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`)
      return
    }

    setSubmitting(true)

    try {
      await auth.resetPassword(token.trim(), password)
      setSuccess('Password updated. You can log in with it now.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not reset your password')
    } finally {
      setSubmitting(false)
    }
  }

  if (success) {
    return (
      <div className="auth-card">
        <h2>All set</h2>
        <Message kind="success">{success}</Message>
        <button className="btn btn-block" onClick={() => onNavigate('login')}>
          Go to login
        </button>
      </div>
    )
  }

  return (
    <div className="auth-card">
      <h2>Reset your password</h2>

      <ol className="steps">
        <li className={step === 'request' ? 'current' : 'done'}>1. Request a token</li>
        <li className={step === 'redeem' ? 'current' : ''}>2. Set a new password</li>
      </ol>

      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      {step === 'request' ? (
        <form onSubmit={handleRequest}>
          <div className="field">
            <label htmlFor="reset-email">Email</label>
            <input
              id="reset-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="The email on your account"
              autoComplete="email"
              required
            />
          </div>

          <button type="submit" className="btn btn-block" disabled={submitting}>
            {submitting ? 'Sending...' : 'Send reset token'}
          </button>

          <button
            type="button"
            className="link auth-back"
            onClick={() => onNavigate('login')}
          >
            Back to login
          </button>
        </form>
      ) : (
        <form onSubmit={handleRedeem}>
          <Message kind="info">
            If that email has an account, a token has been created. This demo has
            no mail server, so it is printed in the backend console — copy it from
            there.
          </Message>

          <div className="field">
            <label htmlFor="reset-token">Reset token</label>
            <input
              id="reset-token"
              type="text"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="3f2b8c10-...."
              required
            />
            <small className="field-hint">Valid for 30 minutes, and usable once.</small>
          </div>

          <div className="field">
            <label htmlFor="reset-password">New password</label>
            <PasswordInput
              id="reset-password"
              value={password}
              onChange={setPassword}
              placeholder={`At least ${MIN_PASSWORD_LENGTH} characters`}
              autoComplete="new-password"
              required
              visible={showPassword}
              onToggleVisible={() => setShowPassword((v) => !v)}
            />
          </div>

          <div className="field">
            <label htmlFor="reset-confirm">Confirm new password</label>
            <PasswordInput
              id="reset-confirm"
              value={confirm}
              onChange={setConfirm}
              placeholder="Type it again"
              autoComplete="new-password"
              required
              visible={showPassword}
              onToggleVisible={() => setShowPassword((v) => !v)}
            />
          </div>

          <button type="submit" className="btn btn-block" disabled={submitting}>
            {submitting ? 'Updating...' : 'Set new password'}
          </button>

          <button type="button" className="link auth-back" onClick={() => setStep('request')}>
            Request a different token
          </button>
        </form>
      )}
    </div>
  )
}
