import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function HomePage() {
  const { user, loading } = useAuth()

  return (
    <main className="relative overflow-hidden">
      <section className="relative glow-coral glow-sage mx-auto max-w-5xl px-6 py-24 sm:py-32">
        <div className="relative z-10 max-w-2xl">
          <p className="text-eyebrow text-coral mb-4" style={{ letterSpacing: '0.15em' }}>
            <span className="text-[11px] font-bold uppercase tracking-[0.15em]">
              For Waikato Students
            </span>
          </p>
          <h1 className="text-4xl sm:text-5xl font-extrabold text-plum tracking-tight leading-[1.05]">
            Find your
            <br />
            next thing.
          </h1>
          <p className="mt-5 text-base sm:text-lg text-muted font-medium max-w-lg">
            Buy and sell secondhand items with other University of Waikato students.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/browse"
              className="rounded-full bg-plum px-6 py-3 text-surface text-base font-bold hover:bg-ink transition-colors shadow-button"
            >
              Browse listings →
            </Link>
            {!loading &&
              (user ? (
                <Link
                  to="/listings/new"
                  className="rounded-full bg-card border border-mustard px-6 py-3 text-plum text-base font-semibold hover:bg-mustard/20 transition-colors"
                >
                  Sell an item
                </Link>
              ) : (
                <Link
                  to="/register"
                  className="rounded-full bg-card border border-mustard px-6 py-3 text-plum text-base font-semibold hover:bg-mustard/20 transition-colors"
                >
                  Sign up
                </Link>
              ))}
          </div>
        </div>
      </section>
    </main>
  )
}
