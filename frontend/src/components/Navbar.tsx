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
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
        <Link to="/" className="text-lg font-semibold text-slate-900">
          Campus Marketplace
        </Link>
        <nav className="flex items-center gap-4 text-sm">
          <Link to="/browse" className="text-slate-700 hover:underline">Browse</Link>
          {!loading && user && (
            <>
              <Link to="/conversations" className="relative text-slate-700 hover:underline">
                Messages
                {unread > 0 && (
                  <span className="absolute -top-1.5 -right-3 min-w-[18px] h-[18px] rounded-full bg-blue-600 text-white text-[10px] flex items-center justify-center px-1">
                    {unread > 99 ? '99+' : unread}
                  </span>
                )}
              </Link>
              <Link to="/listings/mine" className="text-slate-700 hover:underline">My Listings</Link>
              <span className="text-slate-600">Hi, {user.nickname}</span>
              <button
                onClick={onLogout}
                className="rounded-md border border-slate-300 px-3 py-1 hover:bg-slate-50"
              >
                Log out
              </button>
            </>
          )}
          {!loading && !user && (
            <>
              <Link to="/login" className="text-slate-700 hover:underline">Log in</Link>
              <Link
                to="/register"
                className="rounded-md bg-blue-600 px-3 py-1 text-white hover:bg-blue-700"
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
