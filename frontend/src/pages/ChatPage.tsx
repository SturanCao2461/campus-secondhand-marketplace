import { useState, useEffect, useRef } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { conversationsApi, type ConversationDetail } from '../api/conversations'
import { useAuth } from '../auth/useAuth'
import { useChatPolling } from '../hooks/useChatPolling'
import { useBrowserNotification } from '../hooks/useBrowserNotification'
import { Spinner } from '../components/Spinner'

export function ChatPage() {
  const { id } = useParams<{ id: string }>()
  const convId = id ? Number(id) : null
  const { user } = useAuth()
  const nav = useNavigate()
  const [conv, setConv] = useState<ConversationDetail | null>(null)
  const [loadingConv, setLoadingConv] = useState(true)
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const { messages, loading: loadingMsgs, addOptimistic } = useChatPolling(convId)
  const { notify } = useBrowserNotification()
  const prevCountRef = useRef(0)

  useEffect(() => {
    if (!convId) return
    conversationsApi
      .getOne(convId)
      .then(setConv)
      .catch(() => nav('/conversations'))
      .finally(() => setLoadingConv(false))
  }, [convId, nav])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  useEffect(() => {
    if (messages.length > prevCountRef.current && prevCountRef.current > 0) {
      const latest = messages[messages.length - 1]
      if (latest.senderId !== user?.id && conv) {
        notify(conv.counterpartNickname, latest.content, () => nav(`/conversations/${convId}`))
      }
    }
    prevCountRef.current = messages.length
  }, [messages.length, user?.id, conv, notify, nav, convId])

  const handleSend = async () => {
    if (!convId || !input.trim() || sending) return
    const content = input.trim()
    setInput('')
    setSending(true)
    try {
      const msg = await conversationsApi.sendMessage(convId, content)
      addOptimistic(msg)
    } catch {
      setInput(content)
    } finally {
      setSending(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  if (loadingConv || loadingMsgs) {
    return (
      <main className="max-w-2xl mx-auto px-6 py-10">
        <Spinner />
      </main>
    )
  }
  if (!conv) return null

  return (
    <main className="max-w-2xl mx-auto flex flex-col h-[calc(100vh-57px)]">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-border-soft bg-card sticky top-0 z-10">
        <Link to="/conversations" className="text-muted hover:text-plum transition-colors">
          &larr;
        </Link>
        <img
          src={conv.listingImageUrl}
          alt=""
          className="w-10 h-10 rounded-card object-cover bg-mustard/30"
        />
        <div className="min-w-0">
          <p className="font-semibold text-sm text-plum truncate">{conv.counterpartNickname}</p>
          <Link
            to={`/listings/${conv.listingId}/detail`}
            className="text-xs text-coral hover:underline truncate block font-medium"
          >
            {conv.listingTitle}
          </Link>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-2">
        {messages.map((msg, i) => {
          const isOwn = msg.senderId === user?.id
          const showDate = i === 0 || !sameDay(messages[i - 1].createdAt, msg.createdAt)
          return (
            <div key={msg.id}>
              {showDate && (
                <p className="text-center text-xs text-muted my-3 font-medium">
                  {new Date(msg.createdAt).toLocaleDateString()}
                </p>
              )}
              <div className={`flex ${isOwn ? 'justify-end' : 'justify-start'}`}>
                <div
                  className={`max-w-[75%] px-4 py-2 rounded-panel text-sm whitespace-pre-wrap shadow-sm ${
                    isOwn ? 'bg-plum text-surface' : 'bg-card text-plum border border-border-soft'
                  }`}
                >
                  {msg.content}
                  <p className={`text-[10px] mt-1 ${isOwn ? 'text-surface/70' : 'text-muted'}`}>
                    {new Date(msg.createdAt).toLocaleTimeString([], {
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </p>
                </div>
              </div>
            </div>
          )
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Composer */}
      <div className="border-t border-border-soft bg-card px-4 py-3 flex gap-2 items-end">
        <textarea
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message..."
          maxLength={1000}
          rows={1}
          className="flex-1 resize-none border border-border-soft bg-surface rounded-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-coral/30 focus:border-coral"
        />
        <button
          onClick={handleSend}
          disabled={!input.trim() || sending}
          className="rounded-full bg-plum px-5 py-2 text-surface text-sm font-bold hover:bg-ink disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-button"
        >
          Send
        </button>
      </div>
      {input.length > 900 && (
        <p className="text-xs text-muted px-4 pb-1 text-right">{input.length}/1000</p>
      )}
    </main>
  )
}

function sameDay(a: string, b: string): boolean {
  return new Date(a).toDateString() === new Date(b).toDateString()
}
