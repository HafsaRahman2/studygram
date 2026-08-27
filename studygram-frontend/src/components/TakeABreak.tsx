import { useEffect, useRef, useState } from 'react'
import { formatClock } from '../hooks/useBreak'
import { useFocusTrap } from '../hooks/useFocusTrap'
import type { BreakStatus } from '../types'

/*
 * TakeABreak - The full-screen break experience
 *
 * A DESIGN CONSTRAINT WORTH STATING OUT LOUD
 *
 * StudyGram's whole pitch is "a feed with no noise". A break feature that drops
 * students into an infinite scroll would contradict that, and worse, it would
 * not work: five minutes of scrolling does not end after five minutes, it ends
 * when you close the app.
 *
 * So every activity here is FINITE. The breathing exercise completes its
 * rounds. The stretch sequence runs out of stretches. Nothing here refills
 * itself, and nothing rewards staying longer. The break should run out of
 * things to do before the timer runs out.
 */

/* ------------------------------------------------------------- activities */

type ActivityId = 'breathe' | 'stretch' | 'eyes' | 'chill'

const ACTIVITIES: Array<{ id: ActivityId; label: string; icon: string }> = [
  { id: 'breathe', label: 'Breathe', icon: '🫧' },
  { id: 'stretch', label: 'Stretch', icon: '🙆' },
  { id: 'eyes', label: 'Rest your eyes', icon: '👀' },
  { id: 'chill', label: 'Just sit', icon: '🌙' },
]

/*
 * BOX BREATHING - in for 4, hold 4, out 4, hold 4.
 *
 * Used by people who need to calm down on demand, and it works because the
 * long exhale is what actually slows your heart rate. Four seconds a phase is
 * comfortable for most people without practice.
 */
const BREATHE_PHASES = [
  { label: 'Breathe in', seconds: 4, scale: 1 },
  { label: 'Hold', seconds: 4, scale: 1 },
  { label: 'Breathe out', seconds: 4, scale: 0.55 },
  { label: 'Hold', seconds: 4, scale: 0.55 },
]

function Breathe() {
  const [phaseIndex, setPhaseIndex] = useState(0)
  const [rounds, setRounds] = useState(0)

  const phase = BREATHE_PHASES[phaseIndex]

  useEffect(() => {
    const timer = setTimeout(() => {
      const next = (phaseIndex + 1) % BREATHE_PHASES.length
      setPhaseIndex(next)
      // A full round finishes when we wrap back to the start
      if (next === 0) setRounds((r) => r + 1)
    }, phase.seconds * 1000)

    return () => clearTimeout(timer)
  }, [phaseIndex, phase.seconds])

  return (
    <div className="activity breathe">
      <div
        className="breath-circle"
        style={{
          transform: `scale(${phase.scale})`,
          // The circle animates over exactly the phase duration, so the visual
          // and the instruction stay in step without a separate animation clock.
          transitionDuration: `${phase.seconds}s`,
        }}
      >
        <span>{phase.label}</span>
      </div>

      <p className="activity-caption">
        {rounds === 0
          ? 'Follow the circle. In through your nose, out through your mouth.'
          : `${rounds} round${rounds === 1 ? '' : 's'} done. Keep going, or switch to something else.`}
      </p>
    </div>
  )
}

/*
 * STRETCH - a fixed sequence that ends.
 *
 * Sitting still for an hour is what actually makes you ache, so these all
 * target the places studying hurts: neck, shoulders, wrists, back.
 */
const STRETCHES = [
  { name: 'Neck rolls', detail: 'Slowly, both directions. Do not force it.', seconds: 30 },
  { name: 'Shoulder shrugs', detail: 'Up to your ears, hold, drop.', seconds: 30 },
  { name: 'Wrist circles', detail: 'Both ways. Your typing hands earned this.', seconds: 30 },
  { name: 'Reach up tall', detail: 'Stand if you can. Lengthen your whole back.', seconds: 30 },
  { name: 'Twist side to side', detail: 'Slowly, from the waist.', seconds: 30 },
]

function Stretch() {
  const [index, setIndex] = useState(0)
  const [secondsLeft, setSecondsLeft] = useState(STRETCHES[0].seconds)
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (done) return

    const interval = setInterval(() => {
      setSecondsLeft((current) => {
        if (current > 1) return current - 1

        // This stretch is finished - advance, or end the sequence
        setIndex((i) => {
          const next = i + 1
          if (next >= STRETCHES.length) {
            setDone(true)
            return i
          }
          setSecondsLeft(STRETCHES[next].seconds)
          return next
        })

        return 0
      })
    }, 1000)

    return () => clearInterval(interval)
  }, [done])

  if (done) {
    return (
      <div className="activity">
        <div className="activity-icon" aria-hidden="true">
          ✨
        </div>
        <h3>That's the set</h3>
        <p className="activity-caption">
          Your shoulders will thank you. Sit back down whenever you are ready.
        </p>
      </div>
    )
  }

  const current = STRETCHES[index]

  return (
    <div className="activity">
      <div className="stretch-progress">
        {STRETCHES.map((s, i) => (
          <span key={s.name} className={`dot ${i < index ? 'done' : i === index ? 'current' : ''}`} />
        ))}
      </div>

      <div className="stretch-count">{secondsLeft}</div>
      <h3>{current.name}</h3>
      <p className="activity-caption">{current.detail}</p>
    </div>
  )
}

/*
 * REST YOUR EYES - the 20-20-20 rule.
 *
 * Every 20 minutes of screen time, look at something 20 feet away for 20
 * seconds. It is the one piece of screen-fatigue advice with actual optometric
 * backing, and it takes 20 seconds.
 */
