import { describe, it, expect } from 'vitest'
import { render, renderHook } from '@testing-library/react'
import { ToastContext, useToast } from './useToast'

describe('useToast', () => {
  it('throws when used outside ToastProvider', () => {
    // Suppress React's error logging for this expected throw
    const originalError = console.error
    console.error = () => {}
    try {
      expect(() => renderHook(() => useToast())).toThrowError(
        /useToast must be used inside ToastProvider/i
      )
    } finally {
      console.error = originalError
    }
  })

  it('returns the context value when wrapped in a provider', () => {
    const value = {
      success: () => {},
      error: () => {},
    }
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <ToastContext.Provider value={value}>{children}</ToastContext.Provider>
    )
    const { result } = renderHook(() => useToast(), { wrapper })
    expect(result.current).toBe(value)
    expect(typeof result.current.success).toBe('function')
    expect(typeof result.current.error).toBe('function')
  })

  it('provides distinct success and error helpers', () => {
    const successCalls: string[] = []
    const errorCalls: string[] = []
    const value = {
      success: (m: string) => successCalls.push(m),
      error: (m: string) => errorCalls.push(m),
    }
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <ToastContext.Provider value={value}>{children}</ToastContext.Provider>
    )
    const { result } = renderHook(() => useToast(), { wrapper })
    result.current.success('saved')
    result.current.error('oops')
    expect(successCalls).toEqual(['saved'])
    expect(errorCalls).toEqual(['oops'])
  })

  // Render placeholder to keep render util tree-shaken to be imported
  it('render util import keeps test bundle correct', () => {
    expect(typeof render).toBe('function')
  })
})
