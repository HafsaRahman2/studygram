/*
 * api.ts - Every call to the backend goes through here.
 *
 * WHY CENTRALISE THIS
 *
 * The first version of this app wrote `fetch('http://localhost:8080/api/...')`
 * inline at fifteen different call sites. That meant:
 *   - deploying anywhere but localhost required editing fifteen lines
 *   - each call re-implemented its own error handling, slightly differently
 *   - nothing was typed, so a misspelled field failed silently at runtime
 *
 * One module fixes all three. The URL is configured once, errors are handled
 * once, and every function returns a properly typed result.
 */

import type {
  AuthResult,
  BreakStatus,
  BuddyRequest,
  Comment,
  Community,
  Post,
  User,
  UserSearchResult,
} from './types'

/*
 * Where the backend lives.
 *
 * Vite exposes environment variables that start with VITE_ to the browser at
 * build time. Setting VITE_API_URL lets this same code point at a deployed
 * server without a code change; localhost is the fallback for development.
 */
const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/* ------------------------------------------------------------------ token */

/*
 * THE AUTH TOKEN
 *
 * Every protected endpoint requires an "Authorization: Bearer <token>" header.
 * Rather than making every caller remember to attach it, the token is held here
 * and `request()` adds it to everything automatically.
 *
 * WHERE IT IS KEPT, AND THE HONEST TRADE-OFF
 *
 * localStorage, which means any JavaScript running on this page can read it.
 * If an attacker ever got a script onto the page (an XSS flaw), they could
 * steal the token and act as the user until it expires.
 *
 * The more secure option is an httpOnly cookie, which JavaScript cannot read at
 * all — but that reintroduces CSRF (because browsers send cookies
 * automatically) and needs matching server work. This project uses localStorage
 * for simplicity and says so in the README rather than pretending the question
 * does not exist.
 *
 * Mitigations that ARE in place: the token expires, React escapes rendered
 * content by default, and no user content is ever inserted with
 * dangerouslySetInnerHTML.
 */
const TOKEN_KEY = 'studygram.token'

/*
 * Cached in a module variable so the common path does not touch localStorage on
 * every single request; localStorage is synchronous and blocks the main thread.
 */
let authToken: string | null = readStoredToken()

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    // Private browsing modes can make localStorage throw rather than return null.
    return null
  }
}

export function setAuthToken(token: string | null) {
  authToken = token

  try {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  } catch {
    // Session still works for this tab; it just will not survive a refresh.
  }
}

export function getAuthToken(): string | null {
  return authToken
}

/*
 * Called when the server rejects our token — expired, or the signing secret
 * changed because the backend restarted with a new one.
 *
 * A callback rather than importing the auth hook, because api.ts must not
 * depend on React. App registers a handler that clears the session and sends
 * the user back to the login screen.
 */
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

/*
 * ApiError - an error that carries the message the server actually sent.
 *
 * A plain `throw new Error('Request failed')` would throw away the useful part.
 * The backend replies with things like "Username already taken", and that is
 * exactly what the user needs to read.
 */
export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/*
 * The single fetch wrapper every call below uses.
 *
 * Responsibilities:
 *   1. Prefix the base URL
 *   2. Set the JSON content type when there is a body
 *   3. Turn a non-2xx response into a thrown ApiError carrying the server's message
 *   4. Parse the response as JSON, or as text when it is not JSON
 *
 * The <T> is a generic: the caller says what shape it expects back, and
 * TypeScript checks the rest of the code against it.
 */
