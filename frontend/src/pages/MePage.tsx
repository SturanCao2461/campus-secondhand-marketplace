import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function MePage() {
  const { user } = useAuth()
  if (!user) return null

  const initial = user.nickname.charAt(0).toUpperCase()

  return (
    <main className="mx-auto max-w-2xl px-4 py-8">
      <h1 className="text-2xl font-bold mb-6">Your account</h1>

      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-4">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-blue-600 text-2xl font-semibold text-white shrink-0">
            {initial}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-xl font-semibold text-slate-900 truncate">{user.nickname}</h2>
            <p className="text-sm text-slate-500 truncate">{user.email}</p>
            <p className="text-xs text-slate-400 mt-1">Member #{user.id}</p>
          </div>
        </div>
      </div>

      <h2 className="mt-8 mb-3 text-sm font-medium text-slate-700">Quick actions</h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Link
          to="/listings/mine"
          className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm hover:bg-slate-50"
        >
          <div className="font-medium text-slate-900">My Listings</div>
          <div className="mt-0.5 text-xs text-slate-500">Manage your items</div>
        </Link>
        <Link
          to="/conversations"
          className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm hover:bg-slate-50"
        >
          <div className="font-medium text-slate-900">Messages</div>
          <div className="mt-0.5 text-xs text-slate-500">View conversations</div>
        </Link>
        <Link
          to="/listings/new"
          className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm hover:bg-slate-50"
        >
          <div className="font-medium text-slate-900">Sell an item</div>
          <div className="mt-0.5 text-xs text-slate-500">Create a listing</div>
        </Link>
      </div>
    </main>
  )
}
