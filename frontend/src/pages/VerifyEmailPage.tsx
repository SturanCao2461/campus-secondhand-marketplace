import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { api, ApiError } from '../api/apiClient'
import { Spinner } from '../components/Spinner'

type Status = 'loading' | 'success' | 'failed'

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [status, setStatus] = useState<Status>('loading')
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    if (!token) {
      setStatus('failed')
      setErrorMessage('Missing verification token. Check the link in your email.')
      return
    }
    api
      .post('/api/auth/verify-email', { token })
      .then(() => setStatus('success'))
      .catch((err: ApiError) => {
        setStatus('failed')
        setErrorMessage(err.message ?? 'This verification link is invalid or has expired.')
      })
  }, [token])

  if (status === 'loading') {
    return (
      <main className="mx-auto max-w-md px-6 py-12">
        <Spinner label="Verifying your email…" />
      </main>
    )
  }

  if (status === 'success') {
    return (
      <main className="mx-auto max-w-md px-6 py-12 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-sage/10">
          <svg
            className="h-6 w-6 text-sage"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h1 className="text-3xl font-bold text-plum tracking-tight">Email verified</h1>
        <p className="mt-2 text-sm text-muted">
          Thanks. You can now post listings and message other students.
        </p>
        <Link
          to="/"
          className="mt-6 inline-block rounded-full bg-plum px-6 py-2.5 text-sm text-surface font-bold hover:bg-ink transition-colors shadow-button"
        >
          Continue to home
        </Link>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-md px-6 py-12 text-center">
      <h1 className="text-3xl font-bold text-plum tracking-tight">Verification failed</h1>
      <div className="mt-3 rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error">
        {errorMessage}
      </div>
      <p className="mt-4 text-sm text-muted">
        If your link expired, log in and click{' '}
        <strong className="text-plum">Resend verification</strong> on your account page.
      </p>
      <Link
        to="/me"
        className="mt-6 inline-block rounded-full border border-border-soft px-6 py-2.5 text-sm text-muted hover:text-coral transition-colors"
      >
        Go to your account
      </Link>
    </main>
  )
}
