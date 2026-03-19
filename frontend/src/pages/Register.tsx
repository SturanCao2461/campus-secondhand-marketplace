import { Link } from 'react-router-dom'

export default function Register() {
  // TODO: Implement registration form submission to /api/auth/register
  // TODO: Validate that email is a university email domain
  return (
    <div className="form-container">
      <h2 className="form-title">Create Account</h2>
      <form>
        <div className="form-group">
          <label htmlFor="name">Full Name</label>
          <input id="name" type="text" placeholder="Jane Smith" />
        </div>
        <div className="form-group">
          <label htmlFor="email">University Email</label>
          <input id="email" type="email" placeholder="you@university.edu" />
        </div>
        <div className="form-group">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" placeholder="••••••••" />
        </div>
        {/* TODO: Add confirm password field */}
        {/* TODO: Add university email domain validation */}
        <button type="submit" className="btn btn-submit btn-full">Register</button>
      </form>
      <p className="form-footer">
        Already have an account? <Link to="/login">Login</Link>
      </p>
    </div>
  )
}
