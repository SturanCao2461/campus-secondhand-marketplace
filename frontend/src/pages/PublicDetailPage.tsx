import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { listingsApi, type Listing } from '../api/listings'
import { conversationsApi } from '../api/conversations'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../api/apiClient'
import { Spinner } from '../components/Spinner'

export function PublicDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { user } = useAuth()
  const nav = useNavigate()
  const [listing, setListing] = useState<Listing | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [contacting, setContacting] = useState(false)

  useEffect(() => {
    if (!id) return
    listingsApi.getDetail(Number(id))
      .then(setListing)
      .catch(err => {
        if (err instanceof ApiError && err.code === 'LISTING_NOT_FOUND') {
          setError('This listing is no longer available.')
        } else {
          setError('Failed to load listing.')
        }
      })
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <main className="max-w-2xl mx-auto p-6"><Spinner /></main>
  if (error) return (
    <main className="max-w-2xl mx-auto p-6">
      <p className="text-gray-600 mb-4">{error}</p>
      <Link to="/browse" className="text-blue-600 hover:underline">&larr; Back to Browse</Link>
    </main>
  )
  if (!listing) return null

  return (
    <main className="max-w-2xl mx-auto p-6">
      <img src={listing.imageUrl} alt={listing.title}
        className="w-full h-64 object-cover rounded-lg bg-gray-100 mb-4" />

      <div className="flex justify-between items-start mb-4">
        <h1 className="text-2xl font-bold">{listing.title}</h1>
        <span className="text-lg font-semibold text-blue-600">
          {listing.listingType === 'GIVEAWAY' ? 'Free' :
            listing.price != null ? `$${listing.price.toFixed(2)}` : ''}
        </span>
      </div>

      <div className="flex gap-2 mb-4">
        <span className="text-xs bg-gray-100 px-2 py-1 rounded">{listing.status}</span>
        <span className="text-xs bg-gray-100 px-2 py-1 rounded">{listing.category.nameEn}</span>
        {listing.condition && (
          <span className="text-xs bg-gray-100 px-2 py-1 rounded">{listing.condition.replace('_', ' ')}</span>
        )}
        {listing.negotiable && (
          <span className="text-xs bg-green-100 text-green-700 px-2 py-1 rounded">Negotiable</span>
        )}
      </div>

      <p className="text-gray-700 mb-4 whitespace-pre-wrap">{listing.description}</p>

      {listing.originalPrice != null && (
        <p className="text-sm text-gray-500 mb-2">Original price: ${listing.originalPrice.toFixed(2)}</p>
      )}
      {listing.meetAt && <p className="text-sm text-gray-500 mb-2">Meet at: {listing.meetAt}</p>}
      {listing.reasonForSelling && (
        <p className="text-sm text-gray-500 mb-4">Reason: {listing.reasonForSelling}</p>
      )}

      <div className="border-t pt-4 mt-4">
        <p className="text-sm text-gray-500">
          Listed on {new Date(listing.createdAt).toLocaleDateString()}
        </p>
      </div>

      {user && user.id !== listing.ownerId && (
        <button
          onClick={async () => {
            setContacting(true)
            try {
              const conv = await conversationsApi.create(listing.id)
              nav(`/conversations/${conv.id}`)
            } catch {
              setContacting(false)
            }
          }}
          disabled={contacting}
          className="mt-4 w-full rounded-lg bg-blue-600 px-4 py-2.5 text-white font-medium hover:bg-blue-700 disabled:opacity-50"
        >
          {contacting ? 'Opening...' : 'Contact seller'}
        </button>
      )}

      <Link to="/browse" className="text-blue-600 hover:underline text-sm mt-4 inline-block">
        &larr; Back to Browse
      </Link>
    </main>
  )
}
