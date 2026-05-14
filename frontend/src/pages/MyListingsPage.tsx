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

      {loading && <p className="text-gray-500">Loading...</p>}
      {error && <p className="text-red-600">{error}</p>}

      {!loading && !error && data && data.items.length === 0 && (
        <div className="text-center py-12 text-gray-500">
          <p className="text-lg mb-2">No listings yet</p>
          <Link to="/listings/new" className="text-blue-600 hover:underline">
            Create your first listing
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
