import { useState, useCallback, type ReactNode } from 'react'
import { ToastContext } from './useToast'

interface Toast {
  id: number
  message: string
  type: 'success' | 'error'
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  let nextId = 0

  const add = useCallback((message: string, type: 'success' | 'error') => {
    const id = ++nextId
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 3000)
  }, [])

  const success = useCallback((msg: string) => add(msg, 'success'), [add])
  const error = useCallback((msg: string) => add(msg, 'error'), [add])

  return (
    <ToastContext.Provider value={{ success, error }}>
      {children}
      <div className="fixed top-4 right-4 z-50 space-y-2" aria-live="polite">
        {toasts.map(t => (
          <div key={t.id}
            className={`px-4 py-2 rounded shadow-lg text-sm text-white transition-opacity ${
              t.type === 'success' ? 'bg-green-600' : 'bg-red-600'
            }`}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
