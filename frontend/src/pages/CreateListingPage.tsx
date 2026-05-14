import { useState, useEffect, useRef, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { listingsApi, type ListingType, type Condition } from '../api/listings'
import { categoriesApi, type Category } from '../api/categories'
import { ApiError } from '../api/apiClient'
import { useToast } from '../components/Toast'
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
    categoriesApi.list().then(r => setCategories(r.items)).catch(() => {})
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
        else if (err.code === 'INVALID_IMAGE' || err.code === 'MISSING_IMAGE') setFieldErrors({ image: err.message })
        else setError(err.message)
      } else setError('Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="max-w-2xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Create Listing</h1>
      {error && <p className="text-red-600 mb-4">{error}</p>}
      <form onSubmit={handleSubmit} className="space-y-4" onChange={() => setDirty(true)} ref={formRef}>
        <div>
          <label className="block text-sm font-medium mb-1">Title *</label>
          <input name="title" required maxLength={80}
            className="w-full border rounded px-3 py-2" />
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Description *</label>
          <textarea name="description" required maxLength={2000} rows={4}
            className="w-full border rounded px-3 py-2" />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium mb-1">Category *</label>
            <select name="categoryCode" required className="w-full border rounded px-3 py-2">
              <option value="">Select...</option>
              {categories.map(c => (
                <option key={c.code} value={c.code}>{c.nameEn}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Type *</label>
            <select name="listingType" required className="w-full border rounded px-3 py-2">
              <option value="SELL">Sell</option>
              <option value="GIVEAWAY">Giveaway</option>
            </select>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium mb-1">Price</label>
            <input name="price" type="number" step="0.01" min="0"
              className="w-full border rounded px-3 py-2" />
            {fieldErrors.price && <p className="text-red-600 text-xs mt-1">{fieldErrors.price}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Original Price</label>
            <input name="originalPrice" type="number" step="0.01" min="0"
              className="w-full border rounded px-3 py-2" />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium mb-1">Condition</label>
            <select name="condition" className="w-full border rounded px-3 py-2">
              <option value="">Not specified</option>
              <option value="NEW">New</option>
              <option value="LIKE_NEW">Like New</option>
              <option value="GOOD">Good</option>
              <option value="FAIR">Fair</option>
              <option value="POOR">Poor</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Meet At</label>
            <input name="meetAt" maxLength={100}
              className="w-full border rounded px-3 py-2" />
          </div>
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Reason for Selling</label>
          <input name="reasonForSelling" maxLength={100}
            className="w-full border rounded px-3 py-2" />
        </div>
        <div className="flex items-center gap-2">
          <input name="negotiable" type="checkbox" id="negotiable" />
          <label htmlFor="negotiable" className="text-sm">Price is negotiable</label>
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Image *</label>
          <input name="image" type="file" accept="image/jpeg,image/png,image/webp" required
            className="w-full" />
          {fieldErrors.image && <p className="text-red-600 text-xs mt-1">{fieldErrors.image}</p>}
        </div>
        <button type="submit" disabled={submitting}
          className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 disabled:opacity-50">
          {submitting ? 'Creating...' : 'Create Listing'}
        </button>
      </form>
    </main>
  )
}
