import { useEffect, useRef, useState } from 'react'
import { ai } from '../api'
import type { AiMessage, User } from '../types'
import { renderMarkdown } from '../utils/markdown'
import { Avatar, Message } from './ui'

/*
 * AiAssistant - A private conversation with the study assistant
 *
 * WHAT CHANGED, AND WHY IT MATTERED
 *
 * This screen used to have four mode buttons: Chat, Explain, Practice,
 * Summarize. They were the same endpoint with different wording wrapped around
 * whatever you typed, and they caused two problems.
 *
 * First, they could be wrong. Leave it on Summarize from ten minutes ago, type
 * a question, and you got a summary OF YOUR QUESTION, with nothing on screen
 * explaining why.
 *
 * Second, and worse: the assistant had no memory. Every message was sent alone,
 * so "give me an example" was answered with "an example of what?". It looked
 * like a chat and behaved like a series of strangers.
 *
 * That second one also meant this whole page duplicated the instant AI answers
 * on posts - both gave you one answer to one question, and the feed did it
 * better because people could answer too.
 *
 * Now the conversation is sent with every message, so follow-ups work. And
 * Summarize and Practice became ACTIONS ON THE CONVERSATION rather than modes:
 * "write up what we just covered", "now test me on it". Those are things a post
 * cannot do, and that is what makes this page useful.
 */

/*
 * Starter prompts, shown only on an empty conversation.
 *
 * Most people faced with an empty box do not know what to ask an AI, and type
 * something like "help". These say what is possible - then get out of the way
 * once the conversation has started and the hint is no longer needed.
 *
 * They FILL IN the input rather than sending anything, so you finish the
 * sentence yourself and learn how to phrase the next one unaided.
 */
const STARTERS = [
  { label: 'Explain a topic', text: 'Explain ' },
  { label: 'Help me understand', text: "I don't understand " },
  { label: 'Check my reasoning', text: 'Is this right? ' },
]

/*
 * Where the conversation is kept between page changes.
 *
 * WHY sessionStorage AND NOT localStorage
 *
 * Navigating to the feed unmounts this component and React throws its state
 * away, so the conversation vanished every time you looked at anything else.
 * It needs to live somewhere outside the component.
 *
 * sessionStorage keeps it for as long as the browser TAB is open, and drops it
 * when the tab closes. localStorage would keep it indefinitely - convenient,
 * but this is a private conversation, and on a shared or library computer
 * "indefinitely" means the next person finds it.
 *
 * Surviving navigation and refresh is the actual complaint. Surviving until
 * next Tuesday is not worth leaving somebody's study worries on a shared
 * machine.
 */
const CHAT_KEY = 'studygram.assistant'

function loadConversation(): AiMessage[] {
  try {
    const raw = sessionStorage.getItem(CHAT_KEY)
    return raw ? (JSON.parse(raw) as AiMessage[]) : []
  } catch {
    // Private browsing, or a half-written value. An empty chat is a fine
    // place to start from.
    return []
  }
}

