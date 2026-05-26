import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { api, ApiError } from '../api/apiClient'

export function MePage() {
  const { user } = useAuth()
  const [resendStatus, setResendStatus] = useState<'idle' | 'sending' | 'sent' | 'error'>('idle')
  const [resendError, setResendError] = useState('')

  if (!user) return null

  const initial = user.nickname.charAt(0).toUpperCase()

  const handleResend = async () => {
    setResendStatus('sending')
    setResendError('')
    try {
      await api.post('/api/auth/resend-verification', {})
      setResendStatus('sent')
    } catch (err) {
      setResendStatus('error')
      setResendError(err instanceof ApiError ? err.message : 'Could not send verification email.')
    }
  }

  return (
    <main className="max-w-2xl mx-auto px-6 py-10">
      <h1 className="text-3xl font-bold text-plum tracking-tight mb-6">Your account</h1>

      <div className="bg-card rounded-panel shadow-card p-6">
        <div className="flex items-center gap-4">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-blue-600 text-2xl font-semibold text-white shrink-0">
            {initial}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-bold uppercase tracking-wide text-muted">Nickname</p>
            <h2 className="text-base font-semibold text-plum truncate">{user.nickname}</h2>
            <p className="text-xs font-bold uppercase tracking-wide text-muted mt-2">Email</p>
            <p className="text-base font-semibold text-plum truncate">{user.email}</p>
            <div className="mt-1.5 flex flex-wrap items-center gap-2">
              <span className="text-sm text-muted">Member #{user.id}</span>
              {user.emailVerified ? (
                <span className="inline-flex items-center gap-1 bg-sage/20 text-sage rounded-full px-3 py-0.5 text-xs font-bold uppercase">
                  <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  Email verified
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 bg-mustard/30 text-plum rounded-full px-3 py-0.5 text-xs font-bold uppercase">
                  Email not verified
                </span>
              )}
            </div>
          </div>
        </div>

        {!user.emailVerified && (
          <div className="mt-4 rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error">
            <p className="mb-2">
              Verify your email to post listings and message other students. Check your inbox for
              the link we sent when you signed up.
            </p>
            {resendStatus === 'sent' ? (
              <p className="rounded-card bg-sage/10 border border-sage/30 p-3 text-sm text-sage">Sent — check your inbox.</p>
            ) : (
              <button
                onClick={handleResend}
                disabled={resendStatus === 'sending'}
                className="rounded-full bg-plum px-4 py-1.5 text-surface font-semibold text-sm hover:bg-ink transition-colors disabled:opacity-50"
              >
                {resendStatus === 'sending' ? 'Sending…' : 'Resend verification email'}
              </button>
            )}
            {resendStatus === 'error' && (
              <p className="mt-1 text-xs text-error">{resendError}</p>
            )}
          </div>
        )}
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
