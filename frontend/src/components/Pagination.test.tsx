import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('renders nothing when totalPages <= 1', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onPageChange={() => {}} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders all page buttons when totalPages <= 7', () => {
    render(<Pagination page={0} totalPages={5} onPageChange={() => {}} />)
    for (let i = 1; i <= 5; i++) {
      expect(screen.getByText(String(i))).toBeDefined()
    }
    expect(screen.queryByText('…')).toBeNull()
  })

  it('collapses middle pages with ellipsis when totalPages > 7', () => {
    render(<Pagination page={0} totalPages={10} onPageChange={() => {}} />)
    expect(screen.getByText('1')).toBeDefined()
    expect(screen.getByText('10')).toBeDefined()
    expect(screen.getByText('…')).toBeDefined()
    expect(screen.queryByText('5')).toBeNull()
  })

  it('shows both ellipses when current is in middle of long range', () => {
    render(<Pagination page={4} totalPages={10} onPageChange={() => {}} />)
    expect(screen.getByText('1')).toBeDefined()
    expect(screen.getByText('10')).toBeDefined()
    expect(screen.getAllByText('…')).toHaveLength(2)
    expect(screen.getByText('5')).toBeDefined()
    expect(screen.getByText('4')).toBeDefined()
    expect(screen.getByText('6')).toBeDefined()
  })

  it('marks current page as aria-current and disables it', () => {
    render(<Pagination page={2} totalPages={5} onPageChange={() => {}} />)
    const current = screen.getByText('3') as HTMLButtonElement
    expect(current.getAttribute('aria-current')).toBe('page')
    expect(current.disabled).toBe(true)
  })

  it('calls onPageChange with the clicked page index', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={0} totalPages={5} onPageChange={onPageChange} />)
    fireEvent.click(screen.getByText('3'))
    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it('disables Previous on first page and Next on last page', () => {
    const { rerender } = render(<Pagination page={0} totalPages={5} onPageChange={() => {}} />)
    expect((screen.getByText('Previous') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByText('Next') as HTMLButtonElement).disabled).toBe(false)

    rerender(<Pagination page={4} totalPages={5} onPageChange={() => {}} />)
    expect((screen.getByText('Previous') as HTMLButtonElement).disabled).toBe(false)
    expect((screen.getByText('Next') as HTMLButtonElement).disabled).toBe(true)
  })
})
