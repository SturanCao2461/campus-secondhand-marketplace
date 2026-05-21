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
    <div className="mx-auto max-w-md px-4 py-10">
      <h1 className="text-2xl font-semibold">Welcome back</h1>

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <label className="block text-sm">
          <span className="font-medium text-slate-700">Email</span>
          <input
            type="email"
            name="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="mt-1 block w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-blue-600 focus:outline-none"
            autoComplete="email"
          />
        </label>

        <label className="block text-sm">
          <span className="font-medium text-slate-700">Password</span>
          <PasswordInput
            name="password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>

        {error && <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</div>}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-blue-600 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {submitting ? 'Logging in...' : 'Log in'}
        </button>

        <div className="flex items-center justify-between text-sm">
          <Link to="/forgot-password" className="text-slate-600 hover:underline">
            Forgot password?
          </Link>
          <Link to="/register" className="font-medium text-blue-600 hover:underline">
            Create an account
          </Link>
        </div>
      </form>
    </div>
  )
}
