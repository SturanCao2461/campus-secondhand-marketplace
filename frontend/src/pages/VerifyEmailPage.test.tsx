import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { VerifyEmailPage } from './VerifyEmailPage'
import { ApiError } from '../api/apiClient'

vi.mock('../api/apiClient', async () => {
  const actual = await vi.importActual<typeof import('../api/apiClient')>('../api/apiClient')
  return {
    ...actual,
    api: { post: vi.fn() },
  }
})

import { api } from '../api/apiClient'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('VerifyEmailPage', () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
  })

  it('shows the loading spinner while POSTing the token', () => {
    vi.mocked(api.post).mockReturnValue(new Promise(() => {}))
    renderAt('/verify-email?token=abc')
    expect(screen.getByText(/Verifying your email/i)).toBeDefined()
  })

  it('shows the success card when the API succeeds', async () => {
    vi.mocked(api.post).mockResolvedValue(undefined)
    renderAt('/verify-email?token=abc')
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Email verified/i })).toBeDefined()
    })
    expect(api.post).toHaveBeenCalledWith('/api/auth/verify-email', { token: 'abc' })
    expect(screen.getByRole('link', { name: /Continue to home/i })).toBeDefined()
  })

  it('shows the failure card with the API error message when the token is invalid', async () => {
    vi.mocked(api.post).mockRejectedValue(
      new ApiError(400, 'INVALID_VERIFICATION_TOKEN', 'This verification link is invalid or has expired.')
    )
    renderAt('/verify-email?token=expired')
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Verification failed/i })).toBeDefined()
    })
    expect(screen.getByText(/invalid or has expired/i)).toBeDefined()
  })

  it('shows the failure card with a sensible message when token is missing', async () => {
    renderAt('/verify-email')
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Verification failed/i })).toBeDefined()
    })
    expect(screen.getByText(/Missing verification token/i)).toBeDefined()
    expect(api.post).not.toHaveBeenCalled()
  })
})
