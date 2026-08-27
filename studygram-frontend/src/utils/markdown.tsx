import type { ReactNode } from 'react'

/*
 * A small Markdown renderer for AI answers.
 *
 * WHY NOT A LIBRARY
 *
 * The AI replies in Markdown - headings, bold, bullet lists, code blocks - and
 * rendering that as plain text shows the reader literal ## and ** symbols.
 *
 * Pulling in a full Markdown library would work, but it is a large dependency
 * for the handful of constructs a language model actually produces, and most of
 * them can emit raw HTML, which means thinking carefully about sanitisation.
 *
 * WHY THIS IS SAFE
 *
 * This never produces HTML. It returns React ELEMENTS, and React escapes every
 * string it renders as a text node. There is no dangerouslySetInnerHTML
 * anywhere, so even if a model emitted <script>alert(1)</script> the reader
 * would simply see those characters on the page.
 *
 * That is the whole reason for building it this way rather than converting
 * Markdown to an HTML string and injecting it - the latter is how XSS gets in.
 */

/*
 * INLINE FORMATTING: **bold**, *italic*, `code`
 *
 * Splitting on a regex with a capture group keeps the delimiters in the
 * resulting array, so the matched runs can be turned into elements while the
 * text between them passes through untouched.
 */
function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`)/g)

  return parts.filter(Boolean).map((part, i) => {
    const key = `${keyPrefix}-${i}`

    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={key}>{part.slice(2, -2)}</strong>
    }

    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return <code key={key}>{part.slice(1, -1)}</code>
    }

    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) {
      return <em key={key}>{part.slice(1, -1)}</em>
    }

    return <span key={key}>{part}</span>
  })
}

/*
 * BLOCK STRUCTURE
 *
 * Walks the text line by line, because block elements are line-based: a heading
 * is a line starting with #, a list is a run of consecutive lines starting with
 * a bullet, and a code fence runs until the closing fence.
 *
 * Consecutive list items have to be gathered into a single <ul>, which is why
 * the loop keeps a buffer rather than emitting one element per line.
 */
export function renderMarkdown(text: string): ReactNode {
  if (!text) return null

  const lines = text.split('\n')
  const blocks: ReactNode[] = []

  let listBuffer: string[] = []
  let listOrdered = false
  let codeBuffer: string[] = []
  let inCodeFence = false

  function flushList() {
    if (listBuffer.length === 0) return

    const items = listBuffer.map((item, i) => (
      <li key={i}>{renderInline(item, `li-${blocks.length}-${i}`)}</li>
    ))

    blocks.push(
      listOrdered ? (
        <ol key={`b${blocks.length}`}>{items}</ol>
      ) : (
        <ul key={`b${blocks.length}`}>{items}</ul>
      ),
    )

    listBuffer = []
  }

  function flushCode() {
    if (codeBuffer.length === 0) return
    blocks.push(
      <pre key={`b${blocks.length}`}>
        <code>{codeBuffer.join('\n')}</code>
      </pre>,
    )
    codeBuffer = []
  }

  for (const rawLine of lines) {
    const line = rawLine.trimEnd()

    /* Code fences swallow everything until the closing ``` */
    if (line.trimStart().startsWith('```')) {
      if (inCodeFence) {
        flushCode()
        inCodeFence = false
      } else {
        flushList()
        inCodeFence = true
      }
      continue
    }

    if (inCodeFence) {
      codeBuffer.push(rawLine)
      continue
    }

    /* Blank line closes any open list and separates paragraphs. */
    if (line.trim() === '') {
      flushList()
      continue
    }

    /* Headings: #, ##, ### - all rendered small, since an answer sits inside
       a card and must not shout louder than the page around it. */
    const heading = line.match(/^(#{1,6})\s+(.*)$/)
    if (heading) {
      flushList()
      blocks.push(
        <h4 key={`b${blocks.length}`} className="md-heading">
          {renderInline(heading[2], `h-${blocks.length}`)}
        </h4>,
      )
      continue
    }

    /* Horizontal rule */
    if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
      flushList()
      blocks.push(<hr key={`b${blocks.length}`} />)
      continue
    }

    /* Bullet list: -, * or • */
    const bullet = line.match(/^\s*[-*•]\s+(.*)$/)
    if (bullet) {
      if (listOrdered) flushList()
      listOrdered = false
      listBuffer.push(bullet[1])
      continue
    }

    /* Numbered list: 1. 2. 3. */
    const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/)
    if (numbered) {
      if (!listOrdered && listBuffer.length > 0) flushList()
      listOrdered = true
      listBuffer.push(numbered[1])
      continue
    }

    /* Anything else is a paragraph. */
    flushList()
    blocks.push(
      <p key={`b${blocks.length}`}>{renderInline(line, `p-${blocks.length}`)}</p>,
    )
  }

  /* Close anything still open when the text ends. */
  flushList()
  flushCode()

  return <div className="markdown">{blocks}</div>
}
