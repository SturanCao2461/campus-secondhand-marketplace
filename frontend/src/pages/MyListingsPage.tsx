import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { listingsApi, type ListingSummary, type ListingStatus, type PagedListings } from '../api/listings'

export function MyListingsPage() {
  const [data, setData] = useState<PagedListings | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<ListingStatus | 'ALL'>('ALL')

  useEffect(() => {
    setLoading(true)
    setError('')
    listingsApi.listMine({ page, status: statusFilter, sort: 'CREATED_DESC' })
      .then(setData)
      .catch(() => setError('Failed to load listings.'))
      .finally(() => setLoading(false))
  }, [page, statusFilter])

  function statusBadge(s: ListingStatus) {
    const colors: Record<ListingStatus, string> = {
      AVAILABLE: 'bg-green-100 text-green-800',
      RESERVED: 'bg-yellow-100 text-yellow-800',
      SOLD: 'bg-blue-100 text-blue-800',
      REMOVED: 'bg-gray-100 text-gray-500',
    }
    return <span className={`text-xs px-2 py-0.5 rounded ${colors[s]}`}>{s}</span>
  }

  return (
    <main className="max-w-4xl mx-auto p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">My Listings</h1>
        <Link to="/listings/new"
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          + New Listing
        </Link>
      </div>

      <div className="flex gap-2 mb-4">
        {(['ALL', 'AVAILABLE', 'RESERVED', 'SOLD', 'REMOVED'] as const).map(s => (
          <button key={s} onClick={() => { setStatusFilter(s); setPage(0) }}
            className={`px-3 py-1 rounded text-sm ${statusFilter === s ? 'bg-blue-600 text-white' : 'bg-gray-100'}`}>
            {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="border rounded-lg overflow-hidden animate-pulse">
              <div className="w-full h-40 bg-gray-200" />
              <div className="p-3 space-y-2">
                <div className="h-4 bg-gray-200 rounded w-3/4" />
                <div className="h-3 bg-gray-200 rounded w-1/2" />
              </div>
            </div>
          ))}
        </div>
      )}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && data && data.items.length === 0 && (
        <div className="text-center py-12 text-gray-500">
          <svg className="mx-auto h-16 w-16 text-gray-300 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
              d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
          </svg>
          <p className="text-lg mb-2">No listings yet</p>
          <p className="text-sm mb-4">Start selling by creating your first listing</p>
          <Link to="/listings/new"
            className="inline-block bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
            + Create Listing
          </Link>
        </div>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {data.items.map((item: ListingSummary) => (
              <Link key={item.id} to={`/listings/${item.id}`}
                className="border rounded-lg overflow-hidden hover:shadow-md transition-shadow">
                <img src={item.imageUrl} alt={item.title}
                  className="w-full h-40 object-cover bg-gray-100" />
                <div className="p-3">
                  <div className="flex justify-between items-start mb-1">
                    <h3 className="font-medium text-sm truncate">{item.title}</h3>
                    {statusBadge(item.status)}
                  </div>
                  <p className="text-sm text-gray-600">
                    {item.listingType === 'GIVEAWAY' ? 'Free' :
                      item.price != null ? `$${item.price.toFixed(2)}` : ''}
                  </p>
                  <p className="text-xs text-gray-400 mt-1">{item.category.nameEn}</p>
                </div>
              </Link>
            ))}
          </div>

          {data.totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <button onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1 border rounded disabled:opacity-30">
                Previous
              </button>
              <span className="px-3 py-1 text-sm text-gray-600">
                Page {page + 1} of {data.totalPages}
              </span>
              <button onClick={() => setPage(p => p + 1)}
                disabled={page >= data.totalPages - 1}
                className="px-3 py-1 border rounded disabled:opacity-30">
                Next
              </button>
            </div>
          )}
        </>
      )}
    </main>
  )
}
