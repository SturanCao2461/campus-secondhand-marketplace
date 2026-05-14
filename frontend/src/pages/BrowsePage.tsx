import { useState, useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { listingsApi, type ListingSummary, type PagedListings, type ListingType } from '../api/listings'
import { categoriesApi, type Category } from '../api/categories'

export function BrowsePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [data, setData] = useState<PagedListings | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const page = Number(searchParams.get('page') || '0')
  const keyword = searchParams.get('keyword') || ''
  const categoryCode = searchParams.get('category') || ''
  const sort = searchParams.get('sort') || 'CREATED_DESC'
  const listingType = (searchParams.get('type') || '') as ListingType | ''

  useEffect(() => {
    categoriesApi.list().then(r => setCategories(r.items)).catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    setError('')
    listingsApi.browse({
      page,
      keyword: keyword || undefined,
      categoryCode: categoryCode || undefined,
      listingType: listingType || undefined,
      sort: sort as 'CREATED_DESC',
    })
      .then(setData)
      .catch(() => setError('Failed to load listings.'))
      .finally(() => setLoading(false))
  }, [page, keyword, categoryCode, sort, listingType])

  function updateParam(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete('page')
    setSearchParams(next)
  }

  return (
    <main className="max-w-6xl mx-auto p-6">
      <h1 className="text-2xl font-bold mb-6">Browse Listings</h1>

      <div className="flex flex-wrap gap-3 mb-6">
        <input
          type="text"
          placeholder="Search by title..."
          defaultValue={keyword}
          onKeyDown={e => { if (e.key === 'Enter') updateParam('keyword', (e.target as HTMLInputElement).value) }}
          className="border rounded px-3 py-2 text-sm w-48"
        />
        <select value={categoryCode} onChange={e => updateParam('category', e.target.value)}
          className="border rounded px-3 py-2 text-sm">
          <option value="">All Categories</option>
          {categories.map(c => <option key={c.code} value={c.code}>{c.nameEn}</option>)}
        </select>
        <select value={listingType} onChange={e => updateParam('type', e.target.value)}
          className="border rounded px-3 py-2 text-sm">
          <option value="">All Types</option>
          <option value="SELL">For Sale</option>
          <option value="GIVEAWAY">Free</option>
        </select>
        <select value={sort} onChange={e => updateParam('sort', e.target.value)}
          className="border rounded px-3 py-2 text-sm">
          <option value="CREATED_DESC">Newest</option>
          <option value="CREATED_ASC">Oldest</option>
          <option value="PRICE_ASC">Price: Low to High</option>
          <option value="PRICE_DESC">Price: High to Low</option>
        </select>
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {[1, 2, 3, 4, 5, 6, 7, 8].map(i => (
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
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <p className="text-lg">No listings found</p>
          <p className="text-sm mt-1">Try adjusting your search or filters</p>
        </div>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {data.items.map((item: ListingSummary) => (
              <Link key={item.id} to={`/listings/${item.id}/detail`}
                className="border rounded-lg overflow-hidden hover:shadow-md transition-shadow">
                <img src={item.imageUrl} alt={item.title}
                  className="w-full h-40 object-cover bg-gray-100" />
                <div className="p-3">
                  <h3 className="font-medium text-sm truncate">{item.title}</h3>
                  <p className="text-blue-600 font-semibold text-sm mt-1">
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
              <button onClick={() => { const p = new URLSearchParams(searchParams); p.set('page', String(page - 1)); setSearchParams(p) }}
                disabled={page === 0}
                className="px-3 py-1 border rounded disabled:opacity-30">
                Previous
              </button>
              <span className="px-3 py-1 text-sm text-gray-600">
                Page {page + 1} of {data.totalPages}
              </span>
              <button onClick={() => { const p = new URLSearchParams(searchParams); p.set('page', String(page + 1)); setSearchParams(p) }}
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
