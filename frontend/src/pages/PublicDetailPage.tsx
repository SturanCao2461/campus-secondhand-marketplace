import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { listingsApi, type Listing } from '../api/listings'
import { conversationsApi } from '../api/conversations'
import { useAuth } from '../auth/useAuth'
import { ApiError } from '../api/apiClient'
import { Spinner } from '../components/Spinner'

const STATUS_CHIP: Record<string, string> = {
  AVAILABLE: 'bg-sage text-card',
  RESERVED: 'bg-mustard text-plum',
  SOLD: 'bg-plum/10 text-plum',
  REMOVED: 'bg-error/15 text-error',
}

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
    listingsApi
      .getDetail(Number(id))
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

  if (loading)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <Spinner />
      </main>
    )
  if (error)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <p className="text-muted mb-4">{error}</p>
        <Link to="/browse" className="text-coral font-semibold hover:underline">
          &larr; Back to Browse
        </Link>
      </main>
    )
  if (!listing) return null

  return (
    <main className="max-w-2xl mx-auto px-6 py-10">
      <img
        src={listing.imageUrl}
        alt={listing.title}
        className="w-full h-72 object-cover rounded-panel bg-mustard/30 mb-6 shadow-card"
      />

      <div className="flex justify-between items-start mb-4 gap-4">
        <h1 className="text-3xl font-bold text-plum tracking-tight leading-tight">
          {listing.title}
        </h1>
        <span className="text-2xl font-extrabold text-coral tracking-tight whitespace-nowrap">
          {listing.listingType === 'GIVEAWAY'
            ? 'Free'
            : listing.price != null
              ? `$${listing.price.toFixed(2)}`
              : ''}
        </span>
      </div>

      <div className="flex flex-wrap gap-2 mb-6">
        <span
          className={`text-xs font-bold uppercase tracking-wide px-3 py-1 rounded-full ${STATUS_CHIP[listing.status] ?? 'bg-plum/10 text-plum'}`}
        >
          {listing.status}
        </span>
        <span className="text-xs font-semibold px-3 py-1 rounded-full bg-card border border-border-soft text-plum">
          {listing.category.nameEn}
        </span>
        {listing.condition && (
          <span className="text-xs font-semibold px-3 py-1 rounded-full bg-card border border-border-soft text-plum">
            {listing.condition.replace('_', ' ')}
          </span>
        )}
        {listing.negotiable && (
          <span className="text-xs font-bold px-3 py-1 rounded-full bg-sage/20 text-sage">
            Negotiable
          </span>
        )}
      </div>

      <p className="text-plum mb-4 whitespace-pre-wrap leading-relaxed">{listing.description}</p>

      {listing.originalPrice != null && (
        <p className="text-sm text-muted mb-2">
          Original price: ${listing.originalPrice.toFixed(2)}
        </p>
      )}
      {listing.meetAt && <p className="text-sm text-muted mb-2">Meet at: {listing.meetAt}</p>}
      {listing.reasonForSelling && (
        <p className="text-sm text-muted mb-4">Reason: {listing.reasonForSelling}</p>
      )}

      <div className="border-t border-border-soft pt-4 mt-4">
        <p className="text-sm text-muted">
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
          className="mt-6 w-full rounded-full bg-plum px-6 py-3 text-surface text-base font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button"
        >
          {contacting ? 'Opening...' : 'Contact seller'}
        </button>
      )}

      <Link
        to="/browse"
        className="text-coral font-semibold hover:underline text-sm mt-6 inline-block"
      >
        &larr; Back to Browse
      </Link>
    </main>
  )
}
