import { useEffect, useRef, useState, useCallback } from 'react'
import { conversationsApi, type MessageResponse } from '../api/conversations'

export function useChatPolling(conversationId: number | null) {
  const [messages, setMessages] = useState<MessageResponse[]>([])
  const [loading, setLoading] = useState(true)
  const lastIdRef = useRef<number>(0)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchMessages = useCallback(
    async (afterId?: number) => {
      if (!conversationId) return
      try {
        const msgs = await conversationsApi.getMessages(conversationId, afterId, 50)
        if (msgs.length > 0) {
          if (afterId) {
            setMessages(prev => [...prev, ...msgs])
          } else {
            setMessages(msgs)
          }
          lastIdRef.current = msgs[msgs.length - 1].id
        }
      } catch {
        // ignore polling errors
      }
    },
    [conversationId]
  )

  useEffect(() => {
    if (!conversationId) return
    setMessages([])
    setLoading(true)
    lastIdRef.current = 0

    fetchMessages().finally(() => setLoading(false))
  }, [conversationId, fetchMessages])

  useEffect(() => {
    if (!conversationId) return

    const poll = () => {
      if (lastIdRef.current > 0) {
        fetchMessages(lastIdRef.current)
      }
    }

    const start = () => {
      if (!intervalRef.current) {
        intervalRef.current = setInterval(poll, 5_000)
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
  }, [conversationId, fetchMessages])

  const addOptimistic = useCallback((msg: MessageResponse) => {
    setMessages(prev => [...prev, msg])
    lastIdRef.current = msg.id
  }, [])

  return { messages, loading, addOptimistic }
}
