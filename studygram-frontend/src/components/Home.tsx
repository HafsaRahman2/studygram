import type { Page } from '../types'

/*
 * Home - The landing page
 *
 * The copy here is Hafsa's, lightly tidied - not written to sound like a
 * product. "You matter", "without depending on it", "where the only thing you
 * focus on is focusing" and "we got you" are hers, and they are the reason this
 * page sounds like a person rather than a marketing team.
 *
 * The one job of this page: say what StudyGram is, and give one obvious next
 * step.
 */
export function Home({ onNavigate }: { onNavigate: (page: Page) => void }) {
  /*
   * Four cards, written to the same shape on purpose.
   *
   * Every title starts with a verb, and every body is one sentence plus one
   * concrete detail. That is what makes them scan as a set instead of four
   * unrelated paragraphs, and it is why none of them runs to three clauses the
   * way the old ones did.
   *
   * Each detail is also quietly answering "why not just use the app I already
   * have?". Students who have been stuck on it too is why this is not Reddit.
   * Test you on what you covered is why it is not ChatGPT. The timer is why
   * taking a break here is different from just stopping.
   */
  const features = [
    {
      icon: '🎓',
      title: 'Ask your assistant',
      body: 'Get an answer straight away, ask follow-ups, then have it test you on what you covered.',
    },
    {
      icon: '🙋',
      title: 'Ask real people',
      body: 'Ask a question, or share something that worked. Real students answer, and you can stay anonymous.',
    },
    {
      icon: '🤝',
      title: 'Build your crew',
      body: 'Find people studying what you study, and keep up with what they’re working on.',
    },
    {
      icon: '☕',
      title: 'Take a break that ends',
      body: 'Five minutes, then back to it. The timer is the whole point.',
    },
  ]

  return (
    <div className="home">
      <section className="hero">
        <h1>
          You matter.
          <br />
          <span className="accent">So does your focus.</span>
        </h1>

        <p className="hero-sub">
          Getting distracted while studying? Jumping from one app to another just to
          find one answer, then somehow scrolling your feed for an hour without
          even realising?
        </p>

        <p className="hero-sub hero-emphasis">You’re not the only one.</p>

        <p className="hero-sub">
          One place for all of it. Find your answer and get back to work.
        </p>

        <div className="hero-actions">
          <button className="btn btn-large" onClick={() => onNavigate('signup')}>
            Get started
          </button>
          <button
            className="btn btn-large btn-secondary"
            onClick={() => onNavigate('login')}
          >
            Log in
          </button>
        </div>
      </section>

      <section className="features">
        {features.map((feature) => (
          <div key={feature.title} className="feature">
            <div className="feature-icon" aria-hidden="true">
              {feature.icon}
            </div>
            <h3>{feature.title}</h3>
            <p>{feature.body}</p>
          </div>
        ))}
      </section>

      <p className="home-closing">
        Keeping you on track while you ace your exams.
      </p>
    </div>
  )
}
