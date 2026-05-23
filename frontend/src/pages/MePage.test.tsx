import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { MePage } from './MePage'
import { AuthContext, type AuthContextValue, type AuthUser } from '../auth/AuthContext'
import { ApiError } from '../api/apiClient'

vi.mock('../api/apiClient', async () => {
  const actual = await vi.importActual<typeof import('../api/apiClient')>('../api/apiClient')
  return {
    ...actual,
    api: { post: vi.fn() },
  }
})

import { api } from '../api/apiClient'

const baseUser: AuthUser = {
  id: 42,
  email: 'jane@students.waikato.ac.nz',
  nickname: 'Jane',
  emailVerified: false,
}

function renderWithUser(user: AuthUser | null) {
  const value: AuthContextValue = {
    user,
    loading: false,
    login: async () => {},
    register: async () => {},
    logout: async () => {},
    refresh: async () => {},
  }
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter>
        <MePage />
      </MemoryRouter>
    </AuthContext.Provider>
  )
}

describe('MePage', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
  })

  it('renders nothing when user is null', () => {
    const { container } = renderWithUser(null)
    expect(container.querySelector('main')).toBeNull()
  })

  it('shows the green Email verified chip and no resend button when verified', () => {
    renderWithUser({ ...baseUser, emailVerified: true })
    expect(screen.getByText('Email verified')).toBeDefined()
    expect(screen.queryByText('Email not verified')).toBeNull()
    expect(screen.queryByRole('button', { name: /Resend verification email/i })).toBeNull()
  })

  it('shows the amber chip and the resend button when unverified', () => {
    renderWithUser({ ...baseUser, emailVerified: false })
    expect(screen.getByText('Email not verified')).toBeDefined()
    expect(screen.getByRole('button', { name: /Resend verification email/i })).toBeDefined()
  })

  it('POSTs to /api/auth/resend-verification and shows the sent confirmation', async () => {
    vi.mocked(api.post).mockResolvedValue(undefined)
    renderWithUser({ ...baseUser, emailVerified: false })

    fireEvent.click(screen.getByRole('button', { name: /Resend verification email/i }))

    await waitFor(() => {
      expect(screen.getByText(/Sent — check your inbox/i)).toBeDefined()
    })
    expect(api.post).toHaveBeenCalledWith('/api/auth/resend-verification', {})
    expect(screen.queryByRole('button', { name: /Resend verification email/i })).toBeNull()
  })

  it('shows the API error message when resend fails', async () => {
    vi.mocked(api.post).mockRejectedValue(
      new ApiError(429, 'TOO_MANY_REQUESTS', 'Too many requests. Try again later.')
    )
    renderWithUser({ ...baseUser, emailVerified: false })

    fireEvent.click(screen.getByRole('button', { name: /Resend verification email/i }))

    await waitFor(() => {
      expect(screen.getByText(/Too many requests/i)).toBeDefined()
    })
    // Button still present so user can retry
    expect(screen.getByRole('button', { name: /Resend verification email/i })).toBeDefined()
  })
})
