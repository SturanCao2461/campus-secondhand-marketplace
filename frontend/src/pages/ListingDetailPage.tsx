import { useState, useEffect } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { listingsApi, type Listing, type ListingStatus } from '../api/listings'
import { ApiError } from '../api/apiClient'
import { useToast } from '../components/Toast'
import { Spinner } from '../components/Spinner'

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
    listingsApi.getOne(Number(id))
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

  if (loading) return <main className="max-w-2xl mx-auto p-6"><Spinner /></main>
  if (error) return <main className="max-w-2xl mx-auto p-6"><p className="text-red-600">{error}</p></main>
  if (!listing) return null

  const isRemoved = listing.status === 'REMOVED'

  return (
    <main className="max-w-2xl mx-auto p-6">
      {isRemoved && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-2 rounded mb-4">
          This listing has been removed.
        </div>
      )}

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

      {actionError && <p className="text-red-600 mb-4">{actionError}</p>}

      {!isRemoved && (
        <div className="flex flex-wrap gap-2 mb-4">
          {listing.status === 'AVAILABLE' && (
            <>
              <button onClick={() => handleStatusChange('RESERVED')}
                className="px-3 py-1 bg-yellow-500 text-white rounded text-sm">Mark Reserved</button>
              <button onClick={() => handleStatusChange('SOLD')}
                className="px-3 py-1 bg-blue-500 text-white rounded text-sm">Mark Sold</button>
            </>
          )}
          {listing.status === 'RESERVED' && (
            <>
              <button onClick={() => handleStatusChange('AVAILABLE')}
                className="px-3 py-1 bg-green-500 text-white rounded text-sm">Back to Available</button>
              <button onClick={() => handleStatusChange('SOLD')}
                className="px-3 py-1 bg-blue-500 text-white rounded text-sm">Mark Sold</button>
            </>
          )}
          {listing.status === 'SOLD' && (
            <button onClick={() => handleStatusChange('AVAILABLE')}
              className="px-3 py-1 bg-green-500 text-white rounded text-sm">Relist</button>
          )}
          <Link to={`/listings/${listing.id}/edit`}
            className="px-3 py-1 bg-gray-200 rounded text-sm">Edit</Link>
          <button onClick={handleDelete}
            className="px-3 py-1 bg-red-500 text-white rounded text-sm">Delete</button>
        </div>
      )}

      <Link to="/listings/mine" className="text-blue-600 hover:underline text-sm">
        &larr; Back to My Listings
      </Link>
    </main>
  )
}
