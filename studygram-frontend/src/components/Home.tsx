import type { Page } from '../types'

/*
 * Home - The landing page for logged-out visitors
 *
 * This is the first thing anyone sees, so it has one job: explain what
 * StudyGram is and give a single obvious next step.
 */
export function Home({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const features = [
    {
      icon: '🎯',
      title: 'Ask, and get answered',
      body: 'Ask a question and get an instant AI answer, while real people answer it properly.',
    },
    {
      icon: '🕶️',
      title: 'Ask anything, anonymously',
      body: 'Post without your name attached. Your identity is stripped on the server, not just hidden in the page.',
    },
    {
      icon: '🎓',
      title: 'An AI study assistant',
      body: 'Explain a concept, generate practice questions, or summarize your notes without leaving the app.',
    },
    {
      icon: '🤝',
      title: 'Find your crew',
      body: 'Connect with people learning the same things, and follow what they are working on.',
    },
  ]

  return (
    <div className="home">
      <section className="hero">
        <h1>
          Learn in public.
          <br />
          <span className="accent">Without the noise.</span>
        </h1>

        <p className="hero-sub">
          StudyGram is a social network for students, built around what you are
          learning rather than what you are doing. Share progress, ask the
          questions you are embarrassed to ask, and find people studying the same
          thing.
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
    </div>
  )
}
