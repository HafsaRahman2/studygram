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

  /* 'QUESTION' or 'SHARE' - decides how the card is rendered. */
  postType: PostType
  /* Whether the asker has marked a question answered. */
  resolved: boolean
  /* True once the AI has replied, so the UI never offers it twice. */
  hasAiAnswer: boolean
}

/*
 * The two kinds of post.
 *
 * A QUESTION has answers and can be resolved; a SHARE is someone posting
 * something they learned. They live in the same feed but read differently -
 * "3 answers" and "3 comments" mean different things to a reader.
 */
export type PostType = 'QUESTION' | 'SHARE'

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
  /* True when the AI wrote this rather than a person. Always shown. */
  aiGenerated: boolean
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
 * A buddy request, seen from one side.
 *
 * Mirrors dto/StudyBuddyResponse.java. `user` is always THE OTHER PERSON —
 * the server resolves that, because you already know who you are.
 */
export interface BuddyRequest {
  requestId: number
  user: User
  status: string
  /* INCOMING means they asked you, so you can accept. OUTGOING means you asked. */
  direction: 'INCOMING' | 'OUTGOING'
  createdAt: string
}

/*
 * Where you stand with somebody. Decides which button their card shows.
 *
 * Computed on the server (StudyBuddyService.describeRelationship) rather than
 * derived in the browser from three separate lists — re-implementing a rule
 * client-side is what produced this project's earlier bugs.
 */
export type BuddyRelationship =
  | 'SELF'
  | 'NONE'
  | 'REQUEST_SENT'
  | 'REQUEST_RECEIVED'
  | 'BUDDIES'
  | 'REJECTED'

/* One person in search results or suggestions, plus your relationship to them. */
export interface UserSearchResult {
  user: User
  relationship: BuddyRelationship
  /* Present when a request already exists, so it can be accepted from here. */
  requestId: number | null
  /* Topics you both listed — the reason this person is worth connecting with. */
  sharedInterests: string[]
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
  | 'buddies'
  | 'ai'
  | 'profile'
