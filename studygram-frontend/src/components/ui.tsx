import { avatarHue, initials } from '../utils/format'

/*
 * Small building blocks used across the app.
 *
 * Keeping them in one place means a spinner looks the same everywhere, and
 * changing how errors are presented is a one-file edit rather than a hunt.
 */

/* ---------------------------------------------------------------- Avatar */

/*
 * A coloured circle with the user's initials.
 *
 * Real avatars need uploads, storage and moderation. Initials on a colour
 * derived from the name give every user a distinct, stable image for free.
 */
export function Avatar({
  name,
  size = 40,
}: {
  name: string | null | undefined
  size?: number
}) {
  const hue = avatarHue(name)

  return (
    <div
      className="avatar"
      style={{
        width: size,
        height: size,
        fontSize: size * 0.38,
        // hsl makes it easy to vary only the hue while keeping saturation and
        // lightness fixed, so every avatar has the same visual weight.
        background: `hsl(${hue} 65% 92%)`,
        color: `hsl(${hue} 55% 30%)`,
      }}
      aria-hidden="true"
    >
      {initials(name)}
    </div>
  )
}

/* --------------------------------------------------------------- Message */

/*
 * An inline success or error message.
 *
 * role="alert" tells screen readers to announce it as soon as it appears -
 * otherwise a blind user submitting a form gets no feedback at all.
 */
export function Message({
  kind,
  children,
  onDismiss,
}: {
  kind: 'error' | 'success' | 'info'
  children: React.ReactNode
  onDismiss?: () => void
}) {
  if (!children) return null

  return (
    <div className={`message message-${kind}`} role="alert">
      <span>{children}</span>
      {onDismiss && (
        <button className="message-close" onClick={onDismiss} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  )
}

/* --------------------------------------------------------------- Spinner */

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="spinner-wrap">
      <div className="spinner" aria-hidden="true" />
      <span className="spinner-label">{label}</span>
    </div>
  )
}

/* ---------------------------------------------------------- SkeletonPost */

/*
 * A grey placeholder shaped like a post, shown while the feed loads.
 *
 * A skeleton beats a spinner here: it shows how much content is coming and
 * roughly where it will sit, so the page does not lurch when data arrives.
 */
export function SkeletonPost() {
  return (
    <div className="post-card skeleton-card" aria-hidden="true">
      <div className="post-head">
        <div className="skeleton skeleton-avatar" />
        <div style={{ flex: 1 }}>
          <div className="skeleton skeleton-line" style={{ width: '30%' }} />
          <div className="skeleton skeleton-line" style={{ width: '18%', height: 10 }} />
        </div>
      </div>
      <div className="skeleton skeleton-line" style={{ width: '95%' }} />
      <div className="skeleton skeleton-line" style={{ width: '80%' }} />
      <div className="skeleton skeleton-line" style={{ width: '55%' }} />
    </div>
  )
}

/* ------------------------------------------------------------ EmptyState */

/*
 * What to show when there is nothing to show.
 *
 * An empty area leaves the user wondering whether the app is broken or still
 * loading. An empty state says which, and offers the next step.
 */
export function EmptyState({
  icon,
  title,
  children,
  action,
}: {
  icon: string
  title: string
  children?: React.ReactNode
  action?: React.ReactNode
}) {
  return (
    <div className="empty-state">
      <div className="empty-icon" aria-hidden="true">
        {icon}
      </div>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}

/* ------------------------------------------------------------ TopicChips */

/* The little pills showing which topics a post is tagged with. */
export function TopicChips({
  topics,
  onSelect,
}: {
  topics: string[]
  onSelect?: (topic: string) => void
}) {
  if (topics.length === 0) return null

  return (
    <div className="chips">
      {topics.map((topic) =>
        onSelect ? (
          <button key={topic} className="chip chip-button" onClick={() => onSelect(topic)}>
            {topic}
          </button>
        ) : (
          <span key={topic} className="chip">
            {topic}
          </span>
        ),
      )}
    </div>
  )
}

/* ---------------------------------------------------------- PasswordInput */

/*
 * A password field with a show/hide toggle.
 *
 * Extracted because the app has six of them and each one previously carried its
 * own `showX` state variable in App.tsx. Now the state lives with the input
 * that owns it.
 */
export function PasswordInput({
  id,
  label,
  value,
  onChange,
  placeholder,
  required,
  autoComplete,
  visible,
  onToggleVisible,
}: {
  /*
   * REQUIRED, and the reason this prop exists.
   *
   * Callers render <label htmlFor="login-password"> beside this component, but
   * the input inside had no id - so the label pointed at nothing. Visually it
   * looked correct; to a screen reader the field had NO NAME at all, announced
   * only as "protected edit text".
   *
   * On the password reset screen that meant two unnamed password boxes with no
   * way to tell "new password" from "confirm password". A label that is not
   * programmatically associated is decoration.
   */
  id: string
  /* Fallback name, in case a caller ever renders this without a visible label. */
  label?: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  required?: boolean
  autoComplete?: string
  visible: boolean
  onToggleVisible: () => void
}) {
  return (
    <div className="password-input">
      <input
        id={id}
        aria-label={label}
        type={visible ? 'text' : 'password'}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        autoComplete={autoComplete}
      />
      <button
        type="button"
        className="show-password"
        onClick={onToggleVisible}
        aria-label={visible ? 'Hide password' : 'Show password'}
      >
        {visible ? 'Hide' : 'Show'}
      </button>
    </div>
  )
}
