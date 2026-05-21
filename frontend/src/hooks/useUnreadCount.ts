import { useEffect, useRef, useState, useCallback } from 'react'
import { conversationsApi } from '../api/conversations'

export function useUnreadCount(enabled: boolean) {
  const [count, setCount] = useState(0)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const poll = useCallback(async () => {
    try {
      const res = await conversationsApi.unreadCount()
      setCount(res.total)
    } catch {
      // silently ignore — user might be logged out
    }
  }, [])

  useEffect(() => {
    if (!enabled) {
      setCount(0)
      return
    }

    poll()

    const start = () => {
      if (!intervalRef.current) {
        intervalRef.current = setInterval(poll, 15_000)
      }
    }
    const stop = () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }

    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        poll()
        start()
      } else {
        stop()
      }
    }

    document.addEventListener('visibilitychange', onVisibility)
    if (document.visibilityState === 'visible') start()

    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [enabled, poll])

  return count
}
