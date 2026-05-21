import { api } from './apiClient'

export type ConversationSummary = {
  id: number
  listingId: number
  listingTitle: string
  listingImageUrl: string
  counterpartId: number
  counterpartNickname: string
  lastMessagePreview: string
  lastMessageAt: string
  unreadCount: number
}

export type ConversationDetail = {
  id: number
  listingId: number
  listingTitle: string
  listingImageUrl: string
  counterpartId: number
  counterpartNickname: string
  createdAt: string
}

export type MessageResponse = {
  id: number
  conversationId: number
  senderId: number
  senderNickname: string
  content: string
  createdAt: string
}

export type UnreadCountResponse = {
  total: number
}

export const conversationsApi = {
  create: (listingId: number) =>
    api.post<ConversationDetail>('/api/conversations', { listingId }),

  list: () =>
    api.get<ConversationSummary[]>('/api/conversations'),

  getOne: (id: number) =>
    api.get<ConversationDetail>(`/api/conversations/${id}`),

  getMessages: (id: number, after?: number, limit = 50) => {
    const params = new URLSearchParams()
    if (after) params.set('after', String(after))
    params.set('limit', String(limit))
    return api.get<MessageResponse[]>(`/api/conversations/${id}/messages?${params}`)
  },

  sendMessage: (id: number, content: string) =>
    api.post<MessageResponse>(`/api/conversations/${id}/messages`, { content }),

  unreadCount: () =>
    api.get<UnreadCountResponse>('/api/conversations/unread-count'),
}
