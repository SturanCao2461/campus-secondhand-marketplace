import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useUnsavedChangesGuard } from './useUnsavedChangesGuard'

describe('useUnsavedChangesGuard', () => {
  let addSpy: ReturnType<typeof vi.spyOn>
  let removeSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    addSpy = vi.spyOn(window, 'addEventListener')
    removeSpy = vi.spyOn(window, 'removeEventListener')
  })

  afterEach(() => {
    addSpy.mockRestore()
    removeSpy.mockRestore()
  })

  it('does NOT register a beforeunload handler when isDirty is false', () => {
    renderHook(() => useUnsavedChangesGuard(false))
    const beforeunloadCalls = addSpy.mock.calls.filter((c: unknown[]) => c[0] === 'beforeunload')
    expect(beforeunloadCalls).toHaveLength(0)
  })

  it('registers a beforeunload handler when isDirty is true', () => {
    renderHook(() => useUnsavedChangesGuard(true))
    const beforeunloadCalls = addSpy.mock.calls.filter((c: unknown[]) => c[0] === 'beforeunload')
    expect(beforeunloadCalls).toHaveLength(1)
  })

  it('removes the handler on unmount', () => {
    const { unmount } = renderHook(() => useUnsavedChangesGuard(true))
    expect(addSpy.mock.calls.filter((c: unknown[]) => c[0] === 'beforeunload')).toHaveLength(1)
    unmount()
    const removed = removeSpy.mock.calls.filter((c: unknown[]) => c[0] === 'beforeunload')
    expect(removed).toHaveLength(1)
  })

  it('removes when isDirty flips false (re-render)', () => {
    const { rerender } = renderHook(({ dirty }) => useUnsavedChangesGuard(dirty), {
      initialProps: { dirty: true },
    })
    expect(addSpy.mock.calls.filter((c: unknown[]) => c[0] === 'beforeunload')).toHaveLength(1)
    rerender({ dirty: false })
    expect(removeSpy.mock.calls.filter((c: unknown[]) => c[0] === 'beforeunload')).toHaveLength(1)
  })

  it('the registered handler calls preventDefault and sets returnValue', () => {
    renderHook(() => useUnsavedChangesGuard(true))
    const handler = addSpy.mock.calls.find((c: unknown[]) => c[0] === 'beforeunload')?.[1] as
      | EventListener
      | undefined
    expect(handler).toBeDefined()
    const event = {
      preventDefault: vi.fn(),
      returnValue: '',
    } as unknown as BeforeUnloadEvent
    handler!(event)
    expect(event.preventDefault).toHaveBeenCalled()
    expect(event.returnValue).toBe('')
  })
})
