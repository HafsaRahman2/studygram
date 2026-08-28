import { useCallback, useEffect, useState } from 'react'
import { buddies as buddiesApi } from '../api'
import type { BuddyRelationship, BuddyRequest, User, UserSearchResult } from '../types'
import { parseInterests } from '../utils/format'
import { Avatar, EmptyState, Message, Spinner, TopicChips } from './ui'

/*
 * Buddies - Find people, manage requests, see your connections
 *
 * Three tabs, because there are three genuinely different jobs here: looking
 * at who you know, answering people who asked for you, and finding new people.
 * Putting them on one page would mean a long scroll where the thing needing
 * your attention (a pending request) sits below things that do not.
 *
 * The backend for this existed for months with no UI at all. Wiring it up
 * turned up a vulnerability nobody had noticed precisely because nothing ever
 * called the endpoints: /pending and /sent were returning raw entities, and
 * therefore BCrypt password hashes. Unused code is unreviewed code.
 */

type Tab = 'buddies' | 'requests' | 'find'

export function Buddies({
  currentUser,
  onRequestsChanged,
}: {
  currentUser: User
  /* Lets the header's pending badge update when you accept or reject. */
  onRequestsChanged: () => void
}) {
  const [tab, setTab] = useState<Tab>('buddies')

  const [myBuddies, setMyBuddies] = useState<User[]>([])
  const [incoming, setIncoming] = useState<BuddyRequest[]>([])
  const [outgoing, setOutgoing] = useState<BuddyRequest[]>([])

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  /*
   * Load all three lists together.
   *
   * They are small, they are needed for the tab counts whichever tab is open,
   * and accepting a request changes two of them at once. Fetching per tab
   * would mean three loading states and stale badges.
   */
  const loadAll = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const [buddyList, pending, sent] = await Promise.all([
        buddiesApi.list(),
        buddiesApi.pending(),
        buddiesApi.sent(),
      ])

      setMyBuddies(buddyList)
      setIncoming(pending)
      setOutgoing(sent)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load your buddies')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  /* After any action, reload and let the header badge know. */
  const refresh = useCallback(async () => {
    await loadAll()
    onRequestsChanged()
  }, [loadAll, onRequestsChanged])

  async function act(action: () => Promise<unknown>) {
    try {
      await action()
      await refresh()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    }
  }

  return (
    <div className="buddies">
      <header className="page-head">
        <h2>Your crew</h2>
        <p className="muted">
          The people learning the same things as you.
        </p>
      </header>

      {/*
        Toggle buttons, not tabs - the same correction made on the feed.

        role="tab" promises arrow-key navigation between the tabs and a panel
        each one controls. Neither was implemented, so screen reader users were
        told about a keyboard shortcut that does not exist and pointed at a
        panel that was never there. aria-pressed describes what these actually
        do, and the counts are spelled out so "Requests 2" is not announced as
        the number two floating after a word.
      */}
      <div className="tabs" role="group" aria-label="Crew sections">
        <button
          aria-pressed={tab === 'buddies'}
          className={`tab ${tab === 'buddies' ? 'active' : ''}`}
          onClick={() => setTab('buddies')}
        >
          Your crew
          {myBuddies.length > 0 && (
            <span className="tab-count">
              {myBuddies.length}
              <span className="sr-only"> people</span>
            </span>
          )}
        </button>

        <button
          aria-pressed={tab === 'requests'}
          className={`tab ${tab === 'requests' ? 'active' : ''}`}
          onClick={() => setTab('requests')}
        >
          Requests
          {/* Only incoming requests get the attention-seeking badge - the ones
              you sent are not waiting on you. */}
          {incoming.length > 0 && (
            <span className="tab-badge">
              {incoming.length}
              <span className="sr-only"> waiting for you</span>
            </span>
          )}
        </button>

        <button
          aria-pressed={tab === 'find'}
          className={`tab ${tab === 'find' ? 'active' : ''}`}
          onClick={() => setTab('find')}
        >
          Find people
        </button>
      </div>

      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      {loading && <Spinner label="Loading" />}

      {!loading && tab === 'buddies' && (
        <BuddyList
          buddies={myBuddies}
          onRemove={(id) => act(() => buddiesApi.remove(id))}
          onFindPeople={() => setTab('find')}
        />
      )}

      {!loading && tab === 'requests' && (
        <Requests
          incoming={incoming}
          outgoing={outgoing}
          onAccept={(id) => act(() => buddiesApi.accept(id))}
          onReject={(id) => act(() => buddiesApi.reject(id))}
          onCancel={(userId) => act(() => buddiesApi.remove(userId))}
          onFindPeople={() => setTab('find')}
        />
      )}

      {tab === 'find' && <FindPeople currentUser={currentUser} onChanged={refresh} />}
    </div>
  )
}

