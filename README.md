# StudyGram

A social network for students, built around **what you're learning** rather than what you're doing.

Every post is tagged by topic so your feed only shows subjects you actually study, questions can be
asked anonymously, and an AI study assistant is built in — along with a *Take a break* timer that
makes you stop every so often.

**Stack:** React 19 + TypeScript (Vite) · Spring Boot 3 + Java · Spring Security + JWT · PostgreSQL · Groq API

![StudyGram landing page](docs/screenshots/landing.jpg)

---

## Contents

- [What it does](#what-it-does)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [Data model](#data-model)
- [Running it locally](#running-it-locally)
- [Deploying it](#deploying-it)
- [API reference](#api-reference)
- [Authentication](#authentication)
- [Testing](#testing)
- [Accessibility](#accessibility)
- [Engineering notes](#engineering-notes)
- [Known limitations](#known-limitations)

---

## What it does

| Feature | Description |
| --- | --- |
| **Ask or share** | Every post is a **question** or a **share**. Questions get answers, can be marked answered, and are visually distinct from someone sharing what they learned. |
| **Instant AI answers** | Ask a question and optionally get an AI answer immediately, while real people answer it properly. Always labelled as AI-generated. |
| **Topic-based feed** | Posts carry one to five topics. The *For you* feed returns only posts matching the interests on your profile. |
| **Anonymous posting** | Publish without your identity attached. The server strips the author entirely — see [Engineering notes](#anonymity-is-enforced-on-the-server). |
| **Helpful marks & comments** | A join table records who found what helpful, so a post can be marked once per person and show who did. |
| **Communities** | All 64 topics are browsable communities, seeded on startup, reachable from the feed's topic menu. |
| **AI study assistant** | Four modes — chat, explain a topic, generate practice questions, summarize notes — via the Groq API. |
| **Take a break** | A 5-minute timer with finite calming activities, then a one-hour cooldown enforced server-side. |
| **Privacy controls** | Email and phone are private by default and omitted from the API response entirely, not merely hidden in the UI. |
| **Token authentication** | Stateless JWT auth. Every endpoint derives the caller from a signed token rather than trusting an id in the URL. |
| **GitHub showcase** | Optionally pulls your public repositories onto your profile. |
| **Study buddies** | Send, accept and reject buddy requests. *(API complete; no UI yet.)* |

## Screenshots

| The feed | Study buddies |
| --- | --- |
| ![Feed](docs/screenshots/feed.jpg) | ![Study buddies](docs/screenshots/buddies.jpg) |

| AI study assistant | Take a break |
| --- | --- |
| ![Assistant](docs/screenshots/assistant.jpg) | ![Take a break](docs/screenshots/take-a-break.jpg) |

The interface follows your system light/dark preference.

---

## Architecture

Two independently runnable applications talking over HTTP:

```
┌──────────────────────────┐         ┌──────────────────────────┐        ┌────────────┐
│  React + TypeScript      │  JSON   │  Spring Boot             │  JPA   │            │
│  Vite dev server :5173   │ ──────▶ │  REST API :8080          │ ─────▶ │ PostgreSQL │
│                          │ ◀────── │                          │ ◀───── │            │
└──────────────────────────┘         └───────────┬──────────────┘        └────────────┘
                                                 │
                                    ┌────────────┴────────────┐
                                    ▼                         ▼
                              Groq API                  GitHub REST API
                           (study assistant)          (profile showcase)
```

They run on different ports, which browsers treat as different origins, so
`config/CorsConfig.java` declares which origins may call the API.

### Backend layering

The backend follows the standard Spring layering, one responsibility per layer:

```
HTTP request
    │
    ▼
JwtAuthenticationFilter ── verifies the token, establishes who the caller is
    │
    ▼
SecurityConfig ───── decides whether this URL may be reached at all
    │
    ▼
Controller ──── maps URLs to methods, converts JSON, returns status codes
    │
    ▼
Service ─────── business rules: ownership checks, validation, password hashing
    │
    ▼
Repository ──── database access (Spring Data generates the SQL)
    │
    ▼
Entity ──────── a class that maps to a table
```

**DTOs** sit alongside this. Nothing that leaves the API is an entity — every response is a
purpose-built class in `dto/`, which is what makes anonymity and the privacy switches enforceable
rather than cosmetic.

### Frontend structure

```
src/
├── api.ts                  every backend call, typed, one base URL
├── types.ts                interfaces mirroring the backend DTOs
├── App.tsx                 routing and auth state, nothing else
├── hooks/
│   ├── useAuth.ts          session, persisted to localStorage
│   ├── useBreak.ts         break state and countdown
│   └── useTopics.ts        the topic list, fetched once and cached
├── components/             one file per screen or reusable piece
└── utils/format.ts         timestamps, initials, avatar colours
```

---

## Data model

```
users ─────┬──< posts >──── post_topics
           │      │
           │      ├──< comments
           │      └──< helpfuls
           │
           ├──< study_buddies >── users     (self-referencing: user_id + buddy_id)
           ├──< break_sessions
           └──< password_reset_tokens

communities                                (standalone; matched to posts by topic name)
```

Three tables are worth calling out:

- **`post_topics`** — a post's topics get one row each rather than a comma-separated string in one
  column. This is what makes the personalized feed a plain indexed lookup instead of substring
  matching. See [Engineering notes](#normalizing-topics-fixed-the-personalized-feed).
- **`helpfuls`** — a join table of `(user, post)`. Being a row rather than a counter is what stops
  double-marking and lets the app list who found a post useful.
- **`password_reset_tokens`** — random, expiring, single-use.

There is no `sessions` table, deliberately: authentication is stateless, and a token carries
everything needed to identify its holder. See [Authentication](#authentication).

---

## Running it locally

### Prerequisites

- Java 17+
- Node 18+
- PostgreSQL running locally
- A free [Groq API key](https://console.groq.com/keys) *(optional — everything except the AI
  assistant works without one)*

### 1. Create the database

```bash
createdb studygram
```

Tables are created automatically on first run (`spring.jpa.hibernate.ddl-auto=update`), and the 64
communities are seeded by `CommunitySeeder`.

### 2. Configure the backend

```bash
cd studygram-backend
cp .env.example .env
```

Edit `.env` with your PostgreSQL role and, if you have one, your Groq key:

```bash
DB_URL=jdbc:postgresql://localhost:5432/studygram
DB_USERNAME=postgres     # on macOS + Homebrew this is usually your Mac username
DB_PASSWORD=
GROQ_API_KEY=            # optional
STUDYGRAM_JWT_SECRET=    # see below
```

Generate a signing secret for the auth tokens:

```bash
openssl rand -base64 32
```

Paste it in as `STUDYGRAM_JWT_SECRET`. Without one the app still starts, on a built-in development
secret, and logs a loud warning every time — anyone who knows that value can forge a token for any
account.

`.env` is gitignored. No credential is ever committed — `application.properties` reads everything
from the environment.

### 3. Start the backend

```bash
./run.sh          # loads .env, then starts Spring Boot on :8080
```

### 4. Start the frontend

```bash
cd studygram-frontend
npm install
npm run dev       # http://localhost:5173
```

### Running the tests

```bash
cd studygram-backend
./mvnw test
```

No setup needed — the suite runs against an in-memory database, so it works on a fresh clone with
PostgreSQL stopped.

### Resetting a password in development

There is no mail server, so the reset token is written to the **backend console** instead of being
emailed — the same pattern Django and Rails use in development. Request a reset in the UI, copy the
token from the terminal running the backend, and paste it into step 2.

---

## Deploying it

Three pieces, on three free tiers: a database, the API, and the static frontend.

**Order matters.** The frontend must be built against a backend URL that already exists, and the
backend must be told which frontend origin to accept. So: database → backend → frontend → update the
backend's CORS setting.

### 1. Database — Neon

Create a free Postgres project at [neon.tech](https://neon.tech) and copy the connection string. It
looks like:

```
postgresql://user:password@ep-xxx-123.us-east-2.aws.neon.tech/studygram?sslmode=require
```

Spring needs that split into three values, and the URL prefixed with `jdbc:`:

```bash
DB_URL=jdbc:postgresql://ep-xxx-123.us-east-2.aws.neon.tech/studygram?sslmode=require
DB_USERNAME=user
DB_PASSWORD=password
```

> **Why Neon and not the hosting platform's own database?** Render's free Postgres is deleted after
> 30 days. A link on a CV that works for a month and then breaks is worse than no link.

Tables are created on first boot by `ddl-auto=update`, and `CommunitySeeder` fills in the 64 topics.
Nothing to run by hand.

### 2. Backend — Render

New → **Web Service**, connect the GitHub repo, then:

| Setting | Value |
| --- | --- |
| Root directory | `studygram-backend` |
| Runtime | Docker *(it will find the `Dockerfile`)* |
| Instance type | Free |

Environment variables:

```bash
DB_URL=jdbc:postgresql://...        # from step 1
DB_USERNAME=...
DB_PASSWORD=...
STUDYGRAM_JWT_SECRET=...            # generate a NEW one: openssl rand -base64 32
GROQ_API_KEY=...                    # optional; AI assistant is disabled without it
CORS_ORIGINS=http://localhost:5173  # replaced in step 4
```

**Generate a fresh JWT secret for production.** Do not reuse the development one — anyone who has it
can forge a token for any account.

`PORT` is set by Render automatically, and `application.properties` reads it.

First build takes a few minutes. Check it with `curl https://your-service.onrender.com/api/hello`.

### 3. Frontend — Vercel

Import the same repo, then:

| Setting | Value |
| --- | --- |
| Root directory | `studygram-frontend` |
| Framework | Vite *(auto-detected)* |

One environment variable:

```bash
VITE_API_URL=https://your-service.onrender.com
```

> **Vite inlines environment variables at build time, not runtime.** Changing `VITE_API_URL` later
> means triggering a rebuild — editing it in the dashboard alone does nothing to the already-built
> files.

### 4. Close the loop

Take the Vercel URL, put it in Render's `CORS_ORIGINS`, and redeploy the backend:

```bash
CORS_ORIGINS=https://studygram.vercel.app
```

Without this the browser blocks every request, because the API only accepts origins it has been told
about. This is the step that is easy to forget, and the symptom — everything failing with no useful
error — looks far worse than the one-line cause.

### The cold start

The free tier stops the container after about 15 minutes without traffic, so the next visitor waits
30–60 seconds for a JVM to boot.

That is why the app pings `/api/hello` on load and shows a banner explaining the wait
(`useServerWakeup.ts`). The delay is unavoidable on a free tier; being told about it is the
difference between someone waiting and someone assuming the site is broken.

If it matters enough — a link on a CV probably qualifies — Render's cheapest paid instance removes
the sleep entirely.

---

## API reference

All endpoints are prefixed `/api`. Everything except signup, login and password reset requires an
`Authorization: Bearer <token>` header.

**No endpoint takes the caller's own id.** Ids that appear in these URLs identify the *resource*
being acted on — which post, whose profile to view — never who is doing the acting. That comes from
the token. See [Authentication](#authentication) for why that distinction is the whole point.

### Auth

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/signup` | Create an account |
| `POST` | `/login` | Log in |
| `POST` | `/forgot-password` | Request a reset token |
| `POST` | `/reset-password` | Redeem a token and set a new password |
| `POST` | `/change-password` | Change **your own** password while logged in |

Signup and login both return `{ token, user }`. Everything else needs that token.

### Profile

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/profile/{username}` | Fetch a profile, privacy rules applied |
| `PUT` | `/profile` | Update **your own** profile and privacy switches |

### Posts

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/posts` | Main feed, newest first |
| `GET` | `/posts/feed` | **Your** personalized feed, by your interests |
| `GET` | `/posts/user/{userId}` | One user's posts |
| `POST` | `/posts` | Create a post |
| `POST` | `/posts/{id}/helpful` | Toggle a helpful mark |
| `DELETE` | `/posts/{id}` | Delete your own post |

### Comments, communities, breaks, AI, GitHub

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/comments/post/{postId}` | Comments on a post |
| `POST` | `/comments` | Add a comment |
| `DELETE` | `/comments/{id}` | Delete a comment |
| `GET` | `/communities` | All topics, with categories |
| `GET` | `/communities/{name}/posts` | Posts in one community |
| `GET` | `/breaks/status` | **Your** break state: available, active or cooling down |
| `POST` | `/breaks/start\|extend\|end` | Control your break |
| `POST` | `/ai/chat\|explain\|practice\|summarize` | Study assistant |
| `GET` | `/github/{username}/profile\|repos` | Public GitHub data |

---

## Authentication

Requests are authenticated with a **JSON Web Token**, verified on every request. There are no
sessions and the server stores nothing about who is logged in.

### How a request is authenticated

```
1. POST /api/login  { emailOrPhone, password }
        │
        ▼  password checked against the BCrypt hash
2. Server signs a token:  header.payload.signature
        │                        │        │
        │                        │        └── HMAC-SHA256 of the first two parts,
        │                        │            using a secret only the server knows
        │                        └── { sub: "<userId>", username, iat, exp }
        ▼
3. Client stores it and sends it back on every request:
        Authorization: Bearer eyJhbGci...
        │
        ▼
4. JwtAuthenticationFilter recomputes the signature and checks the expiry.
   Valid   -> the caller's identity is established for this request
   Invalid -> the request continues unauthenticated, and SecurityConfig returns 401
```

The payload is **encoded, not encrypted** — paste a token into jwt.io and you can read the user id.
That is expected, which is why nothing secret goes inside one. What the signature guarantees is that
the contents were not *changed*: edit one character and the signature no longer matches, so the
token is refused. Forging a token for another user requires the signing secret.

### What this replaced, and why it mattered

Before this, the API identified callers by a number in the URL:

```
DELETE /api/posts/5?userId=1
```

The server dutifully checked that user 1 owned post 5 — but never checked that the person sending
the request *was* user 1. Changing the number was enough to act as somebody else. Every ownership
rule in the app rested on the client being honest about its own identity.

Concretely, these were all possible, and all now have a test proving they are not:

| Attack | Then | Now |
| --- | --- | --- |
| `DELETE /api/posts/{id}?userId=<owner>` | Deleted anyone's post | Refused — id comes from the token |
| `PUT /api/profile/{someoneElsesId}` | Edited anyone's profile and privacy switches | Endpoint takes no id at all |
| `POST /api/change-password {userId: 2}` | Changed another user's password | Field removed from the DTO |
| `GET /api/posts/feed/{anyUserId}` | Read anyone's personalized feed, exposing private interests | Always your own |
| `GET /api/profile/x?viewerId=<them>` | Revealed fields that user had marked hidden | Viewer comes from the token |

### Design decisions

**The public list is a whitelist.** `SecurityConfig` lists the endpoints that do *not* need a token
and protects everything else with `anyRequest().authenticated()`. Written the other way round, every
endpoint added later would be public until someone remembered to protect it. Defaulting to closed
means forgetting is safe.

**CSRF protection is off, and that is correct here.** CSRF attacks work by making a browser send a
request it *automatically* attaches credentials to — which is what cookies do. This API uses no
cookies; the token goes in a header that JavaScript must add deliberately, and another site's
JavaScript cannot read our token in order to add it. If the token ever moves into a cookie, CSRF
protection has to come back on.

**Ownership checks still live in the services.** The token establishes *who you are*; it says
nothing about *what is yours*. `PostService.deletePost` still verifies ownership — that check simply
means something now that the identity behind it is trustworthy.

---

## Testing

```bash
cd studygram-backend && ./mvnw test
```

40 tests, running against an in-memory H2 database so the suite needs no setup and no running
PostgreSQL.

| Suite | Covers |
| --- | --- |
| `JwtServiceTest` | Token round-trip, tampered payloads, tokens signed with the wrong secret, expiry, malformed input, secrets that are too short |
| `AuthorizationIntegrationTest` | The full application over real HTTP: login, 401s, and one test per attack in the table above |
| `StudyBuddyIntegrationTest` | The password-hash leak, the request lifecycle, and the search and matching rules |
| `StudygramBackendApplicationTests` | The context loads — catches broken beans, missing properties and invalid queries |

The negative tests are the point. That a valid token works is table stakes; what protects accounts
is that a **tampered** one does not, and that Bob cannot delete Alice's post however he asks.

---

## Accessibility

Built in rather than bolted on, because retrofitting it is far harder.

| | |
| --- | --- |
| **Skip link** | First thing Tab reaches; jumps straight past the navigation to the content |
| **Focus trap** | The break overlay keeps keyboard focus inside it, closes on Escape, and returns focus to the button that opened it |
| **Live regions** | Posting, and answers arriving, are announced — events a sighted user learns from things appearing on screen |
| **Contrast** | Muted text was around 4.0:1 and 2.6:1, below the 4.5:1 WCAG AA asks for. Both palettes corrected. |
| **`lang="en"`** | Screen readers pick a pronunciation engine from it; without it an English page can be read as nonsense |
| **Focus rings** | `:focus-visible`, so keyboard users get a ring and mouse users do not |
| **Semantics** | Real `<main>`, `<nav>`, `<article>`, `<header>`; `role="tab"`/`aria-selected` on tabs; `aria-expanded` on menus |
| **Decorative emoji** | `aria-hidden`, so a screen reader does not read "graduation cap" before every AI answer |
| **Labels** | Every input is programmatically associated with its label, verified by a script rather than by eye |
| **One `h1` per page** | Visually hidden where the design has no visible title, so heading navigation still works |

Auditing found real bugs that looked fine on screen:

- **Every password field had an orphaned label.** `<label htmlFor="login-password">` pointed at an id
  that did not exist, because `PasswordInput` rendered its input without one. Sighted users saw a
  labelled field; a screen reader announced an unnamed "protected edit text". On the reset screen
  that meant two indistinguishable password boxes.
- **The break timer button announced only "4:57"** — a number with no indication of what it counted
  or what pressing it would do.
- **The feed had no heading at all**, so heading navigation had nothing to land on.

Two decisions worth explaining:

**The break timer is `aria-live="off"`.** A live region announcing a new time every second would talk
over everything else and never stop, making the break unusable. The remaining time is exposed via
`aria-label` and available on demand instead. *A live region is not automatically an improvement —
an unwelcome one is worse than none.*

**`sr-only` clips rather than hides.** `display: none` would remove the text from the accessibility
tree entirely, which defeats the point. It is clipped to a single pixel so it stays readable to
assistive technology while being invisible on screen.

---

## Engineering notes

Problems worth explaining, because the fix is more interesting than the feature.

### Simplifying the surface without deleting the code

Ten features across six navigation items is a lot to hand somebody on their
first visit. Three changes cut what a user has to deal with, without removing
anything the backend can do:

**Explore folded into the feed.** A separate page listed every topic and showed
that community's posts — which is what the feed already did when you clicked a
topic chip. The same destination by two routes, costing a nav item. It is now a
topic menu in the feed's own tab row, and the category browsing survived intact.
Nav went from six items to four.

That change also fixed a bug: the old topic filter narrowed whatever posts were
already loaded, so a topic with nothing recent looked empty even when it was not.
The menu asks the server for that community's posts instead.

**Seven privacy switches became one sensible default.** Nobody configures seven
toggles, so in practice everybody's email and phone number were public — that was
the default, and nothing prompted anyone to think about it. Contact details now
default to hidden and the switches are gone. The flags and their enforcement
stayed, because the rule is still worth having; only the decision was taken away
from the user.

**The composer collapses.** The feed opened with a textarea, a topic picker, a
checkbox and a button stacked above the first post — four decisions before you
had read anything. It is one line now, expanding on click. Most visits to a feed
are to read.

---

### Unused code is unreviewed code

Wiring up the study buddies UI turned up a vulnerability that had been sitting in the codebase for
months: `/api/buddies/pending` and `/sent` returned `StudyBuddy` **entities**. Each holds two `User`
objects, so the JSON contained BCrypt password hashes, plus emails and phone numbers belonging to
people who had marked them private.

Nobody had noticed because nothing ever called those endpoints. The backend was "finished" and the
frontend had never been built, so no request had ever been looked at.

The fix is the same one as everywhere else in this file — map through a DTO, never serialize an
entity — and the test asserts against the **raw response body** rather than a named field, because
the bug was that an entire object tree got serialized. A field-level check would have passed: nobody
thinks to assert on `$[0].user.buddy.password`.

---

### Identity had to move out of the URL

Covered in full under [Authentication](#authentication), but it belongs on this list because it is
the same mistake as the two entries below it, in a third disguise: a rule that lived on the client.

The server checked ownership correctly. It just took the client's word for who the client was.

---

### Anonymity is enforced on the server

The first version of `PostResponse` sent `authorName: "Anonymous"` but still included the real
`authorId`, because the React app needed it to decide whether to draw a Delete button.

That defeated the feature entirely. Anyone could open DevTools, read the raw JSON, and match the ID
back to a user. The post *said* anonymous; the data underneath did not.

Now an anonymous post carries **no identifying field at all** — `authorId` and `authorUsername` are
null. Ownership is signalled instead by a boolean `ownPost`, computed server-side for the one person
asking, which answers "may I delete this?" without naming anybody.

> If the browser must not know something, do not send it. Hidden in the UI is not hidden.

The same rule drives `UserProfileResponse`: a field you have marked private is never written into
the response, so there is nothing to find in the page source.

### Normalizing topics fixed the personalized feed

Topics were stored as one string — `"Programming, Web Development"` — in a single column. The
personalized feed then tried to match a user's interest against that whole string, and
`"Programming"` never equals `"Programming, Web Development"`. **The *For you* tab returned zero
posts, always.** Splitting on `","` also left a leading space on every item after the first, which
broke the comparison a second time.

The fix was to normalize: each topic became its own row in `post_topics`. Matching is now an
ordinary indexed lookup, a post can carry any number of topics, and the feed works.

### Deleting a post used to crash

`comments.post_id` and `helpfuls.post_id` are foreign keys, and a database will not delete a row that
other rows still reference. Any post someone had commented on could not be deleted — it failed with
a constraint violation.

`PostService.deletePost` now removes the children first, inside a single `@Transactional` method so a
partial failure rolls the whole thing back.

### Password reset needed a token

The original flow accepted an email address *and* a new password in one request, which meant anyone
who knew your email could take your account.

It is now two steps. Step one creates a random, 30-minute, single-use token tied to the account. Step
two exchanges the token for a new password — and takes **no** email or user ID, so the caller cannot
name whose password to change.

Step one also returns an identical response whether or not the account exists, so the endpoint cannot
be used to discover which email addresses are registered (*user enumeration*). Login does the same,
answering "incorrect email or password" without saying which.

### Removing N+1 queries from the feed

Building the feed used to call `getCommentCount(postId)` and `getHelpfulUsers(postId)` once per post,
and each of those re-fetched the post first. Fifty posts meant over two hundred round trips to the
database to render one page.

`PostService.toResponses` now fetches the counts for the whole batch in two `GROUP BY` queries and
stitches them together in memory. The feed costs a fixed number of queries regardless of how many
posts come back.

### The break timer is anchored to a server timestamp

Counting down in JavaScript is unreliable: browsers throttle timers in background tabs, the clock
stops while a laptop sleeps, and anything in memory can be edited from the console.

The server sends an absolute `endsAt` and the client recomputes `endsAt - now` on every tick. Ticks
can be late, throttled or skipped and the displayed time is still right — the tick only decides *when
to re-read the clock*, never what the answer is.

The cooldown deliberately starts when a break **ends**, not when it starts, so the study period
between breaks is always a full hour whether the break ran five minutes or ten.

---

## Known limitations

Honest list of what is not finished. These are known, not overlooked.

- **Tokens cannot be revoked.** A JWT is valid until it expires (7 days by default), so "log out
  everywhere" is not possible and a stolen token stays usable. The standard fix is a short-lived
  access token plus a refresh token, which is the next thing I would add.
- **The token is kept in `localStorage`**, where page JavaScript can read it. An XSS flaw would
  therefore leak the session. The alternative — an httpOnly cookie — is safer against XSS but
  reintroduces CSRF and needs matching server work. The trade-off is deliberate and documented in
  `api.ts` rather than glossed over.
- **No rate limiting.** Nothing stops a script trying thousands of passwords against `/api/login`.
- **No pagination.** The feed returns every post. Fine at demo scale, wrong at any real size.
- **Test coverage is deliberately narrow.** 40 tests, concentrated on authentication,
  authorization and study buddies because that is where a bug is most costly. Posting, comments and
  the break rules are verified by hand, not by the suite.
- **Buddy suggestions are ranked in memory.** Every other user is loaded and scored in Java. Fine at
  this size, wrong at any real one — the fix is to normalize interests into their own table, exactly
  as post topics were, and let the database do the matching.
- **`ddl-auto=update` instead of migrations.** Convenient in development; a real deployment needs
  Flyway or Liquibase so schema changes are versioned and reversible.
- **Navigation is component state, not routes.** There is no react-router, so pages are not linkable
  and the browser Back button does not move between them.
- **Break "today" uses the server's timezone**, which is wrong for anyone in a different one.

---

## What I learned building this

- Where a rule is enforced matters more than whether it exists. Four separate bugs here — the
  anonymous author ID, the privacy switches, the break cooldown, and identity itself — were all the
  same mistake wearing different clothes: a rule implemented where the user controls the code.
- Authentication and authorization are different questions, and getting the second right is worth
  nothing without the first. This app checked ownership carefully from the very beginning. It just
  had no idea who was asking, so every one of those careful checks was decorative.
- Denormalized data reads fine and queries terribly. One comma-separated column silently broke a
  headline feature for months.
- Foreign keys will not let you delete a parent while children point at it, and the error surfaces
  at the worst possible moment — in front of somebody.
