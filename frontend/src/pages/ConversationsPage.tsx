import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { conversationsApi, type ConversationSummary } from '../api/conversations'
import { Spinner } from '../components/Spinner'

export function ConversationsPage() {
  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [loading, setLoading] = useState(true)
  const nav = useNavigate()

  useEffect(() => {
    conversationsApi.list()
      .then(setConversations)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <main className="max-w-2xl mx-auto p-6"><Spinner /></main>

  if (conversations.length === 0) {
    return (
      <main className="max-w-2xl mx-auto p-6">
        <div className="text-center py-12 text-gray-500">
          <svg className="mx-auto h-16 w-16 text-gray-300 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
              d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
          </svg>
          <p className="text-lg mb-2">No conversations yet</p>
          <Link to="/browse" className="text-sm text-blue-600 hover:underline">
            Browse listings to start a conversation
          </Link>
        </div>
      </main>
    )
  }

  return (
    <main className="max-w-2xl mx-auto p-6">
      <h1 className="text-xl font-bold mb-4">Messages</h1>
      <ul className="divide-y divide-gray-200">
        {conversations.map(conv => (
          <li key={conv.id}>
            <button
              onClick={() => nav(`/conversations/${conv.id}`)}
              className="w-full flex items-center gap-3 py-3 px-2 hover:bg-gray-50 rounded text-left"
            >
              <img
                src={conv.listingImageUrl}
                alt={conv.listingTitle}
                className="w-14 h-14 rounded object-cover bg-gray-100 flex-shrink-0"
              />
              <div className="flex-1 min-w-0">
                <div className="flex justify-between items-baseline">
                  <span className="font-medium text-sm truncate">
                    {conv.counterpartNickname}
                  </span>
                  <span className="text-xs text-gray-400 flex-shrink-0">
                    {formatRelativeTime(conv.lastMessageAt)}
                  </span>
                </div>
                <p className="text-xs text-gray-500 truncate">{conv.listingTitle}</p>
                <p className="text-sm text-gray-600 truncate">
                  {conv.lastMessagePreview || 'No messages yet'}
                </p>
              </div>
              {conv.unreadCount > 0 && (
                <span className="w-5 h-5 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center flex-shrink-0">
                  {conv.unreadCount > 9 ? '9+' : conv.unreadCount}
                </span>
              )}
            </button>
          </li>
        ))}
      </ul>
    </main>
  )
}

function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60_000)
  if (diffMin < 1) return 'now'
  if (diffMin < 60) return `${diffMin}m`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}h`
  const diffDay = Math.floor(diffHr / 24)
  if (diffDay < 7) return `${diffDay}d`
  return date.toLocaleDateString()
}