/* ------------------------------------------------------------- BuddyList */

function BuddyList({
  buddies,
  onRemove,
  onFindPeople,
}: {
  buddies: User[]
  onRemove: (userId: number) => void
  onFindPeople: () => void
}) {
  if (buddies.length === 0) {
    return (
      <EmptyState
        icon="🤝"
        title="Your crew is empty"
        action={
          <button className="btn" onClick={onFindPeople}>
            Find people
          </button>
        }
      >
        Your crew is the people learning the same things as you.
      </EmptyState>
    )
  }

  return (
    <div className="person-list">
      {buddies.map((buddy) => (
        <PersonCard
          key={buddy.id}
          user={buddy}
          action={<RemoveButton onConfirm={() => onRemove(buddy.id)} />}
        />
      ))}
    </div>
  )
}

/* -------------------------------------------------------------- Requests */

function Requests({
  incoming,
  outgoing,
  onAccept,
  onReject,
  onCancel,
  onFindPeople,
}: {
  incoming: BuddyRequest[]
  outgoing: BuddyRequest[]
  onAccept: (requestId: number) => void
  onReject: (requestId: number) => void
  onCancel: (userId: number) => void
  onFindPeople: () => void
}) {
  if (incoming.length === 0 && outgoing.length === 0) {
    return (
      <EmptyState
        icon="📭"
        title="No pending requests"
        action={
          <button className="btn" onClick={onFindPeople}>
            Find people
          </button>
        }
      >
        Requests you send and receive will show up here.
      </EmptyState>
    )
  }

  return (
    <>
      {/* Incoming first: these are the ones waiting on you. */}
      {incoming.length > 0 && (
        <section className="request-group">
          <h3>Waiting for you</h3>
          <div className="person-list">
            {incoming.map((req) => (
              <PersonCard
                key={req.requestId}
                user={req.user}
                action={
                  <div className="card-actions">
                    <button className="btn btn-small" onClick={() => onAccept(req.requestId)}>
                      Accept
                    </button>
                    <button
                      className="btn btn-small btn-secondary"
                      onClick={() => onReject(req.requestId)}
                    >
                      Decline
                    </button>
                  </div>
                }
              />
            ))}
          </div>
        </section>
      )}

      {outgoing.length > 0 && (
        <section className="request-group">
          <h3>Sent</h3>
          <div className="person-list">
            {outgoing.map((req) => (
              <PersonCard
                key={req.requestId}
                user={req.user}
                action={
                  <div className="card-actions">
                    <span className="pill">Pending</span>
                    <button
                      className="link"
                      onClick={() => onCancel(req.user.id)}
                      title="Withdraw this request"
                    >
                      Cancel
                    </button>
                  </div>
                }
              />
            ))}
          </div>
        </section>
      )}
    </>
  )
}

/* ------------------------------------------------------------ FindPeople */