function RestEyes() {
  const [secondsLeft, setSecondsLeft] = useState(20)

  useEffect(() => {
    if (secondsLeft === 0) return
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [secondsLeft])

  return (
    <div className="activity">
      {secondsLeft > 0 ? (
        <>
          <div className="eye-timer">{secondsLeft}</div>
          <h3>Look out a window</h3>
          <p className="activity-caption">
            Find something far away — across the room, down the street — and let
            your eyes settle on it. Do not look back at this screen yet.
          </p>
        </>
      ) : (
        <>
          <div className="activity-icon" aria-hidden="true">
            👌
          </div>
          <h3>Better</h3>
          <p className="activity-caption">
            That is the 20-20-20 rule: every 20 minutes, look 20 feet away for 20
            seconds. Worth doing even when you are not on a break.
          </p>
        </>
      )}
    </div>
  )
}

/*
 * JUST SIT - deliberately the emptiest option.
 *
 * Sometimes the most useful thing an app can do is stop asking for your
 * attention. One line, no timer, no interaction.
 */
function JustSit() {
  return (
    <div className="activity">
      <div className="activity-icon float" aria-hidden="true">
        🌙
      </div>
      <h3>Nothing to do here</h3>
      <p className="activity-caption">
        That is the point. Put the phone down, look away from the screen, and let
        the timer run out.
      </p>
    </div>
  )
}

/* ------------------------------------------------------------ main screen */

export function TakeABreak({
  status,
  remaining,
  onExtend,
  onEnd,
  onMinimize,
}: {
  status: BreakStatus
  remaining: number
  onExtend: () => void
  onEnd: () => void
  onMinimize: () => void
}) {
  const [activity, setActivity] = useState<ActivityId>('breathe')

  /*
   * Keyboard focus stays inside the overlay while it is open, and Escape
   * minimizes it. Without the trap, Tab would walk focus into the feed behind -
   * which is invisible but still reachable, and deeply confusing for anyone
   * navigating by keyboard or screen reader.
   */
  const dialogRef = useRef<HTMLDivElement>(null)
  useFocusTrap(dialogRef, true, onMinimize)

  /*
   * Warn when the break is nearly over, so going back to work is not a
   * surprise. Under a minute is late enough to matter and early enough to
   * finish what you are doing.
   */
  const almostDone = remaining <= 60

  return (
    <div
      className="break-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="break-heading"
      ref={dialogRef}
    >
      <div className="break-inner">
        <header className="break-head">
          <div>
            <span className="break-label" id="break-heading">
              Break time
            </span>

            {/*
              aria-live="off" on the ticking clock, deliberately.

              A screen reader announcing a new time every single second would
              make the break unusable - it would talk over everything else and
              never stop. The remaining time is available on demand instead.
            */}
            <div
              className={`break-clock ${almostDone ? 'ending' : ''}`}
              aria-live="off"
              aria-label={`${Math.floor(remaining / 60)} minutes ${remaining % 60} seconds remaining`}
            >
              {formatClock(remaining)}
            </div>
          </div>

          <button className="link" onClick={onMinimize}>
            Minimize<span className="sr-only"> break screen (or press Escape)</span>
          </button>
        </header>

        <nav className="break-activities">
          {ACTIVITIES.map((item) => (
            <button
              key={item.id}
              className={`break-activity ${activity === item.id ? 'active' : ''}`}
              onClick={() => setActivity(item.id)}
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <div className="break-stage">
          {/*
            The `key` forces React to build a fresh component when you switch
            activities, rather than reusing the old one. Without it, swapping
            from Stretch to Breathe and back would resume the stretch sequence
            wherever it left off, timers and all.
          */}
          {activity === 'breathe' && <Breathe key="breathe" />}
          {activity === 'stretch' && <Stretch key="stretch" />}
          {activity === 'eyes' && <RestEyes key="eyes" />}
          {activity === 'chill' && <JustSit key="chill" />}
        </div>

        <footer className="break-foot">
          {status.canExtend ? (
            <button className="btn btn-secondary" onClick={onExtend}>
              +5 more minutes
            </button>
          ) : (
            <span className="muted">Extension used for this break</span>
          )}

          <button className="btn" onClick={onEnd}>
            I'm ready
          </button>
        </footer>

        <p className="break-note">
          Ending early starts your next hour early too — you are not penalised
          for coming back sooner.
        </p>
      </div>
    </div>
  )
}

/* ------------------------------------------------------- the entry button */

/*
 * BreakButton - lives in the header and reflects all three states.
 *
 * Deliberately shows the cooldown rather than hiding the button. "Next break in
 * 43 min" tells you the feature exists and when it returns; a button that
 * vanishes just looks broken.
 */
export function BreakButton({
  status,
  remaining,
  onOpen,
}: {
  status: BreakStatus | null
  remaining: number
  onOpen: () => void
}) {
  if (!status) return null

  if (status.state === 'ACTIVE') {
    return (
      <button className="break-btn active" onClick={onOpen}>
        <span aria-hidden="true">🫧</span>
        {formatClock(remaining)}
      </button>
    )
  }

  if (status.state === 'COOLDOWN') {
    const minutes = Math.ceil(remaining / 60)
    return (
      <button
        className="break-btn cooling"
        disabled
        title={`You have taken ${status.breaksToday} break${status.breaksToday === 1 ? '' : 's'} today`}
      >
        <span aria-hidden="true">⏳</span>
        {minutes < 60 ? `${minutes}m` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`}
      </button>
    )
  }

  return (
    <button className="break-btn" onClick={onOpen}>
      <span aria-hidden="true">☕</span>
      Take a break
    </button>
  )
}
