import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/apiClient'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await api.post('/api/auth/forgot-password', { email })
      setSubmitted(true)
    } catch (e) {
      if (e instanceof ApiError) setError(e.message)
      else setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (submitted) {
    return (
      <div className="mx-auto max-w-md px-6 py-12">
        <h1 className="text-3xl font-bold text-plum tracking-tight">Check your email</h1>
        <p className="mt-3 text-muted text-sm">
          If an account exists for <span className="text-plum">{email}</span>, we sent a password
          reset link. The link expires in 30 minutes.
        </p>
        <p className="mt-6 text-sm">
          <Link to="/login" className="font-semibold text-coral hover:underline">
            Back to log in
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <h1 className="text-3xl font-bold text-plum tracking-tight">Forgot your password?</h1>
      <p className="mt-2 text-sm text-muted">Enter your email and we'll send a reset link.</p>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        <label className="block text-sm">
          <span className="font-semibold text-plum">Email</span>
          <input
            type="email"
            name="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="mt-1 block w-full rounded-card border border-border-soft bg-card px-3 py-2 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20"
          />
        </label>

        {error && (
          <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error">
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-full bg-plum py-2.5 text-surface font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button"
        >
          {submitting ? 'Sending...' : 'Send reset link'}
        </button>

        <p className="text-center text-sm">
          <Link to="/login" className="text-muted hover:text-coral transition-colors">
            Back to log in
          </Link>
        </p>
      </form>
    </div>
  )
}
