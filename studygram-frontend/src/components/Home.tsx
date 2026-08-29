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
  const features = [
    {
      icon: '🎓',
      title: 'Your own AI assistant, without depending on it',
      body: 'Ask it anything, push back when it does not click, then get it to test you on what you covered. It helps you understand; it does not do the understanding for you.',
    },
    {
      icon: '🙋',
      title: 'Getting stuck? Can’t understand properly?',
      body: 'Ask real people who have been through the same thing, and anonymously, if you would rather not put your name to it.',
    },
    {
      icon: '🤝',
      title: 'Make your own crew',
      body: 'Find people studying what you study, and keep up with what they are working on.',
    },
    {
      icon: '☕',
      title: 'Take a break, a real one',
      body: 'Five minutes, then back to it. StudyGram will not let you get lost.',
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
          StudyGram puts it all in one place, so the only thing you focus on is
          focusing.
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
