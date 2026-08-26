/*
 * Shared types describing exactly what the backend sends back.
 *
 * The old code typed everything as `any`, which meant TypeScript could not
 * catch a single typo: `post.helpfullCount` compiled happily and rendered
 * "undefined" at runtime. Describing the API shape once here means the compiler
 * checks every use of it.
 *
 * Each interface mirrors a DTO class in the backend:
 *   User    <- dto/UserProfileResponse.java
 *   Post    <- dto/PostResponse.java
 *   Comment <- dto/CommentResponse.java
 */

/*
 * A user profile.
 *
 * Most fields are optional because the server omits anything the user chose to
 * hide - `email` genuinely is not in the response for someone with hideEmail
 * set. `?` forces you to handle that instead of printing "undefined".
 */
export interface User {
  id: number
  username: string

  name?: string
  email?: string
  phoneNumber?: string
  education?: string
  interests?: string
  careerGoal?: string
  githubUsername?: string

  hideName: boolean
  hideEmail: boolean
  hidePhone: boolean
  hideEducation: boolean
  hideInterests: boolean
  hideCareerGoal: boolean
  hideGithub: boolean

  ownProfile: boolean
}

/*
 * A post in the feed.
 *
 * authorId and authorUsername are null on anonymous posts - the server does not
 * send them at all, so there is nothing to accidentally leak in the UI.
 * Use `ownPost` to decide whether to show a Delete button; it is a yes/no answer
 * about the current viewer and identifies nobody.
 */
export interface Post {
  id: number
  content: string
  authorId: number | null
  authorName: string
  authorUsername: string | null
  ownPost: boolean
  anonymous: boolean
  createdAt: string
  helpfulCount: number
  commentCount: number
  topics: string[]
  helpfulUsers: string[]
}

/*
 * A comment on a post.
 * `canDelete` is computed by the server (you wrote it, or you own the post).
 */
export interface Comment {
  id: number
  content: string
  authorId: number | null
  authorName: string
  authorUsername: string | null
  canDelete: boolean
  anonymous: boolean
  createdAt: string
}

/* A topic/community, seeded by the backend. */
export interface Community {
  id: number
  name: string
  displayName: string
  description: string
  category: string
}

/*
 * What signup and login return: a token plus the profile it belongs to.
 *
 * Mirrors dto/AuthResponse.java. The token is the credential every later
 * request carries; the user comes along so the app can render a name and
 * avatar without a second round trip.
 */
export interface AuthResult {
  token: string
  user: User
}

/*
 * The state of the "Take a break" feature for one user.
 *
 * Mirrors dto/BreakStatusResponse.java. The server owns these rules entirely -
 * the browser only renders whichever of the three states it is told about.
 */
export interface BreakStatus {
  state: 'AVAILABLE' | 'ACTIVE' | 'COOLDOWN'
  secondsRemaining: number
  secondsUntilAvailable: number
  canExtend: boolean
  endsAt: string | null
  breaksToday: number
  breakMinutes: number
  cooldownMinutes: number
}

/* One turn in the AI assistant conversation. */
export interface AiMessage {
  role: 'user' | 'ai'
  content: string
}

export type AiMode = 'chat' | 'explain' | 'practice' | 'summarize'

/* Which screen the app is showing. */
export type Page =
  | 'home'
  | 'login'
  | 'signup'
  | 'forgot-password'
  | 'feed'
  | 'explore'
  | 'ai'
  | 'profile'
