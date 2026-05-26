import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../api/apiClient'
import { PasswordInput } from '../components/PasswordInput'

export function RegisterPage() {
  const { register } = useAuth()
  const nav = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setError("Passwords don't match.")
      return
    }
    setSubmitting(true)
    try {
      await register(email, password, nickname)
      nav('/')
    } catch (e) {
      if (e instanceof ApiError) setError(e.message)
      else setError('Something went wrong. Try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <h1 className="text-3xl font-bold text-plum tracking-tight">Create your account</h1>
      <p className="mt-2 text-sm text-muted">
        Use a real email — you'll need it to reset your password later.
      </p>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        <Field label="Email" hint="@students.waikato.ac.nz only">
          <input
            type="email"
            name="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className={inputCls}
            autoComplete="email"
          />
        </Field>

        <Field label="Password" hint="8-64 characters with at least one letter and one digit">
          <PasswordInput
            name="password"
            required
            minLength={8}
            maxLength={64}
            value={password}
            onChange={e => setPassword(e.target.value)}
            autoComplete="new-password"
          />
        </Field>

        <Field label="Confirm Password">
          <PasswordInput
            name="confirmPassword"
            required
            value={confirm}
            onChange={e => setConfirm(e.target.value)}
            autoComplete="new-password"
          />
        </Field>

        <Field label="Nickname" hint="2-20 characters, shown publicly">
          <input
            type="text"
            name="nickname"
            required
            minLength={2}
            maxLength={20}
            value={nickname}
            onChange={e => setNickname(e.target.value)}
            className={inputCls}
          />
        </Field>

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
          {submitting ? 'Creating account...' : 'Create account'}
        </button>

        <p className="text-center text-sm text-muted">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-coral hover:underline">
            Log in
          </Link>
        </p>
      </form>
    </div>
  )
}

const inputCls =
  'mt-1 block w-full rounded-card border border-border-soft bg-card px-3 py-2 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20'

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="font-semibold text-plum">{label}</span>
      {children}
      {hint && <span className="text-xs text-muted mt-1 block">{hint}</span>}
    </label>
  )
}
