import { useParams } from 'react-router-dom'

export default function ListingDetail() {
  const { id } = useParams<{ id: string }>()
  // TODO: Fetch listing detail from /api/listings/:id
  // TODO: Show seller info, images, description, contact button
  return (
    <div>
      <h1 className="page-title">Listing Detail</h1>
      <div className="placeholder-card">
        <p>🔍 Details for listing <strong>#{id}</strong> will appear here.</p>
        {/* TODO: Display listing images, title, price, condition, description */}
        {/* TODO: Add "Message Seller" button */}
        {/* TODO: Add "Add to Favorites" button */}
      </div>
    </div>
  )
}