export function AiAssistant({ currentUser }: { currentUser: User }) {
  /*
   * The function form of useState runs only on the first render. Writing
   * useState(loadConversation()) would re-read storage on every render and
   * throw the result away.
   */
  const [messages, setMessages] = useState<AiMessage[]>(loadConversation)
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const inputRef = useRef<HTMLTextAreaElement>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  /* Follow the conversation as it grows. */
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  /* Keep the stored copy in step, so leaving the page does not lose it. */
  useEffect(() => {
    try {
      if (messages.length > 0) {
        sessionStorage.setItem(CHAT_KEY, JSON.stringify(messages))
      } else {
        sessionStorage.removeItem(CHAT_KEY)
      }
    } catch {
      // Storage unavailable. The chat still works; it just will not survive
      // navigating away.
    }
  }, [messages])

  const hasConversation = messages.length > 0

  /*
   * One place that talks to the assistant.
   *
   * Chat, summarise and practice differ only in which call they make and what
   * the user's turn looks like in the transcript - everything else (optimistic
   * append, loading state, error handling) is identical, so it lives here once.
   */
  async function send(
    userTurn: string,
    call: (history: AiMessage[]) => Promise<string>,
  ) {
    if (loading) return

    setError('')

    // Show the user's turn immediately, and build the history to send from it.
    const history: AiMessage[] = [...messages, { role: 'user', content: userTurn }]
    setMessages(history)
    setLoading(true)

    try {
      const answer = await call(history)
      setMessages((current) => [...current, { role: 'ai', content: answer }])
    } catch (err) {
      /*
       * The failed turn stays in the transcript rather than vanishing - you
       * can see what you asked, and try again without retyping it.
       */
      setError(
        err instanceof Error
          ? err.message
          : 'The assistant is unavailable right now.',
      )
    } finally {
      setLoading(false)
    }
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const question = input.trim()
    if (!question) return

    setInput('')
    send(question, (history) => ai.chat(history))
  }

  function useStarter(text: string) {
    setInput(text)
    inputRef.current?.focus()
    /* Cursor at the end, so they type straight on from the prompt. */
    requestAnimationFrame(() => {
      const el = inputRef.current
      el?.setSelectionRange(el.value.length, el.value.length)
    })
  }

  return (
    <div className="ai">
      <header className="ai-head">
        <div>
          <h2>Study assistant</h2>
          <p className="muted">
            A private conversation, nobody else sees this. Ask follow-ups; it
            remembers what you have been discussing.
          </p>
        </div>

        {hasConversation && (
          <button className="link" onClick={() => setMessages([])}>
            Start over
          </button>
        )}
      </header>

      <div className="ai-chat">
        {!hasConversation && !loading && (
          <div className="ai-welcome">
            <div className="ai-welcome-icon" aria-hidden="true">
              🎓
            </div>
            <h3>Hi {currentUser.name ?? currentUser.username}</h3>
            <p>What are you working on today?</p>

            <div className="starters">
              {STARTERS.map((starter) => (
                <button
                  key={starter.label}
                  className="starter"
                  onClick={() => useStarter(starter.text)}
                >
                  {starter.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((message, index) => (
          <div key={index} className={`bubble-row ${message.role}`}>
            {message.role === 'ai' ? (
              <div className="ai-avatar" aria-hidden="true">
                🎓
              </div>
            ) : (
              <Avatar name={currentUser.name ?? currentUser.username} size={32} />
            )}

            {/* AI replies come back as Markdown; ours is plain text. */}
            <div className="bubble">
              {message.role === 'ai' ? renderMarkdown(message.content) : message.content}
            </div>
          </div>
        ))}

        {loading && (
          <div className="bubble-row ai">
            <div className="ai-avatar" aria-hidden="true">
              🎓
            </div>
            <div className="bubble typing">
              <span />
              <span />
              <span />
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      <Message kind="error" onDismiss={() => setError('')}>
        {error}
      </Message>

      {/*
        ACTIONS ON THE CONVERSATION, not modes.
        Hidden entirely until there is something to act on - a "test me on this"
        button with no "this" behind it is just a button that fails.
      */}
      {hasConversation && (
        <div className="ai-actions">
          <span className="ai-actions-label">On this conversation:</span>

          <button
            className="btn btn-small btn-secondary"
            disabled={loading}
            onClick={() =>
              send('Summarise what we have covered.', (history) => ai.summarize(history))
            }
          >
            📄 Summarise it
          </button>

          <button
            className="btn btn-small btn-secondary"
            disabled={loading}
            onClick={() =>
              send('Test me on what we have covered.', (history) => ai.practice(history, 5))
            }
          >
            📝 Test me on it
          </button>
        </div>
      )}

      <form className="ai-input" onSubmit={handleSubmit}>
        <textarea
          ref={inputRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={
            hasConversation ? 'Ask a follow-up...' : 'Ask anything about what you are studying...'
          }
          rows={2}
          aria-label="Your message"
          onKeyDown={(e) => {
            /*
             * Enter sends, Shift+Enter makes a new line - what every chat app
             * does. Without this, Enter in a textarea just adds a line and the
             * send button is the only way out.
             */
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              handleSubmit(e)
            }
          }}
        />

        <button type="submit" className="btn" disabled={loading || !input.trim()}>
          {loading ? 'Thinking...' : 'Send'}
        </button>
      </form>
    </div>
  )
}
