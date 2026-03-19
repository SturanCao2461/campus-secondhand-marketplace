package com.campus.marketplace.service.messages;

import org.springframework.stereotype.Service;

/**
 * Messages service.
 * TODO: Implement conversation and message management.
 */
@Service
public class MessageService {

    public Object getConversations(Long userId) {
        // TODO: Return paginated conversations for user
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Object getMessages(Long conversationId) {
        // TODO: Return messages in a conversation
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void sendMessage(Long senderId, Long receiverId, String content) {
        // TODO: Persist message, create or update conversation
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
