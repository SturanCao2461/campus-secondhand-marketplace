import { useState, useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  listingsApi,
  type ListingSummary,
  type PagedListings,
  type ListingType,
} from '../api/listings'
import { categoriesApi, type Category } from '../api/categories'
import { Pagination } from '../components/Pagination'

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
    categoriesApi
      .list()
      .then(r => setCategories(r.items))
      .catch(() => {})
  }, [])

  useEffect(() => {
    setLoading(true)
    setError('')
    listingsApi
      .browse({
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

  const inputBase =
    'rounded-full border border-border-soft bg-card px-4 py-2 text-sm font-medium shadow-sm focus:border-coral focus:outline-none focus:ring-2 focus:ring-coral/20'

  return (
    <main className="max-w-6xl mx-auto px-6 py-10">
      <h1 className="text-3xl font-bold text-plum tracking-tight mb-6">Browse Listings</h1>

      <div className="flex flex-wrap gap-3 mb-8">
        <input
          type="text"
          placeholder="Search MacBook, textbooks, lamp..."
          defaultValue={keyword}
          onKeyDown={e => {
            if (e.key === 'Enter') updateParam('keyword', (e.target as HTMLInputElement).value)
          }}
          className={`${inputBase} w-56`}
        />
        <select
          value={categoryCode}
          onChange={e => updateParam('category', e.target.value)}
          className={inputBase}
        >
          <option value="">All Categories</option>
          {categories.map(c => (
            <option key={c.code} value={c.code}>
              {c.nameEn}
            </option>
          ))}
        </select>
        <select
          value={listingType}
          onChange={e => updateParam('type', e.target.value)}
          className={inputBase}
        >
          <option value="">All Types</option>
          <option value="SELL">For Sale</option>
          <option value="GIVEAWAY">Free</option>
        </select>
        <select
          value={sort}
          onChange={e => updateParam('sort', e.target.value)}
          className={inputBase}
        >
          <option value="CREATED_DESC">Newest</option>
          <option value="CREATED_ASC">Oldest</option>
          <option value="PRICE_ASC">Price: Low to High</option>
          <option value="PRICE_DESC">Price: High to Low</option>
        </select>
      </div>

      {loading && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {[1, 2, 3, 4, 5, 6, 7, 8].map(i => (
            <div key={i} className="bg-card rounded-card overflow-hidden shadow-card animate-pulse">
              <div className="w-full h-40 bg-mustard/30" />
              <div className="p-4 space-y-2">
                <div className="h-4 bg-coral/20 rounded w-3/4" />
                <div className="h-3 bg-coral/10 rounded w-1/2" />
              </div>
            </div>
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-card bg-error/10 border border-error/20 p-3 text-sm text-error mb-4">
          {error}
        </div>
      )}

      {!loading && !error && data && data.items.length === 0 && (
        <div className="text-center py-16 text-muted">
          <svg
            className="mx-auto h-16 w-16 text-coral/40 mb-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          <p className="text-lg font-semibold text-plum">No listings found</p>
          <p className="text-sm mt-1">Try adjusting your search or filters</p>
        </div>
      )}

      {!loading && !error && data && data.items.length > 0 && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {data.items.map((item: ListingSummary) => (
              <Link
                key={item.id}
                to={`/listings/${item.id}/detail`}
                className="bg-card rounded-card overflow-hidden shadow-card hover:-translate-y-0.5 hover:shadow-panel transition-all"
              >
                <img
                  src={item.imageUrl}
                  alt={item.title}
                  className="w-full h-40 object-cover bg-mustard/30"
                />
                <div className="p-4">
                  <h3 className="font-semibold text-sm text-plum truncate">{item.title}</h3>
                  <div className="flex justify-between items-baseline mt-1">
                    <p className="text-xl font-extrabold text-coral tracking-tight">
                      {item.listingType === 'GIVEAWAY'
                        ? 'Free'
                        : item.price != null
                          ? `$${item.price.toFixed(2)}`
                          : ''}
                    </p>
                  </div>
                  <p className="text-xs text-muted mt-1 font-medium">{item.category.nameEn}</p>
                </div>
              </Link>
            ))}
          </div>

          <Pagination
            page={page}
            totalPages={data.totalPages}
            onPageChange={p => {
              const next = new URLSearchParams(searchParams)
              next.set('page', String(p))
              setSearchParams(next)
            }}
          />
        </>
      )}
    </main>
  )
}
