import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ErrorBoundary } from './ErrorBoundary'

function Boom(): never {
  throw new Error('kaboom')
}

describe('ErrorBoundary', () => {
  it('renders children when no error', () => {
    render(
      <ErrorBoundary>
        <p>safe</p>
      </ErrorBoundary>
    )
    expect(screen.getByText('safe')).toBeDefined()
  })

  it('renders default fallback UI when child throws', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>
    )
    expect(screen.getByText(/Something went wrong/i)).toBeDefined()
    expect(screen.getByText('kaboom')).toBeDefined()
    expect(screen.getByRole('button', { name: /try again/i })).toBeDefined()
    spy.mockRestore()
  })

  it('renders custom fallback when provided', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <ErrorBoundary fallback={<p>custom-fallback</p>}>
        <Boom />
      </ErrorBoundary>
    )
    expect(screen.getByText('custom-fallback')).toBeDefined()
    spy.mockRestore()
  })

  it('clears error state when Try again is clicked', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    let shouldThrow = true
    function MaybeBoom() {
      if (shouldThrow) throw new Error('boom-once')
      return <p>recovered</p>
    }

    const { rerender } = render(
      <ErrorBoundary>
        <MaybeBoom />
      </ErrorBoundary>
    )
    expect(screen.getByText('boom-once')).toBeDefined()

    shouldThrow = false
    fireEvent.click(screen.getByRole('button', { name: /try again/i }))
    rerender(
      <ErrorBoundary>
        <MaybeBoom />
      </ErrorBoundary>
    )
    expect(screen.getByText('recovered')).toBeDefined()
    spy.mockRestore()
  })
})
