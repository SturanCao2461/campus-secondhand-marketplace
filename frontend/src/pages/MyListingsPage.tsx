import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import {
  listingsApi,
  type ListingSummary,
  type ListingStatus,
  type PagedListings,
} from '../api/listings'
import { Pagination } from '../components/Pagination'

const STATUS_CHIP: Record<ListingStatus, string> = {
  AVAILABLE: 'bg-sage text-card',
  RESERVED: 'bg-mustard text-plum',
  SOLD: 'bg-plum/10 text-plum',
  REMOVED: 'bg-error/15 text-error',
}

export function MyListingsPage() {
  const [data, setData] = useState<PagedListings | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<ListingStatus | 'ALL'>('ALL')

  useEffect(() => {
    setLoading(true)
    setError('')
    listingsApi
      .listMine({ page, status: statusFilter, sort: 'CREATED_DESC' })
      .then(setData)
      .catch(() => setError('Failed to load listings.'))
      .finally(() => setLoading(false))
  }, [page, statusFilter])

  function statusBadge(s: ListingStatus) {
    return (
      <span
        className={`text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded-full ${STATUS_CHIP[s]}`}
      >
        {s}
      </span>
    )
  }

  return (
    <main className="max-w-4xl mx-auto px-6 py-10">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-3xl font-bold text-plum tracking-tight">My Listings</h1>
        <Link
          to="/listings/new"
          className="rounded-full bg-plum text-surface px-5 py-2 font-semibold hover:bg-ink transition-colors shadow-button"
        >
          + New Listing
        </Link>
      </div>

      <div className="flex gap-2 mb-6 flex-wrap">
        {(['ALL', 'AVAILABLE', 'RESERVED', 'SOLD', 'REMOVED'] as const).map(s => (
          <button
            key={s}
            onClick={() => {
              setStatusFilter(s)
              setPage(0)
            }}
            className={`px-4 py-1.5 rounded-full text-sm font-semibold transition-colors ${
              statusFilter === s
                ? 'bg-plum text-surface'
                : 'bg-card border border-border-soft text-plum hover:bg-surface'
            }`}
          >
            {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="bg-card rounded-card overflow-hidden shadow-card animate-pulse">
              <div className="w-full h-40 bg-mustard/30" />
              <div className="p-4 space-y-2">
                <div className="h-4 bg-coral/20 rounded w-3/4" />
                <div className="h-3 bg-coral/10 rounded w-1/2" />
              </div>
            </div>
          ))}
        </div>
      )}
      {error && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {error}
        </div>
      )}

      {!loading && !error && data && data.items.length === 0 && (
        <div className="text-center py-16 text-muted">
          <svg
            className="mx-auto h-16 w-16 text-coral/40 mb-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"
            />
          </svg>
          <p className="text-lg font-semibold text-plum mb-2">No listings yet</p>
          <p className="text-sm mb-4">Start selling by creating your first listing</p>
          <Link
            to="/listings/new"
            className="inline-block rounded-full bg-plum text-surface px-5 py-2 font-semibold hover:bg-ink transition-colors shadow-button"
          >
            + Create Listing
          </Link>
        </div>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {data.items.map((item: ListingSummary) => (
              <Link
                key={item.id}
                to={`/listings/${item.id}`}
                className="bg-card rounded-card overflow-hidden shadow-card hover:-translate-y-0.5 hover:shadow-panel transition-all"
              >
                <img
                  src={item.imageUrl}
                  alt={item.title}
                  className="w-full h-40 object-cover bg-mustard/30"
                />
                <div className="p-4">
                  <div className="flex justify-between items-start mb-1 gap-2">
                    <h3 className="font-semibold text-sm text-plum truncate">{item.title}</h3>
                    {statusBadge(item.status)}
                  </div>
                  <p className="text-lg font-extrabold text-coral tracking-tight">
                    {item.listingType === 'GIVEAWAY'
                      ? 'Free'
                      : item.price != null
                        ? `$${item.price.toFixed(2)}`
                        : ''}
                  </p>
                  <p className="text-xs text-muted mt-1 font-medium">{item.category.nameEn}</p>
                </div>
              </Link>
            ))}
          </div>

          <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </main>
  )
}
