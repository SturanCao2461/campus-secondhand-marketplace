import { api } from './apiClient'
import type { Category } from './categories'

export type ListingStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'REMOVED'
export type ListingType = 'SELL' | 'GIVEAWAY'
export type Condition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR'

export interface ListingSummary {
  id: number
  title: string
  price: number | null
  imageUrl: string
  status: ListingStatus
  listingType: ListingType
  category: Category
  createdAt: string
}

export interface Listing extends ListingSummary {
  ownerId: number
  description: string
  originalPrice: number | null
  condition: Condition | null
  meetAt: string | null
  negotiable: boolean
  reasonForSelling: string | null
  updatedAt: string
}

export interface PagedListings {
  items: ListingSummary[]
  page: number
  pageSize: number
  totalPages: number
  totalItems: number
}

export interface MyListingsQuery {
  page?: number
  status?: ListingStatus | 'ALL'
  sort?: 'CREATED_DESC' | 'CREATED_ASC' | 'PRICE_DESC' | 'PRICE_ASC'
  includeRemoved?: boolean
}

export interface BrowseQuery {
  page?: number
  keyword?: string
  categoryCode?: string
  minPrice?: number
  maxPrice?: number
  listingType?: ListingType
  sort?: 'CREATED_DESC' | 'CREATED_ASC' | 'PRICE_DESC' | 'PRICE_ASC'
}

export const listingsApi = {
  browse: (q: BrowseQuery) => {
    const params = new URLSearchParams()
    if (q.page != null) params.set('page', String(q.page))
    if (q.keyword) params.set('keyword', q.keyword)
    if (q.categoryCode) params.set('categoryCode', q.categoryCode)
    if (q.minPrice != null) params.set('minPrice', String(q.minPrice))
    if (q.maxPrice != null) params.set('maxPrice', String(q.maxPrice))
    if (q.listingType) params.set('listingType', q.listingType)
    if (q.sort) params.set('sort', q.sort)
    return api.get<PagedListings>(`/api/listings?${params}`)
  },
  getDetail: (id: number) => api.get<Listing>(`/api/listings/${id}/detail`),
  create: (form: FormData) => api.postForm<Listing>('/api/listings', form),
  listMine: (q: MyListingsQuery) => {
    const params = new URLSearchParams()
    if (q.page != null) params.set('page', String(q.page))
    if (q.status && q.status !== 'ALL') params.set('status', q.status)
    if (q.sort) params.set('sort', q.sort)
    if (q.includeRemoved) params.set('includeRemoved', 'true')
    return api.get<PagedListings>(`/api/listings/me?${params}`)
  },
  getOne: (id: number) => api.get<Listing>(`/api/listings/${id}`),
  update: (id: number, form: FormData) => api.putForm<Listing>(`/api/listings/${id}`, form),
  changeStatus: (id: number, newStatus: ListingStatus) =>
    api.patch<Listing>(`/api/listings/${id}/status`, { newStatus }),
  remove: (id: number) => api.delete<void>(`/api/listings/${id}`),
}
