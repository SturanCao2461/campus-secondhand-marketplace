import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { AuthContext, type AuthContextValue } from './AuthContext'

function withAuth(value: Partial<AuthContextValue>) {
  const v: AuthContextValue = {
    user: null,
    loading: false,
    login: async () => {},
    register: async () => {},
    logout: async () => {},
    refresh: async () => {},
    ...value,
  } as AuthContextValue
  return ({ children }: { children: React.ReactNode }) => (
    <AuthContext.Provider value={v}>{children}</AuthContext.Provider>
  )
}

function LoginProbe() {
  const loc = useLocation()
  return <div>login-page-{loc.search}</div>
}

function renderAt(path: string, providerValue: Partial<AuthContextValue>) {
  const Wrapper = withAuth(providerValue)
  return render(
    <Wrapper>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route
            path="/me"
            element={
              <ProtectedRoute>
                <div>protected-content</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<LoginProbe />} />
        </Routes>
      </MemoryRouter>
    </Wrapper>
  )
}

describe('ProtectedRoute', () => {
  it('renders children when user is authenticated', () => {
    renderAt('/me', {
      user: {
        id: 1,
        email: 'a@students.waikato.ac.nz',
        nickname: 'A',
        emailVerified: true,
      },
      loading: false,
    })
    expect(screen.getByText('protected-content')).toBeDefined()
  })

  it('shows loading placeholder while auth is bootstrapping', () => {
    renderAt('/me', { user: null, loading: true })
    expect(screen.getByText(/loading/i)).toBeDefined()
    expect(screen.queryByText('protected-content')).toBeNull()
  })

  it('redirects to /login with ?next when unauthenticated', () => {
    renderAt('/me', { user: null, loading: false })
    expect(screen.queryByText('protected-content')).toBeNull()
    expect(screen.getByText(/login-page-/)).toBeDefined()
  })

  it('preserves search params in ?next', () => {
    renderAt('/me?foo=bar', { user: null, loading: false })
    const text = screen.getByText(/login-page-/).textContent || ''
    // Expect /login?next=%2Fme%3Ffoo%3Dbar
    expect(text).toMatch(/next=/)
    expect(decodeURIComponent(text)).toContain('/me?foo=bar')
  })
})
