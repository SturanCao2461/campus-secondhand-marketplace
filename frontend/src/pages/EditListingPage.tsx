import { useState, useEffect, type FormEvent } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { listingsApi, type Listing, type ListingType, type Condition } from '../api/listings'
import { categoriesApi, type Category } from '../api/categories'
import { ApiError } from '../api/apiClient'
import { useToast } from '../components/useToast'
import { Spinner } from '../components/Spinner'

export function EditListingPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const toast = useToast()
  const [listing, setListing] = useState<Listing | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    Promise.all([listingsApi.getOne(Number(id)), categoriesApi.list()])
      .then(([l, c]) => {
        if (l.status === 'REMOVED') {
          setError('This listing has been removed and cannot be edited.')
        }
        setListing(l)
        setCategories(c.items)
      })
      .catch(err => {
        if (err instanceof ApiError && err.code === 'LISTING_NOT_FOUND') {
          navigate('/listings/mine')
        } else {
          setError('Failed to load listing.')
        }
      })
      .finally(() => setLoading(false))
  }, [id, navigate])

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!listing) return
    setError('')
    setSubmitting(true)

    const fd = new FormData(e.currentTarget)
    const image = fd.get('image') as File

    const body = {
      title: fd.get('title') as string,
      description: fd.get('description') as string,
      categoryCode: fd.get('categoryCode') as string,
      listingType: fd.get('listingType') as ListingType,
      price: fd.get('price') ? Number(fd.get('price')) : null,
      originalPrice: fd.get('originalPrice') ? Number(fd.get('originalPrice')) : null,
      condition: (fd.get('condition') as Condition) || null,
      meetAt: (fd.get('meetAt') as string) || null,
      negotiable: fd.get('negotiable') === 'on',
      reasonForSelling: (fd.get('reasonForSelling') as string) || null,
    }

    const multipart = new FormData()
    multipart.append('listing', new Blob([JSON.stringify(body)], { type: 'application/json' }))
    if (image && image.size > 0) {
      multipart.append('image', image)
    }

    try {
      await listingsApi.update(listing.id, multipart)
      toast.success('Changes saved!')
      navigate(`/listings/${listing.id}`)
    } catch (err) {
      if (err instanceof ApiError) setError(err.message)
      else setError('Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading)
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <Spinner />
      </main>
    )
  if (!listing) return null
  if (listing.status === 'REMOVED') {
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error">
          This listing has been removed and cannot be edited.
        </div>
      </main>
    )
  }

  return (
    <main className="max-w-2xl mx-auto px-6 py-10">
      <h1 className="text-3xl font-bold text-plum tracking-tight mb-6">Edit Listing</h1>
      {error && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {error}
        </div>
      )}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Title</span> *
          </label>
          <input
            name="title"
            required
            maxLength={80}
            defaultValue={listing.title}
            className={inputCls}
          />
        </div>
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Description</span> *
          </label>
          <textarea
            name="description"
            required
            maxLength={2000}
            rows={4}
            defaultValue={listing.description}
            className={inputCls}
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Category</span> *
            </label>
            <select
              name="categoryCode"
              required
              defaultValue={listing.category.code}
              className={inputCls}
            >
              {categories.map(c => (
                <option key={c.code} value={c.code}>
                  {c.nameEn}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Type</span> *
            </label>
            <select
              name="listingType"
              required
              defaultValue={listing.listingType}
              className={inputCls}
            >
              <option value="SELL">Sell</option>
              <option value="GIVEAWAY">Giveaway</option>
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Price</span>
            </label>
            <input
              name="price"
              type="number"
              step="0.01"
              min="0"
              defaultValue={listing.price ?? ''}
              className={inputCls}
            />
          </div>
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Original Price</span>
            </label>
            <input
              name="originalPrice"
              type="number"
              step="0.01"
              min="0"
              defaultValue={listing.originalPrice ?? ''}
              className={inputCls}
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Condition</span>
            </label>
            <select name="condition" defaultValue={listing.condition ?? ''} className={inputCls}>
              <option value="">Not specified</option>
              <option value="NEW">New</option>
              <option value="LIKE_NEW">Like New</option>
              <option value="GOOD">Good</option>
              <option value="FAIR">Fair</option>
              <option value="POOR">Poor</option>
            </select>
          </div>
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Meet At</span>
            </label>
            <input
              name="meetAt"
              maxLength={100}
              defaultValue={listing.meetAt ?? ''}
              className={inputCls}
            />
          </div>
        </div>
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Reason for Selling</span>
          </label>
          <input
            name="reasonForSelling"
            maxLength={100}
            defaultValue={listing.reasonForSelling ?? ''}
            className={inputCls}
          />
        </div>
        <div className="flex items-center gap-2">
          <input
            name="negotiable"
            type="checkbox"
            id="negotiable"
            defaultChecked={listing.negotiable}
          />
          <label htmlFor="negotiable" className="text-sm text-plum">
            Price is negotiable
          </label>
        </div>
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Image</span>{' '}
            <span className="text-xs text-muted">(leave empty to keep current)</span>
          </label>
          <div className="bg-mustard/30 rounded-card p-3">
            <input
              name="image"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="w-full"
            />
          </div>
          <p className="text-xs text-muted mt-1">Current: {listing.imageUrl}</p>
        </div>
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-full bg-plum px-6 py-2.5 text-surface font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button"
        >
          {submitting ? 'Saving...' : 'Save Changes'}
        </button>
      </form>
    </main>
  )
}

const inputCls =
  'mt-1 block w-full rounded-card border border-border-soft bg-card px-3 py-2 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20'
