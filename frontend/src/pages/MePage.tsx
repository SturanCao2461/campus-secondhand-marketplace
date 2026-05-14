import { useAuth } from '../auth/useAuth'

export function MePage() {
  const { user } = useAuth()
  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <h1 className="text-2xl font-semibold">Your account</h1>
      <pre className="mt-4 rounded-md bg-slate-100 p-4 text-sm">
        {JSON.stringify(user, null, 2)}
      </pre>
    </div>
  )
}
