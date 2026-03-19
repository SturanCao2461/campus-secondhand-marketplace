import { Link } from 'react-router-dom'

export default function Home() {
  return (
    <div>
      <h1 className="page-title">Welcome to Campus Marketplace 🎓</h1>
      <p className="page-subtitle">
        Buy and sell second-hand items safely within your campus community.
        Local pickup only — no shipping, no payments online.
      </p>
      {/* TODO: Add featured listings carousel */}
      {/* TODO: Add search bar */}
      <div className="placeholder-card">
        <p>📦 Browse listings from students and staff on campus.</p>
        <br />
        <Link to="/listings" className="btn btn-submit">Browse Listings</Link>
      </div>
    </div>
  )
}
