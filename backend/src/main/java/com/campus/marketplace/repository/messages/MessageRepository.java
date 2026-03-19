package com.campus.marketplace.repository.messages;

import com.campus.marketplace.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Message repository.
 * TODO: Add conversation-level queries.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySenderIdOrReceiverIdOrderByCreatedAtAsc(Long senderId, Long receiverId);
    // TODO: Add findConversations method
}
