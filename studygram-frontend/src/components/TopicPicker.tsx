import { useEffect, useId, useRef, useState } from 'react'
import { useTopics } from '../hooks/useTopics'

/*
 * TopicPicker - Searchable multi-select for topics
 *
 * Used in two places: choosing what a post is about, and choosing your
 * interests on the profile page. Both needed the same searchable dropdown with
 * removable tags, and both previously had their own near-identical copy of it
 * inside App.tsx, each with its own bugs.
 *
 * Improvements over the original:
 *   - closes when you click outside it (the old one stayed open forever)
 *   - keyboard accessible: arrow keys to move, Enter to pick, Escape to close
 *   - topics grouped under category headings
 *   - shows a count and enforces a maximum
 */
export function TopicPicker({
  selected,
  onChange,
  max = 5,
  placeholder = 'Search topics...',
  label,
}: {
  selected: string[]
  onChange: (topics: string[]) => void
  max?: number
  placeholder?: string
  label?: string
}) {
  const { topicNames, byCategory, loading } = useTopics()

  /*
   * useId gives ids that are unique per instance and stable across renders.
   *
   * Two things were wrong without it. The visible <label> had no htmlFor and
   * the input had no id, so the label was decoration - to a screen reader this
   * combobox had no name at all, on signup, in the composer and on the profile
   * alike. And the listbox id was the literal string "topic-listbox", so two
   * pickers on one page would both claim it and aria-controls would point at
   * whichever won.
   */
  const inputId = useId()
  const listboxId = useId()

  const [search, setSearch] = useState('')
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(0)

  /*
   * useRef gives us a handle on the actual DOM element, which we need in order
   * to ask "was this click inside me or outside me?".
   */
  const containerRef = useRef<HTMLDivElement>(null)

  /*
   * Close the dropdown when the user clicks anywhere else on the page.
   *
   * The listener goes on `document` because the click we care about happens
   * outside this component, so no handler of ours would otherwise see it.
   * The cleanup function removes it - forgetting that is a classic memory leak,
   * since every mount would add another listener that never goes away.
   */
  useEffect(() => {
    if (!open) return

    function handleClickOutside(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const atLimit = selected.length >= max

  /* Topics matching the search box, minus the ones already picked. */
  const matches = topicNames.filter(
    (name) =>
      name.toLowerCase().includes(search.toLowerCase()) && !selected.includes(name),
  )

  function add(topic: string) {
    if (atLimit || selected.includes(topic)) return
    onChange([...selected, topic])
    setSearch('')
    setHighlighted(0)
  }

  function remove(topic: string) {
    onChange(selected.filter((t) => t !== topic))
  }

  /*
   * Keyboard navigation. A dropdown you can only use with a mouse is unusable
   * for anyone relying on a keyboard or a screen reader.
   */
  function handleKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      setOpen(false)
      return
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setHighlighted((i) => Math.min(i + 1, matches.length - 1))
      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setHighlighted((i) => Math.max(i - 1, 0))
      return
    }

    if (event.key === 'Enter' && open && matches[highlighted]) {
      // Stop the form this picker sits inside from submitting
      event.preventDefault()
      add(matches[highlighted])
      return
    }

    /*
     * Backspace on an empty search box removes the last tag. Small touch, but
     * it is what every tag input people already use does.
     */
    if (event.key === 'Backspace' && search === '' && selected.length > 0) {
      remove(selected[selected.length - 1])
    }
  }

  return (
    <div className="topic-picker" ref={containerRef}>
      {label && (
        <div className="picker-label">
          <label htmlFor={inputId}>{label}</label>
          <span className={`picker-count ${atLimit ? 'at-limit' : ''}`}>
            {selected.length}/{max}
          </span>
        </div>
      )}

      {/* Chosen topics, each removable */}
      {selected.length > 0 && (
        <div className="chips">
          {selected.map((topic) => (
            <span key={topic} className="chip chip-selected">
              {topic}
              <button
                type="button"
                onClick={() => remove(topic)}
                aria-label={`Remove ${topic}`}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="picker-input-wrap">
        <input
          id={inputId}
          /*
           * Falls back to a spoken name when the caller renders no visible
           * label. An unnamed combobox is announced as "edit text" and nothing
           * more, which tells you it exists and not what it is for.
           */
          aria-label={label ? undefined : 'Search topics'}
          type="text"
          value={search}
          placeholder={
            loading
              ? 'Loading topics...'
              : atLimit
                ? `Maximum ${max} topics selected`
                : placeholder
          }
          disabled={loading || atLimit}
          onChange={(e) => {
            setSearch(e.target.value)
            setOpen(true)
            setHighlighted(0)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          // Tells assistive technology this input drives a popup list
          role="combobox"
          aria-expanded={open}
          aria-controls={listboxId}
        />

        {open && !loading && !atLimit && (
          <div className="picker-dropdown" id={listboxId} role="listbox">
            {matches.length === 0 ? (
              <div className="picker-empty">No topics match "{search}"</div>
            ) : search ? (
              /* While searching, a flat list of matches reads better than
                 categories that mostly contain one item each. */
              matches.map((topic, index) => (
                <button
                  key={topic}
                  type="button"
                  role="option"
                  aria-selected={index === highlighted}
                  className={`picker-option ${index === highlighted ? 'highlighted' : ''}`}
                  onMouseEnter={() => setHighlighted(index)}
                  onClick={() => add(topic)}
                >
                  {topic}
                </button>
              ))
            ) : (
              /* With no search term, show everything grouped by category */
              Object.entries(byCategory).map(([category, list]) => {
                const available = list.filter((t) => !selected.includes(t.displayName))
                if (available.length === 0) return null

                return (
                  <div key={category} className="picker-group">
                    <div className="picker-group-label">{category}</div>
                    {available.map((topic) => (
                      <button
                        key={topic.name}
                        type="button"
                        role="option"
                        aria-selected={false}
                        className="picker-option"
                        onClick={() => add(topic.displayName)}
                      >
                        {topic.displayName}
                      </button>
                    ))}
                  </div>
                )
              })
            )}
          </div>
        )}
      </div>
    </div>
  )
}
