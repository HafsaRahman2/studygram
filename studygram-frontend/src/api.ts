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

import type { BreakStatus, Comment, Community, Post, User } from './types'

/*
 * Where the backend lives.
 *
 * Vite exposes environment variables that start with VITE_ to the browser at
 * build time. Setting VITE_API_URL lets this same code point at a deployed
 * server without a code change; localhost is the fallback for development.
 */
const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

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

  let response: Response

  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
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
 * `viewerId` appears throughout the API below.
 *
 * It tells the server who is asking, so it can decide what that person is
 * allowed to see: whose posts show a Delete button, whether a hidden email is
 * included, and so on. The server never trusts it for anything destructive -
 * every write re-checks ownership independently.
 */
function viewerQuery(viewerId?: number, separator = '?'): string {
  return viewerId ? `${separator}viewerId=${viewerId}` : ''
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
    return request<User>('/api/signup', { method: 'POST', body: data })
  },

  login(emailOrPhone: string, password: string) {
    return request<User>('/api/login', {
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

  changePassword(userId: number, currentPassword: string, newPassword: string) {
    return request<string>('/api/change-password', {
      method: 'POST',
      body: { userId, currentPassword, newPassword },
    })
  },
}

/* --------------------------------------------------------------- profile */

export const profile = {
  get(username: string, viewerId?: number) {
    return request<User>(`/api/profile/${username}${viewerQuery(viewerId)}`)
  },

  update(userId: number, data: Partial<User>) {
    return request<User>(`/api/profile/${userId}`, { method: 'PUT', body: data })
  },
}

/* ----------------------------------------------------------------- posts */

export const posts = {
  /* The main feed: everything, newest first. */
  feed(viewerId?: number) {
    return request<Post[]>(`/api/posts${viewerQuery(viewerId)}`)
  },

  /* The "For You" feed: only topics matching the user's interests. */
  personalizedFeed(userId: number) {
    return request<Post[]>(`/api/posts/feed/${userId}`)
  },

  byUser(userId: number, viewerId?: number) {
    return request<Post[]>(`/api/posts/user/${userId}${viewerQuery(viewerId)}`)
  },

  create(data: {
    userId: number
    content: string
    topics: string[]
    anonymous: boolean
  }) {
    return request<Post>('/api/posts', { method: 'POST', body: data })
  },

  toggleHelpful(postId: number, userId: number) {
    return request<{ marked: boolean; helpfulCount: number; helpfulUsers: string[] }>(
      `/api/posts/${postId}/helpful?userId=${userId}`,
      { method: 'POST' },
    )
  },

  remove(postId: number, userId: number) {
    return request<string>(`/api/posts/${postId}?userId=${userId}`, {
      method: 'DELETE',
    })
  },
}

/* -------------------------------------------------------------- comments */

export const comments = {
  forPost(postId: number, viewerId?: number) {
    return request<Comment[]>(`/api/comments/post/${postId}${viewerQuery(viewerId)}`)
  },

  add(data: { userId: number; postId: number; content: string; anonymous: boolean }) {
    return request<Comment>('/api/comments', { method: 'POST', body: data })
  },

  remove(commentId: number, userId: number) {
    return request<string>(`/api/comments/${commentId}?userId=${userId}`, {
      method: 'DELETE',
    })
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

/* ---------------------------------------------------------------- breaks */

/*
 * Every one of these returns the full BreakStatus, so callers can replace their
 * state with the result rather than guessing what changed and refetching.
 */
export const breaks = {
  status(userId: number) {
    return request<BreakStatus>(`/api/breaks/status/${userId}`)
  },

  start(userId: number) {
    return request<BreakStatus>(`/api/breaks/start?userId=${userId}`, { method: 'POST' })
  },

  /* The single allowed +5 minutes. */
  extend(userId: number) {
    return request<BreakStatus>(`/api/breaks/extend?userId=${userId}`, { method: 'POST' })
  },

  /* Finish early - starts the cooldown early too. */
  end(userId: number) {
    return request<BreakStatus>(`/api/breaks/end?userId=${userId}`, { method: 'POST' })
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
