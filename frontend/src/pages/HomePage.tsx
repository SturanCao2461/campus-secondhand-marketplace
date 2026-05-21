import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function HomePage() {
  const { user, loading } = useAuth()

  return (
    <div className="mx-auto max-w-5xl px-4 py-16 text-center">
      <h1 className="text-4xl font-bold">Campus Secondhand Marketplace</h1>
      <p className="mt-3 text-lg text-slate-600">
        Buy and sell secondhand items with other University of Waikato students.
      </p>
      <div className="mt-8 flex justify-center gap-4">
        <Link
          to="/browse"
          className="bg-blue-600 text-white px-6 py-3 rounded-lg text-lg hover:bg-blue-700"
        >
          Browse Listings
        </Link>
        {!loading &&
          (user ? (
            <Link
              to="/listings/new"
              className="border border-slate-300 px-6 py-3 rounded-lg text-lg hover:bg-slate-50"
            >
              Sell an Item
            </Link>
          ) : (
            <Link
              to="/register"
              className="border border-slate-300 px-6 py-3 rounded-lg text-lg hover:bg-slate-50"
            >
              Sign Up
            </Link>
          ))}
      </div>
    </div>
  )
}
