import { Link } from 'react-router-dom'

export default function MyListings() {
  // TODO: Fetch user's listings from /api/listings/my (requires auth)
  // TODO: Allow edit and delete of listings
  return (
    <div>
      <h1 className="page-title">My Listings</h1>
      <p className="page-subtitle">Items you are selling.</p>
      {/* TODO: Add "New Listing" button that opens a create form */}
      <Link to="/listings/new" className="btn btn-submit" style={{ marginBottom: '1.5rem', display: 'inline-block' }}>
        + New Listing
      </Link>
      <div className="placeholder-card">
        <p>📝 Your listings will appear here.</p>
        {/* TODO: Render user's listing cards with edit/delete actions */}
      </div>
    </div>
  )
}
