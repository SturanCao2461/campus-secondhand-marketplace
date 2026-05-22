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
      <main className="mx-auto max-w-md px-4 py-12">
        <Spinner label="Verifying your email…" />
      </main>
    )
  }

  if (status === 'success') {
    return (
      <main className="mx-auto max-w-md px-4 py-12 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-green-100">
          <svg
            className="h-6 w-6 text-green-600"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h1 className="text-2xl font-semibold text-slate-900">Email verified</h1>
        <p className="mt-2 text-sm text-slate-500">
          Thanks. You can now post listings and message other students.
        </p>
        <Link
          to="/"
          className="mt-6 inline-block rounded-md bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
        >
          Continue to home
        </Link>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-md px-4 py-12 text-center">
      <h1 className="text-2xl font-semibold text-slate-900">Verification failed</h1>
      <div className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-700">{errorMessage}</div>
      <p className="mt-4 text-sm text-slate-500">
        If your link expired, log in and click <strong>Resend verification</strong> on your account
        page.
      </p>
      <Link
        to="/me"
        className="mt-6 inline-block rounded-md border border-slate-300 px-4 py-2 text-sm hover:bg-slate-50"
      >
        Go to your account
      </Link>
    </main>
  )
}
