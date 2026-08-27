import { useEffect, type RefObject } from 'react'

/*
 * useFocusTrap - Keep keyboard focus inside an open dialog
 *
 * WHY A MODAL NEEDS THIS
 *
 * A dialog is visually obvious: it covers everything, so a sighted user knows
 * the rest of the page is unavailable. Keyboard focus does not work that way.
 * Without help, pressing Tab inside a dialog eventually walks focus out of it
 * and into the page behind - which is still there in the accessibility tree,
 * just hidden under a coloured layer.
 *
 * For someone using a screen reader that is genuinely disorienting: they are
 * now reading a feed they cannot see, with no indication they have left the
 * thing they opened, and no obvious way back.
 *
 * So while the break overlay is open, focus cycles within it: Tab from the last
 * control returns to the first, Shift+Tab from the first goes to the last, and
 * Escape closes it - which is what every dialog on the web is expected to do.
 *
 * Focus is also RESTORED on close, back to whatever was focused before. Without
 * that, closing a dialog dumps a keyboard user at the top of the document and
 * they have to tab all the way back to where they were.
 */

/* Everything a user can normally reach with Tab. */
const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'textarea:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export function useFocusTrap(
  containerRef: RefObject<HTMLElement | null>,
  active: boolean,
  onEscape?: () => void,
) {
  useEffect(() => {
    if (!active) return

    /*
     * Captured into a plain const after the guard.
     *
     * TypeScript will not carry the null check into handleKeyDown below: that
     * is a hoisted function declaration, so as far as the compiler knows it
     * could run before the guard. Binding the narrowed value here makes it
     * unambiguous.
     */
    const el = containerRef.current
    if (!el) return

    /* Remember where focus was, so it can be handed back on close. */
    const previouslyFocused = document.activeElement as HTMLElement | null

    /*
     * Move focus into the dialog. Without this, focus stays on the button that
     * opened it - which is now behind the overlay - so the first Tab press
     * lands somewhere invisible.
     */
    const focusables = el.querySelectorAll<HTMLElement>(FOCUSABLE)
    focusables[0]?.focus()

    /*
     * An arrow function expression, not a `function` declaration.
     *
     * Declarations are hoisted, so TypeScript assumes they might run before the
     * null guard above and refuses to narrow `el` inside them. An expression is
     * created where it appears, after the guard, so the narrowing holds.
     */
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onEscape?.()
        return
      }

      if (event.key !== 'Tab') return

      /*
       * Re-query on every Tab rather than reusing the list from mount: the
       * dialog's contents change as the user moves between break activities,
       * and a stale list would trap focus on buttons that no longer exist.
       */
      const items = Array.from(
        el.querySelectorAll<HTMLElement>(FOCUSABLE),
      ).filter((node) => node.offsetParent !== null) // skip anything hidden

      if (items.length === 0) return

      const first = items[0]
      const last = items[items.length - 1]

      // Shift+Tab off the front wraps to the back.
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
        return
      }

      // Tab off the end wraps to the front.
      if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      // Hand focus back where it came from.
      previouslyFocused?.focus?.()
    }
  }, [containerRef, active, onEscape])
}
