import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function Navbar() {
  const { user, logout } = useAuth()
  const nav = useNavigate()

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
          {user ? (
            <>
              <span className="text-slate-600">Hi, {user.nickname}</span>
              <button
                onClick={onLogout}
                className="rounded-md border border-slate-300 px-3 py-1 hover:bg-slate-50"
              >
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="text-slate-700 hover:underline">Log in</Link>
              <Link
                to="/register"
                className="rounded-md bg-slate-900 px-3 py-1 text-white hover:bg-slate-800"
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
