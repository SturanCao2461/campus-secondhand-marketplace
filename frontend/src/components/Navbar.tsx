import { Link } from 'react-router-dom'

export default function Navbar() {
  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <Link to="/">🎓 Campus Marketplace</Link>
      </div>
      <div className="navbar-links">
        <Link to="/listings">Browse</Link>
        <Link to="/favorites">Favorites</Link>
        <Link to="/messages">Messages</Link>
        <Link to="/my-listings">My Listings</Link>
        <Link to="/login" className="btn-link">Login</Link>
        <Link to="/register" className="btn-link btn-primary">Register</Link>
      </div>
    </nav>
  )
}
