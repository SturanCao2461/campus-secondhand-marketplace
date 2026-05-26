import { useState, useEffect, useRef, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { listingsApi, type ListingType, type Condition } from '../api/listings'
import { categoriesApi, type Category } from '../api/categories'
import { ApiError } from '../api/apiClient'
import { useToast } from '../components/useToast'
import { useUnsavedChangesGuard } from '../hooks/useUnsavedChangesGuard'

export function CreateListingPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [categories, setCategories] = useState<Category[]>([])
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [dirty, setDirty] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)

  useUnsavedChangesGuard(dirty && !submitting)

  useEffect(() => {
    categoriesApi
      .list()
      .then(r => setCategories(r.items))
      .catch(() => {})
  }, [])

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError('')
    setFieldErrors({})
    setSubmitting(true)

    const fd = new FormData(e.currentTarget)
    const image = fd.get('image') as File
    if (!image || image.size === 0) {
      setError('Please select an image.')
      setSubmitting(false)
      return
    }

    const listing = {
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
    multipart.append('listing', new Blob([JSON.stringify(listing)], { type: 'application/json' }))
    multipart.append('image', image)

    try {
      await listingsApi.create(multipart)
      setDirty(false)
      toast.success('Listing created!')
      navigate('/listings/mine')
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'INVALID_PRICE') setFieldErrors({ price: err.message })
        else if (err.code === 'INVALID_CATEGORY') setFieldErrors({ categoryCode: err.message })
        else if (err.code === 'INVALID_IMAGE' || err.code === 'MISSING_IMAGE')
          setFieldErrors({ image: err.message })
        else setError(err.message)
      } else setError('Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="max-w-2xl mx-auto px-6 py-10">
      <h1 className="text-3xl font-bold text-plum tracking-tight mb-6">Create Listing</h1>
      {error && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {error}
        </div>
      )}
      <form
        onSubmit={handleSubmit}
        className="space-y-4"
        onChange={() => setDirty(true)}
        ref={formRef}
      >
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Title</span> *
          </label>
          <input name="title" required maxLength={80} className={inputCls} />
        </div>
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Description</span> *
          </label>
          <textarea name="description" required maxLength={2000} rows={4} className={inputCls} />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Category</span> *
            </label>
            <select name="categoryCode" required className={inputCls}>
              <option value="">Select...</option>
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
            <select name="listingType" required className={inputCls}>
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
            <input name="price" type="number" step="0.01" min="0" className={inputCls} />
            {fieldErrors.price && <p className="text-xs text-error mt-1">{fieldErrors.price}</p>}
          </div>
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Original Price</span>
            </label>
            <input name="originalPrice" type="number" step="0.01" min="0" className={inputCls} />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-1">
              <span className="font-semibold text-plum">Condition</span>
            </label>
            <select name="condition" className={inputCls}>
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
            <input name="meetAt" maxLength={100} className={inputCls} />
          </div>
        </div>
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Reason for Selling</span>
          </label>
          <input name="reasonForSelling" maxLength={100} className={inputCls} />
        </div>
        <div className="flex items-center gap-2">
          <input name="negotiable" type="checkbox" id="negotiable" />
          <label htmlFor="negotiable" className="text-sm text-plum">
            Price is negotiable
          </label>
        </div>
        <div>
          <label className="block text-sm mb-1">
            <span className="font-semibold text-plum">Image</span> *
          </label>
          <div className="bg-mustard/30 rounded-card p-3">
            <input
              name="image"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              required
              className="w-full"
            />
          </div>
          {fieldErrors.image && <p className="text-xs text-error mt-1">{fieldErrors.image}</p>}
        </div>
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-full bg-plum px-6 py-2.5 text-surface font-bold hover:bg-ink disabled:opacity-50 transition-colors shadow-button"
        >
          {submitting ? 'Creating...' : 'Create Listing'}
        </button>
      </form>
    </main>
  )
}

const inputCls =
  'mt-1 block w-full rounded-card border border-border-soft bg-card px-3 py-2 text-sm shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20'
