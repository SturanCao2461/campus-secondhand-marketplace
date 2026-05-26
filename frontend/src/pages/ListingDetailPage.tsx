import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { listingsApi, type Listing, type ListingStatus } from '../api/listings'
import { ApiError } from '../api/apiClient'
import { useToast } from '../components/useToast'
import { Spinner } from '../components/Spinner'

const STATUS_CHIP: Record<ListingStatus, string> = {
  AVAILABLE: 'bg-sage text-card',
  RESERVED: 'bg-mustard text-plum',
  SOLD: 'bg-plum/10 text-plum',
  REMOVED: 'bg-error/15 text-error',
}

export function ListingDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const toast = useToast()
  const [listing, setListing] = useState<Listing | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    if (!id) return
    listingsApi
      .getOne(Number(id))
      .then(setListing)
      .catch(err => {
        if (err instanceof ApiError && err.code === 'LISTING_NOT_FOUND') {
          navigate('/listings/mine')
        } else {
          setError('Failed to load listing.')
        }
      })
      .finally(() => setLoading(false))
  }, [id, navigate])

  async function handleStatusChange(newStatus: ListingStatus) {
    if (!listing) return
    setActionError('')
    try {
      const updated = await listingsApi.changeStatus(listing.id, newStatus)
      setListing(updated)
      toast.success(`Status changed to ${newStatus}`)
    } catch (err) {
      if (err instanceof ApiError) setActionError(err.message)
    }
  }

  async function handleDelete() {
    if (!listing || !confirm('Are you sure you want to remove this listing?')) return
    try {
      await listingsApi.remove(listing.id)
      toast.success('Listing removed')
      navigate('/listings/mine')
    } catch (err) {
      if (err instanceof ApiError) setActionError(err.message)
    }
  }

  if (loading)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <Spinner />
      </main>
    )
  if (error)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error">
          {error}
        </div>
      </main>
    )
  if (!listing) return null

  const isRemoved = listing.status === 'REMOVED'
  const actionBtn = 'rounded-full px-4 py-1.5 text-sm font-semibold transition-colors'

  return (
    <main className="max-w-2xl mx-auto px-6 py-10">
      {isRemoved && (
        <div className="bg-error/10 border border-error/30 text-error px-4 py-2.5 rounded-card mb-4 text-sm font-medium">
          This listing has been removed.
        </div>
      )}

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
          className={`text-xs font-bold uppercase tracking-wide px-3 py-1 rounded-full ${STATUS_CHIP[listing.status]}`}
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

      {actionError && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {actionError}
        </div>
      )}

      {!isRemoved && (
        <div className="flex flex-wrap gap-2 mb-6">
          {listing.status === 'AVAILABLE' && (
            <>
              <button
                onClick={() => handleStatusChange('RESERVED')}
                className={`${actionBtn} bg-mustard text-plum hover:bg-mustard/80`}
              >
                Mark Reserved
              </button>
              <button
                onClick={() => handleStatusChange('SOLD')}
                className={`${actionBtn} bg-plum text-surface hover:bg-ink`}
              >
                Mark Sold
              </button>
            </>
          )}
          {listing.status === 'RESERVED' && (
            <>
              <button
                onClick={() => handleStatusChange('AVAILABLE')}
                className={`${actionBtn} bg-sage text-card hover:bg-sage/80`}
              >
                Back to Available
              </button>
              <button
                onClick={() => handleStatusChange('SOLD')}
                className={`${actionBtn} bg-plum text-surface hover:bg-ink`}
              >
                Mark Sold
              </button>
            </>
          )}
          {listing.status === 'SOLD' && (
            <button
              onClick={() => handleStatusChange('AVAILABLE')}
              className={`${actionBtn} bg-sage text-card hover:bg-sage/80`}
            >
              Relist
            </button>
          )}
          <Link
            to={`/listings/${listing.id}/edit`}
            className={`${actionBtn} bg-card border border-border-soft text-plum hover:bg-surface`}
          >
            Edit
          </Link>
          <button
            onClick={handleDelete}
            className={`${actionBtn} bg-error text-card hover:bg-error/80`}
          >
            Delete
          </button>
        </div>
      )}

      <Link to="/listings/mine" className="text-coral font-semibold hover:underline text-sm">
        &larr; Back to My Listings
      </Link>
    </main>
  )
}
