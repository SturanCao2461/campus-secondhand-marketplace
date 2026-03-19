import { Link } from 'react-router-dom'

export default function Login() {
  // TODO: Implement login form submission to /api/auth/login
  // TODO: Store JWT token and redirect on success
  return (
    <div className="form-container">
      <h2 className="form-title">Login</h2>
      <form>
        <div className="form-group">
          <label htmlFor="email">University Email</label>
          <input id="email" type="email" placeholder="you@university.edu" />
        </div>
        <div className="form-group">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" placeholder="••••••••" />
        </div>
        {/* TODO: Add form validation */}
        <button type="submit" className="btn btn-submit btn-full">Login</button>
      </form>
      <p className="form-footer">
        Don't have an account? <Link to="/register">Register</Link>
      </p>
    </div>
  )
}
