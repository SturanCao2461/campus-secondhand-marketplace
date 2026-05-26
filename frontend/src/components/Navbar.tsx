import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useUnreadCount } from '../hooks/useUnreadCount'

export function Navbar() {
  const { user, logout, loading } = useAuth()
  const nav = useNavigate()
  const unread = useUnreadCount(!!user)

  const onLogout = async () => {
    await logout()
    nav('/')
  }

  return (
    <header className="border-b border-border-soft bg-card/80 backdrop-blur">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link to="/" className="text-lg font-bold text-plum tracking-tight">
          Campus Marketplace
        </Link>
        <nav className="flex items-center gap-5 text-sm font-medium">
          <Link to="/browse" className="text-plum hover:text-coral transition-colors">
            Browse
          </Link>
          {!loading && user && (
            <>
              <Link
                to="/conversations"
                className="relative text-plum hover:text-coral transition-colors"
              >
                Messages
                {unread > 0 && (
                  <span className="absolute -top-1.5 -right-3 min-w-[18px] h-[18px] rounded-full bg-coral text-card text-[10px] font-semibold flex items-center justify-center px-1">
                    {unread > 99 ? '99+' : unread}
                  </span>
                )}
              </Link>
              <Link to="/listings/mine" className="text-plum hover:text-coral transition-colors">
                My Listings
              </Link>
              <span className="text-muted">Hi, {user.nickname}</span>
              <button
                onClick={onLogout}
                className="rounded-full border border-border-soft px-4 py-1.5 text-plum hover:bg-surface transition-colors"
              >
                Log out
              </button>
            </>
          )}
          {!loading && !user && (
            <>
              <Link to="/login" className="text-plum hover:text-coral transition-colors">
                Log in
              </Link>
              <Link
                to="/register"
                className="rounded-full bg-plum px-4 py-1.5 text-surface font-semibold hover:bg-ink transition-colors shadow-button"
              >
                Sign up
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
