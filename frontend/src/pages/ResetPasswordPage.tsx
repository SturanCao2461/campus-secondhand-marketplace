import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/apiClient'
import { PasswordInput } from '../components/PasswordInput'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const nav = useNavigate()
  const token = params.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!token) {
    return (
      <div className="mx-auto max-w-md px-4 py-10">
        <h1 className="text-2xl font-semibold">Invalid link</h1>
        <p className="mt-3 text-sm text-slate-600">
          This reset link is missing a token. Request a new one.
        </p>
        <p className="mt-6 text-sm">
          <Link to="/forgot-password" className="font-medium text-slate-900 hover:underline">
            Request a new link
          </Link>
        </p>
      </div>
    )
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setError("Passwords don't match.")
      return
    }
    setSubmitting(true)
    try {
      await api.post('/api/auth/reset-password', { token, newPassword: password })
      nav('/login?reset=ok')
    } catch (e) {
      if (e instanceof ApiError) setError(e.message)
      else setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md px-4 py-10">
      <h1 className="text-2xl font-semibold">Set a new password</h1>

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <label className="block text-sm">
          <span className="font-medium text-slate-700">New password</span>
          <PasswordInput
            required
            minLength={8}
            maxLength={64}
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="new-password"
          />
          <span className="mt-1 block text-xs text-slate-500">
            8–64 characters with at least one letter and one digit
          </span>
        </label>

        <label className="block text-sm">
          <span className="font-medium text-slate-700">Confirm password</span>
          <PasswordInput
            required
            value={confirm}
            onChange={e => setConfirm(e.target.value)}
            autoComplete="new-password"
          />
        </label>

        {error && <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</div>}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-slate-900 py-2 text-white hover:bg-slate-800 disabled:opacity-50"
        >
          {submitting ? 'Updating...' : 'Update password'}
        </button>
      </form>
    </div>
  )
}
