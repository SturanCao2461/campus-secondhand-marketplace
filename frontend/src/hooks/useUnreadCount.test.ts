import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useUnreadCount } from './useUnreadCount'
import { conversationsApi } from '../api/conversations'

vi.mock('../api/conversations', () => ({
  conversationsApi: {
    unreadCount: vi.fn(),
  },
}))

const mockedUnreadCount = conversationsApi.unreadCount as ReturnType<typeof vi.fn>

describe('useUnreadCount', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockedUnreadCount.mockReset()
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'visible',
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns 0 when disabled', () => {
    const { result } = renderHook(() => useUnreadCount(false))
    expect(result.current).toBe(0)
    expect(mockedUnreadCount).not.toHaveBeenCalled()
  })

  it('polls immediately when enabled and updates count', async () => {
    mockedUnreadCount.mockResolvedValue({ total: 5 })

    const { result } = renderHook(() => useUnreadCount(true))

    await act(async () => {
      await Promise.resolve()
    })

    expect(mockedUnreadCount).toHaveBeenCalledTimes(1)
    expect(result.current).toBe(5)
  })

  it('polls every 15 seconds while page is visible', async () => {
    mockedUnreadCount
      .mockResolvedValueOnce({ total: 1 })
      .mockResolvedValueOnce({ total: 2 })
      .mockResolvedValueOnce({ total: 3 })

    const { result } = renderHook(() => useUnreadCount(true))

    await act(async () => {
      await Promise.resolve()
    })
    expect(result.current).toBe(1)

    await act(async () => {
      vi.advanceTimersByTime(15_000)
      await Promise.resolve()
    })
    expect(mockedUnreadCount).toHaveBeenCalledTimes(2)

    await act(async () => {
      vi.advanceTimersByTime(15_000)
      await Promise.resolve()
    })
    expect(mockedUnreadCount).toHaveBeenCalledTimes(3)
  })

  it('silently ignores API errors', async () => {
    mockedUnreadCount.mockRejectedValue(new Error('network down'))

    const { result } = renderHook(() => useUnreadCount(true))

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    // count remains at initial 0, no thrown error
    expect(result.current).toBe(0)
  })

  it('resets count to 0 when disabled after being enabled', async () => {
    mockedUnreadCount.mockResolvedValue({ total: 7 })

    const { result, rerender } = renderHook(({ enabled }) => useUnreadCount(enabled), {
      initialProps: { enabled: true },
    })

    await act(async () => {
      await Promise.resolve()
    })
    expect(result.current).toBe(7)

    rerender({ enabled: false })
    expect(result.current).toBe(0)
  })

  it('cleans up interval on unmount', async () => {
    mockedUnreadCount.mockResolvedValue({ total: 1 })

    const { unmount } = renderHook(() => useUnreadCount(true))

    await act(async () => {
      await Promise.resolve()
    })
    expect(mockedUnreadCount).toHaveBeenCalledTimes(1)

    unmount()

    await act(async () => {
      vi.advanceTimersByTime(60_000)
      await Promise.resolve()
    })

    // No more polls after unmount
    expect(mockedUnreadCount).toHaveBeenCalledTimes(1)
  })
})
