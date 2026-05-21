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
      <div className="mx-auto max-w-md px-4 py-10">
        <h1 className="text-2xl font-semibold">Check your email</h1>
        <p className="mt-3 text-sm text-slate-600">
          If an account exists for <span className="font-medium">{email}</span>, we sent a
          password reset link. The link expires in 30 minutes.
        </p>
        <p className="mt-6 text-sm">
          <Link to="/login" className="font-medium text-blue-600 hover:underline">
            Back to log in
          </Link>
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md px-4 py-10">
      <h1 className="text-2xl font-semibold">Forgot your password?</h1>
      <p className="mt-2 text-sm text-slate-600">
        Enter your email and we'll send a reset link.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <label className="block text-sm">
          <span className="font-medium text-slate-700">Email</span>
          <input
            type="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-600 focus:outline-none"
          />
        </label>

        {error && <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</div>}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-blue-600 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {submitting ? 'Sending...' : 'Send reset link'}
        </button>

        <p className="text-center text-sm">
          <Link to="/login" className="text-slate-600 hover:underline">Back to log in</Link>
        </p>
      </form>
    </div>
  )
}
