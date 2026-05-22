import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useChatPolling } from './useChatPolling'
import { conversationsApi, type MessageResponse } from '../api/conversations'

vi.mock('../api/conversations', () => ({
  conversationsApi: {
    getMessages: vi.fn(),
  },
}))

const mockedGet = conversationsApi.getMessages as ReturnType<typeof vi.fn>

const msg = (id: number, content = 'hi'): MessageResponse => ({
  id,
  conversationId: 1,
  senderId: 1,
  senderNickname: 'TestSender',
  content,
  createdAt: new Date(2026, 0, 1, 0, 0, id).toISOString(),
})

describe('useChatPolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockedGet.mockReset()
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'visible',
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns empty messages and loading=false when conversationId is null', async () => {
    const { result } = renderHook(() => useChatPolling(null))
    await act(async () => {
      await Promise.resolve()
    })
    expect(result.current.messages).toEqual([])
    expect(mockedGet).not.toHaveBeenCalled()
  })

  it('fetches initial messages when conversationId is provided', async () => {
    mockedGet.mockResolvedValueOnce([msg(1), msg(2), msg(3)])
    const { result } = renderHook(() => useChatPolling(7))
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(mockedGet).toHaveBeenCalledWith(7, undefined, 50)
    expect(result.current.messages).toHaveLength(3)
    expect(result.current.loading).toBe(false)
  })

  it('appends only new messages on subsequent polls (cursor)', async () => {
    mockedGet
      .mockResolvedValueOnce([msg(1), msg(2)])
      .mockResolvedValueOnce([msg(3)])
      .mockResolvedValueOnce([msg(4), msg(5)])

    const { result } = renderHook(() => useChatPolling(7))
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(result.current.messages.map(m => m.id)).toEqual([1, 2])

    await act(async () => {
      vi.advanceTimersByTime(5_000)
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(mockedGet).toHaveBeenLastCalledWith(7, 2, 50)
    expect(result.current.messages.map(m => m.id)).toEqual([1, 2, 3])

    await act(async () => {
      vi.advanceTimersByTime(5_000)
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(mockedGet).toHaveBeenLastCalledWith(7, 3, 50)
    expect(result.current.messages.map(m => m.id)).toEqual([1, 2, 3, 4, 5])
  })

  it('addOptimistic appends a message and advances the cursor', async () => {
    mockedGet
      .mockResolvedValueOnce([msg(1)])
      .mockResolvedValueOnce([msg(11)]) // server-confirmed reply, must come AFTER optimistic id 10
    const { result } = renderHook(() => useChatPolling(7))
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    act(() => {
      result.current.addOptimistic(msg(10, 'pending'))
    })
    expect(result.current.messages.map(m => m.id)).toEqual([1, 10])

    await act(async () => {
      vi.advanceTimersByTime(5_000)
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(mockedGet).toHaveBeenLastCalledWith(7, 10, 50)
    expect(result.current.messages.map(m => m.id)).toEqual([1, 10, 11])
  })

  it('silently ignores polling errors', async () => {
    mockedGet.mockResolvedValueOnce([msg(1)]).mockRejectedValueOnce(new Error('network'))
    const { result } = renderHook(() => useChatPolling(7))
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(result.current.messages).toHaveLength(1)

    await act(async () => {
      vi.advanceTimersByTime(5_000)
      await Promise.resolve()
      await Promise.resolve()
    })
    // count unchanged, no thrown error
    expect(result.current.messages).toHaveLength(1)
  })

  it('cleans up interval on unmount', async () => {
    mockedGet.mockResolvedValue([msg(1)])
    const { unmount } = renderHook(() => useChatPolling(7))
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(mockedGet).toHaveBeenCalledTimes(1)
    unmount()
    await act(async () => {
      vi.advanceTimersByTime(60_000)
      await Promise.resolve()
    })
    expect(mockedGet).toHaveBeenCalledTimes(1)
  })
})