async function request<T>(
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const { method = 'GET', body } = options

  /*
   * Build the headers: JSON content type when there is a body, and the token
   * whenever we have one. Attaching it in one place means no endpoint can be
   * accidentally called unauthenticated.
   */
  const headers: Record<string, string> = {}

  if (body) {
    headers['Content-Type'] = 'application/json'
  }

  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`
  }

  let response: Response

  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    // fetch only rejects when the request never reached the server at all -
    // wrong port, backend not running, no network. A 404 or 500 is a
    // successful round trip and lands below, not here.
    throw new ApiError(
      'Cannot reach the server. Is the backend running on ' + BASE_URL + '?',
      0,
    )
  }

  // The backend sends error details as plain text, so read the body as text
  // first and only then try to interpret it as JSON.
  const text = await response.text()

  if (!response.ok) {
    /*
     * 401 means the server did not accept our token — it expired, or it was
     * issued by a backend that has since restarted with a different secret.
     *
     * Handling it centrally means the app logs out cleanly instead of every
     * screen independently showing "Authentication required" while looking
     * logged in.
     *
     * Login and signup are excluded: a 401 there means "wrong password", which
     * the form should display, not a dead session.
     */
    if (
      response.status === 401 &&
      !path.startsWith('/api/login') &&
      !path.startsWith('/api/signup')
    ) {
      setAuthToken(null)
      onUnauthorized?.()
    }

    throw new ApiError(text || `Request failed (${response.status})`, response.status)
  }

  if (!text) {
    return undefined as T
  }

  try {
    return JSON.parse(text) as T
  } catch {
    // Some endpoints reply with a plain sentence ("Post deleted successfully")
    return text as T
  }
}

/*
 * NOTE ON WHAT IS NO LONGER HERE
 *
 * These functions used to pass `userId` and `viewerId` in query strings, so the
 * server could tell who was asking. That was the whole vulnerability: the
 * client declared its own identity, and the server believed it.
 *
 * Those parameters are gone. Identity now travels in the signed token attached
 * to every request above, and the server derives the caller from it. Ids that
 * remain in these URLs point at the RESOURCE being acted on (which post, whose
 * profile to view), never at who is doing the acting.
 */

/* ---------------------------------------------------------------- health */

/*
 * A cheap public endpoint, used to check the server is awake.
 *
 * Free hosting tiers stop the container after a period with no traffic, so the
 * first request after a quiet spell has to start a JVM before it can answer -
 * often 30 to 60 seconds. Without something on screen explaining that, a
 * visitor sees a blank page and leaves before it ever loads.
 */
export function ping() {
  return request<string>('/api/hello')
}

/* ------------------------------------------------------------------ auth */

export const auth = {
  signup(data: {
    name: string
    username: string
    email: string
    phoneNumber: string | null
    password: string
  }) {
    return request<AuthResult>('/api/signup', { method: 'POST', body: data })
  },

  login(emailOrPhone: string, password: string) {
    return request<AuthResult>('/api/login', {
      method: 'POST',
      body: { emailOrPhone, password },
    })
  },

  /* Step 1 of password reset: ask for a token. Always "succeeds". */
  requestPasswordReset(email: string) {
    return request<string>('/api/forgot-password', {
      method: 'POST',
      body: { email },
    })
  },

  /* Step 2: redeem the token and set the new password. */
  resetPassword(token: string, newPassword: string) {
    return request<string>('/api/reset-password', {
      method: 'POST',
      body: { token, newPassword },
    })
  },

  /*
   * No userId: whose password changes is decided by the token, not by us.
   * The current password is still required as proof it is really you.
   */
  changePassword(currentPassword: string, newPassword: string) {
    return request<string>('/api/change-password', {
      method: 'POST',
      body: { currentPassword, newPassword },
    })
  },
}

/* --------------------------------------------------------------- profile */

export const profile = {
  get(username: string) {
    return request<User>(`/api/profile/${username}`)
  },

  /*
   * No id in the URL. This edits YOUR profile, and there is no way to name
   * another one - which is what stops the old
   * PUT /api/profile/{someoneElsesId} from working.
   */
  update(data: Partial<User>) {
    return request<User>('/api/profile', { method: 'PUT', body: data })
  },
}

/* ----------------------------------------------------------------- posts */

export const posts = {
  /* The main feed: everything, newest first. */
  feed() {
    return request<Post[]>('/api/posts')
  },

  /* The "For You" feed: only topics matching your interests. Always your own. */
  personalizedFeed() {
    return request<Post[]>('/api/posts/feed')
  },

  /* userId here is WHOSE posts to show - a lookup, not an identity claim. */
  byUser(userId: number) {
    return request<Post[]>(`/api/posts/user/${userId}`)
  },

  create(data: { content: string; topics: string[]; anonymous: boolean }) {
    return request<Post>('/api/posts', { method: 'POST', body: data })
  },

  toggleHelpful(postId: number) {
    return request<{ marked: boolean; helpfulCount: number; helpfulUsers: string[] }>(
      `/api/posts/${postId}/helpful`,
      { method: 'POST' },
    )
  },

  remove(postId: number) {
    return request<string>(`/api/posts/${postId}`, { method: 'DELETE' })
  },
}

/* -------------------------------------------------------------- comments */

export const comments = {
  forPost(postId: number) {
    return request<Comment[]>(`/api/comments/post/${postId}`)
  },

  add(data: { postId: number; content: string; anonymous: boolean }) {
    return request<Comment>('/api/comments', { method: 'POST', body: data })
  },

  remove(commentId: number) {
    return request<string>(`/api/comments/${commentId}`, { method: 'DELETE' })
  },
}

/* ----------------------------------------------------------- communities */

export const communities = {
  /* The canonical topic list, seeded by the backend. */
  all() {
    return request<Community[]>('/api/communities')
  },

  postsIn(name: string) {
    return request<Post[]>(`/api/communities/${name}/posts`)
  },
}

/* --------------------------------------------------------------- buddies */

/*
 * Study buddies.
 *
 * Note what is NOT here: any way to ask about somebody else's buddies or
 * requests. Every one of these endpoints acts on the caller's own connections,
 * decided by the token.
 */
export const buddies = {
  /* Your accepted buddies. */
  list() {
    return request<User[]>('/api/buddies')
  },

  /* Requests waiting for YOU to accept or reject. */
  pending() {
    return request<BuddyRequest[]>('/api/buddies/pending')
  },

  /* Requests you have sent that are still unanswered. */
  sent() {
    return request<BuddyRequest[]>('/api/buddies/sent')
  },

  count() {
    return request<{ count: number }>('/api/buddies/count')
  },

  /*
   * Find people by username or display name.
   * Queries under two characters return an empty list from the server.
   */
  search(query: string) {
    return request<UserSearchResult[]>(
      `/api/buddies/search?q=${encodeURIComponent(query)}`,
    )
  },

  /* People who share your interests, most overlap first. */
  suggestions() {
    return request<UserSearchResult[]>('/api/buddies/suggestions')
  },

  sendRequest(buddyId: number) {
    return request<{ message: string; requestId: number }>('/api/buddies/request', {
      method: 'POST',
      body: { buddyId },
    })
  },

  accept(requestId: number) {
    return request<string>(`/api/buddies/accept/${requestId}`, { method: 'POST' })
  },

  reject(requestId: number) {
    return request<string>(`/api/buddies/reject/${requestId}`, { method: 'POST' })
  },

  /*
   * Removes the connection in either state — an accepted buddy, or a pending
   * request you want to withdraw. The server looks up the connection between
   * the two of you regardless of who sent it.
   */
  remove(buddyId: number) {
    return request<string>(`/api/buddies?buddyId=${buddyId}`, { method: 'DELETE' })
  },
}

/* ---------------------------------------------------------------- breaks */

/*
 * Every one of these returns the full BreakStatus, so callers can replace their
 * state with the result rather than guessing what changed and refetching.
 */
export const breaks = {
  status() {
    return request<BreakStatus>('/api/breaks/status')
  },

  start() {
    return request<BreakStatus>('/api/breaks/start', { method: 'POST' })
  },

  /* The single allowed +5 minutes. */
  extend() {
    return request<BreakStatus>('/api/breaks/extend', { method: 'POST' })
  },

  /* Finish early - starts the cooldown early too. */
  end() {
    return request<BreakStatus>('/api/breaks/end', { method: 'POST' })
  },
}

/* -------------------------------------------------------------------- ai */

export const ai = {
  chat(message: string) {
    return request<{ response: string }>('/api/ai/chat', {
      method: 'POST',
      body: { message },
    }).then((r) => r.response)
  },

  explain(topic: string) {
    return request<{ explanation: string }>('/api/ai/explain', {
      method: 'POST',
      body: { topic },
    }).then((r) => r.explanation)
  },

  practice(topic: string, count = 5) {
    return request<{ questions: string }>('/api/ai/practice', {
      method: 'POST',
      body: { topic, count },
    }).then((r) => r.questions)
  },

  summarize(text: string) {
    return request<{ summary: string }>('/api/ai/summarize', {
      method: 'POST',
      body: { text },
    }).then((r) => r.summary)
  },
}

/* ---------------------------------------------------------------- github */

export const github = {
  profile(username: string) {
    return request<{
      username: string
      name: string | null
      bio: string | null
      avatarUrl: string
      profileUrl: string
      publicRepos: number
      followers: number
      following: number
    }>(`/api/github/${username}/profile`)
  },

  repos(username: string) {
    return request<
      Array<{
        name: string
        description: string | null
        language: string | null
        stars: number
        forks: number
        url: string
      }>
    >(`/api/github/${username}/repos`)
  },
}
