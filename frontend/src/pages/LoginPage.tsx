import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../api/apiClient'
import { PasswordInput } from '../components/PasswordInput'

export function LoginPage() {
  const { login } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
      const next = params.get('next') ?? '/'
      if (!next.startsWith('/')) {
        nav('/')
      } else {
        nav(next)
      }
    } catch (e) {
      if (e instanceof ApiError) setError(e.message)
      else setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <h1 className="text-3xl font-bold text-plum tracking-tight">Welcome back</h1>

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
            autoComplete="email"
          />
        </label>

        <label className="block text-sm">
          <span className="font-semibold text-plum">Password</span>
          <PasswordInput
            name="password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
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
          {submitting ? 'Logging in...' : 'Log in'}
        </button>

        <div className="flex items-center justify-between text-sm">
          <Link to="/forgot-password" className="text-muted hover:text-coral transition-colors">
            Forgot password?
          </Link>
          <Link to="/register" className="font-semibold text-coral hover:underline">
            Create an account
          </Link>
        </div>
      </form>
    </div>
  )
}