function FindPeople({
  currentUser,
  onChanged,
}: {
  currentUser: User
  onChanged: () => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<UserSearchResult[]>([])
  const [suggestions, setSuggestions] = useState<UserSearchResult[]>([])
  const [searching, setSearching] = useState(false)
  const [loadingSuggestions, setLoadingSuggestions] = useState(true)
  const [error, setError] = useState('')

  const hasInterests = parseInterests(currentUser.interests).length > 0

  /* Suggestions load once; they do not depend on the search box. */
  useEffect(() => {
    let cancelled = false

    buddiesApi
      .suggestions()
      .then((list) => {
        if (!cancelled) setSuggestions(list)
      })
      .catch(() => {
        /* Suggestions are a bonus; a failure here should not shout. */
      })
      .finally(() => {
        if (!cancelled) setLoadingSuggestions(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  /*
   * DEBOUNCED SEARCH
   *
   * Firing a request on every keystroke would send one per letter — six
   * requests to type "hafsa", five of them already stale by the time they
   * return, and results that flicker as they arrive out of order.
   *
   * Instead each keystroke schedules a search 300ms out and cancels the
   * previous one, so only a genuine pause in typing actually reaches the
   * server.
   */
  useEffect(() => {
    const trimmed = query.trim()

    if (trimmed.length < 2) {
      setResults([])
      setSearching(false)
      return
    }

    setSearching(true)
    let cancelled = false

    const timer = setTimeout(() => {
      buddiesApi
        .search(trimmed)
        .then((list) => {
          if (!cancelled) setResults(list)
        })
        .catch((err) => {
          if (!cancelled) setError(err instanceof Error ? err.message : 'Search failed')
        })
        .finally(() => {
          if (!cancelled) setSearching(false)
        })
    }, 300)

    /* Cancels both the pending timer and any in-flight response. */
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [query])

  /*
   * Update one result in place after an action, so the button changes to
   * "Pending" without refetching the whole list and losing the user's scroll.
   */
  function patchResult(userId: number, relationship: BuddyRelationship) {
    const patch = (list: UserSearchResult[]) =>
      list.map((r) => (r.user.id === userId ? { ...r, relationship } : r))

    setResults(patch)
    setSuggestions(patch)
  }

  async function sendRequest(userId: number) {
    // Optimistic: the button flips immediately, and reverts if the server says no.
    patchResult(userId, 'REQUEST_SENT')

    try {
      await buddiesApi.sendRequest(userId)
      onChanged()
    } catch (err) {
      patchResult(userId, 'NONE')
      setError(err instanceof Error ? err.message : 'Could not send that request')
    }
  }

  async function acceptRequest(requestId: number, userId: number) {
    patchResult(userId, 'BUDDIES')

    try {
      await buddiesApi.accept(requestId)
      onChanged()
    } catch (err) {
      patchResult(userId, 'REQUEST_RECEIVED')
      setError(err instanceof Error ? err.message : 'Could not accept that request')
    }
  }

  const searchQuery = query.trim()
  const showingSearch = searchQuery.length >= 2

  return (
    <div className="find-people">
      <div className="search-box">
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name or username..."
          aria-label="Search for people"
        />
      </div>

      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      {/* ------------------------------------------------ search results */}
      {showingSearch && (
        <section>
          <h3 className="section-label">
            Results {searching && <span className="muted">searching...</span>}
          </h3>

          {!searching && results.length === 0 ? (
            <EmptyState icon="🔍" title={`Nobody matching "${searchQuery}"`}>
              Try a different name or username.
            </EmptyState>
          ) : (
            <div className="person-list">
              {results.map((result) => (
                <PersonCard
                  key={result.user.id}
                  user={result.user}
                  sharedInterests={result.sharedInterests}
                  action={
                    <RelationshipAction
                      result={result}
                      onAdd={() => sendRequest(result.user.id)}
                      onAccept={() =>
                        result.requestId && acceptRequest(result.requestId, result.user.id)
                      }
                    />
                  }
                />
              ))}
            </div>
          )}
        </section>
      )}

      {/* -------------------------------------------------- suggestions */}
      {!showingSearch && (
        <section>
          <h3 className="section-label">Suggested for you</h3>

          {loadingSuggestions && <Spinner label="Finding people" />}

          {!loadingSuggestions && !hasInterests && (
            <EmptyState icon="🎯" title="Add your interests first">
              Suggestions are based on subjects you have in common. Add some
              interests on your profile and people will show up here.
            </EmptyState>
          )}

          {!loadingSuggestions && hasInterests && suggestions.length === 0 && (
            <EmptyState icon="🌱" title="No suggestions right now">
              Nobody new shares your interests yet. Try searching by name.
            </EmptyState>
          )}

          <div className="person-list">
            {suggestions.map((result) => (
              <PersonCard
                key={result.user.id}
                user={result.user}
                sharedInterests={result.sharedInterests}
                action={
                  <RelationshipAction
                    result={result}
                    onAdd={() => sendRequest(result.user.id)}
                    onAccept={() =>
                      result.requestId && acceptRequest(result.requestId, result.user.id)
                    }
                  />
                }
              />
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

/* ---------------------------------------------------- RelationshipAction */

/*
 * The right button for where you two stand.
 *
 * The server sends the relationship (see UserSearchResult), so this component
 * only has to render it — there is no client-side logic deciding who you are
 * connected to, which is exactly how it should be.
 */
function RelationshipAction({
  result,
  onAdd,
  onAccept,
}: {
  result: UserSearchResult
  onAdd: () => void
  onAccept: () => void
}) {
  switch (result.relationship) {
    case 'BUDDIES':
      return <span className="pill pill-success">In your crew</span>

    case 'REQUEST_SENT':
      return <span className="pill">Pending</span>

    case 'REQUEST_RECEIVED':
      return (
        <button className="btn btn-small" onClick={onAccept}>
          Accept
        </button>
      )

    case 'SELF':
      return <span className="pill muted-pill">You</span>

    default:
      return (
        <button className="btn btn-small btn-secondary" onClick={onAdd}>
          Add to crew
        </button>
      )
  }
}

/* ----------------------------------------------------------- RemoveButton */

/* Two-step, because removing a buddy is not something to do by mis-click. */
function RemoveButton({ onConfirm }: { onConfirm: () => void }) {
  const [confirming, setConfirming] = useState(false)

  if (!confirming) {
    return (
      <button className="link subtle-link" onClick={() => setConfirming(true)}>
        Remove
      </button>
    )
  }

  return (
    <div className="card-actions">
      <button className="btn btn-small danger-btn" onClick={onConfirm}>
        Remove
      </button>
      <button className="link" onClick={() => setConfirming(false)}>
        Cancel
      </button>
    </div>
  )
}

/* ------------------------------------------------------------ PersonCard */

/*
 * One person, used by all three tabs. The action slot is whatever that
 * context needs: Accept, Add, Remove, or a status pill.
 */
function PersonCard({
  user,
  sharedInterests,
  action,
}: {
  user: User
  sharedInterests?: string[]
  action: React.ReactNode
}) {
  const interests = parseInterests(user.interests)

  return (
    <article className="person-card">
      <Avatar name={user.name ?? user.username} size={44} />

      <div className="person-info">
        <span className="person-name">{user.name ?? user.username}</span>
        <span className="person-handle">@{user.username}</span>

        {user.careerGoal && <span className="person-goal">🎯 {user.careerGoal}</span>}

        {/*
          Shared interests are the reason to connect, so they get priority over
          the person's full interest list. "3 subjects in common" is a reason;
          a list of everything they study is just noise.
        */}
        {sharedInterests && sharedInterests.length > 0 ? (
          <div className="shared">
            <span className="shared-label">
              {sharedInterests.length} shared{' '}
              {sharedInterests.length === 1 ? 'interest' : 'interests'}
            </span>
            <TopicChips topics={sharedInterests} />
          </div>
        ) : (
          interests.length > 0 && <TopicChips topics={interests.slice(0, 3)} />
        )}
      </div>

      <div className="person-action">{action}</div>
    </article>
  )
}
