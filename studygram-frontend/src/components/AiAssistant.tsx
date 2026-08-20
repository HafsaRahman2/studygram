import { useEffect, useRef, useState } from 'react'
import { ai } from '../api'
import type { AiMessage, AiMode, User } from '../types'
import { Avatar } from './ui'

/*
 * AiAssistant - The study assistant, backed by Groq via our own backend
 *
 * NOTE ON WHY THE BACKEND IS IN THE MIDDLE
 *
 * The browser could call Groq directly, but then the API key would have to be
 * shipped to the browser, where anyone can read it out of the network tab and
 * spend your quota. Routing through our own server keeps the key on the server.
 */

const MODES: Array<{
  id: AiMode
  label: string
  icon: string
  description: string
  placeholder: string
}> = [
  {
    id: 'chat',
    label: 'Chat',
    icon: '💬',
    description: 'Ask anything about what you are studying.',
    placeholder: 'Ask a question...',
  },
  {
    id: 'explain',
    label: 'Explain',
    icon: '💡',
    description: 'Name a topic and get it explained simply.',
    placeholder: 'e.g. recursion, photosynthesis, supply and demand',
  },
  {
    id: 'practice',
    label: 'Practice',
    icon: '📝',
    description: 'Get practice questions with answers, increasing in difficulty.',
    placeholder: 'e.g. sorting algorithms, French verbs',
  },
  {
    id: 'summarize',
    label: 'Summarize',
    icon: '📄',
    description: 'Paste your notes and get the key points back.',
    placeholder: 'Paste your notes here...',
  },
]

export function AiAssistant({ currentUser }: { currentUser: User }) {
  const [mode, setMode] = useState<AiMode>('chat')
  const [messages, setMessages] = useState<AiMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)

  /*
   * A handle on the bottom of the message list, so we can scroll to it whenever
   * a new message arrives. Without this, replies appear below the fold and the
   * user has to scroll manually every time.
   */
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  const activeMode = MODES.find((m) => m.id === mode)!

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault()

    const question = input.trim()
    if (!question || loading) return

    setInput('')
    setMessages((current) => [...current, { role: 'user', content: question }])
    setLoading(true)

    try {
      /*
       * Each mode hits a different endpoint. The backend wraps the user's text
       * in a different prompt for each one, which is why "Explain" gives a
       * teaching answer and "Practice" gives questions.
       */
      let answer: string

      switch (mode) {
        case 'explain':
          answer = await ai.explain(question)
          break
        case 'practice':
          answer = await ai.practice(question, 5)
          break
        case 'summarize':
          answer = await ai.summarize(question)
          break
        default:
          answer = await ai.chat(question)
      }

      setMessages((current) => [...current, { role: 'ai', content: answer }])
    } catch (err) {
      setMessages((current) => [
        ...current,
        {
          role: 'ai',
          content:
            err instanceof Error
              ? err.message
              : 'Something went wrong reaching the assistant.',
        },
      ])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="ai">
      <header className="ai-head">
        <h2>Study assistant</h2>
        {messages.length > 0 && (
          <button className="link" onClick={() => setMessages([])}>
            Clear conversation
          </button>
        )}
      </header>

      <div className="ai-modes" role="tablist">
        {MODES.map((m) => (
          <button
            key={m.id}
            role="tab"
            aria-selected={mode === m.id}
            className={`mode ${mode === m.id ? 'active' : ''}`}
            onClick={() => setMode(m.id)}
          >
            <span aria-hidden="true">{m.icon}</span>
            {m.label}
          </button>
        ))}
      </div>

      <p className="ai-mode-desc">{activeMode.description}</p>

      <div className="ai-chat">
        {messages.length === 0 && !loading && (
          <div className="ai-welcome">
            <div className="ai-welcome-icon" aria-hidden="true">
              🎓
            </div>
            <h3>Hi {currentUser.name ?? currentUser.username}</h3>
            <p>What are you working on today?</p>
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

            {/* whiteSpace: pre-wrap keeps the model's paragraph breaks and
                indentation instead of collapsing everything into one block. */}
            <div className="bubble">{message.content}</div>
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

      <form className="ai-input" onSubmit={handleSubmit}>
        {mode === 'summarize' ? (
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={activeMode.placeholder}
            rows={4}
            aria-label="Notes to summarize"
          />
        ) : (
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={activeMode.placeholder}
            aria-label="Your question"
          />
        )}

        <button type="submit" className="btn" disabled={loading || !input.trim()}>
          {loading ? 'Thinking...' : 'Send'}
        </button>
      </form>
    </div>
  )
}
