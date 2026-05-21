package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.dto.*;
import nz.ac.waikato.campusmarketplace.entity.Conversation;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.Message;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.ConversationRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.repository.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationService {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final long SEND_LIMIT = 30;
    private static final long CREATE_LIMIT = 10;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ListingRepository listings;
    private final RateLimitService rateLimit;

    public ConversationService(ConversationRepository conversations,
                               MessageRepository messages,
                               ListingRepository listings,
                               RateLimitService rateLimit) {
        this.conversations = conversations;
        this.messages = messages;
        this.listings = listings;
        this.rateLimit = rateLimit;
    }

    @Transactional
    public ConversationDetail createOrGetConversation(Long listingId, User buyer) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new ApiException(ErrorCode.LISTING_NOT_FOUND, "Listing not found"));

        if (listing.getOwner().getId().equals(buyer.getId())) {
            throw new ApiException(ErrorCode.CANNOT_MESSAGE_SELF, "Cannot start a conversation on your own listing");
        }

        Conversation conv = conversations.findByListingIdAndBuyerId(listingId, buyer.getId())
                .orElseGet(() -> {
                    enforceCreateLimit(buyer.getId());
                    Conversation c = Conversation.builder()
                            .listing(listing)
                            .buyer(buyer)
                            .seller(listing.getOwner())
                            .buyerLastReadAt(LocalDateTime.now())
                            .sellerLastReadAt(LocalDateTime.now())
                            .build();
                    return conversations.save(c);
                });

        return toDetail(conv, buyer.getId());
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> getConversationsForUser(User user) {
        List<Conversation> convs = conversations.findAllByParticipant(user.getId());
        return convs.stream().map(c -> toSummary(c, user.getId())).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail getConversationDetail(Long conversationId, User user) {
        Conversation conv = findAndVerifyParticipant(conversationId, user.getId());
        return toDetail(conv, user.getId());
    }

    @Transactional
    public List<MessageResponse> getMessages(Long conversationId, User user, Long afterId, int limit) {
        Conversation conv = findAndVerifyParticipant(conversationId, user.getId());

        int capped = Math.min(Math.max(limit, 1), 100);
        List<Message> msgs;
        if (afterId != null && afterId > 0) {
            msgs = messages.findAfterCursor(conversationId, afterId, PageRequest.of(0, capped));
        } else {
            msgs = messages.findAllByConversation(conversationId, PageRequest.of(0, capped));
        }

        updateLastReadAt(conv, user.getId());
        conversations.save(conv);

        return msgs.stream().map(this::toMessageResponse).toList();
    }

    @Transactional
    public MessageResponse sendMessage(Long conversationId, User sender, String content) {
        Conversation conv = findAndVerifyParticipant(conversationId, sender.getId());
        enforceSendLimit(sender.getId());

        Message msg = Message.builder()
                .conversation(conv)
                .sender(sender)
                .content(content)
                .build();
        msg = messages.save(msg);

        conv.setUpdatedAt(LocalDateTime.now());
        conversations.save(conv);

        return toMessageResponse(msg);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(User user) {
        List<Conversation> convs = conversations.findAllByParticipant(user.getId());
        long total = convs.stream()
                .mapToLong(c -> countUnreadForUser(c, user.getId()))
                .sum();
        return new UnreadCountResponse(total);
    }

    private Conversation findAndVerifyParticipant(Long conversationId, Long userId) {
        Conversation conv = conversations.findById(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found"));
        if (!conv.getBuyer().getId().equals(userId) && !conv.getSeller().getId().equals(userId)) {
            throw new ApiException(ErrorCode.CONVERSATION_FORBIDDEN, "Not a participant of this conversation");
        }
        return conv;
    }

    private long countUnreadForUser(Conversation conv, Long userId) {
        LocalDateTime since = conv.getBuyer().getId().equals(userId)
                ? conv.getBuyerLastReadAt()
                : conv.getSellerLastReadAt();
        if (since == null) since = conv.getCreatedAt();
        return messages.countUnread(conv.getId(), userId, since);
    }

    private void updateLastReadAt(Conversation conv, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        if (conv.getBuyer().getId().equals(userId)) {
            conv.setBuyerLastReadAt(now);
        } else {
            conv.setSellerLastReadAt(now);
        }
    }

    private void enforceSendLimit(Long userId) {
        String key = "ratelimit:message:send:" + userId;
        rateLimit.increment(key, RATE_WINDOW);
        RateLimitDecision decision = rateLimit.check(key, SEND_LIMIT, RATE_WINDOW);
        if (decision.exceeded()) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "Message rate limit exceeded. Try again later.", decision.retryAfterSeconds());
        }
    }

    private void enforceCreateLimit(Long userId) {
        String key = "ratelimit:conversation:create:" + userId;
        rateLimit.increment(key, RATE_WINDOW);
        RateLimitDecision decision = rateLimit.check(key, CREATE_LIMIT, RATE_WINDOW);
        if (decision.exceeded()) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "Conversation creation rate limit exceeded. Try again later.", decision.retryAfterSeconds());
        }
    }

    private ConversationDetail toDetail(Conversation conv, Long userId) {
        User counterpart = conv.getBuyer().getId().equals(userId) ? conv.getSeller() : conv.getBuyer();
        Listing listing = conv.getListing();
        return new ConversationDetail(
                conv.getId(),
                listing.getId(),
                listing.getTitle(),
                "/api/uploads/" + listing.getImagePath(),
                counterpart.getId(),
                counterpart.getNickname(),
                conv.getCreatedAt()
        );
    }

    private ConversationSummary toSummary(Conversation conv, Long userId) {
        User counterpart = conv.getBuyer().getId().equals(userId) ? conv.getSeller() : conv.getBuyer();
        Listing listing = conv.getListing();
        long unread = countUnreadForUser(conv, userId);

        String preview = "";
        LocalDateTime lastAt = conv.getUpdatedAt();
        var lastMsg = messages.findLastMessage(conv.getId());
        if (lastMsg.isPresent()) {
            Message m = lastMsg.get();
            preview = m.getContent().length() > 60 ? m.getContent().substring(0, 60) + "..." : m.getContent();
            lastAt = m.getCreatedAt();
        }

        return new ConversationSummary(
                conv.getId(),
                listing.getId(),
                listing.getTitle(),
                "/api/uploads/" + listing.getImagePath(),
                counterpart.getId(),
                counterpart.getNickname(),
                preview,
                lastAt,
                unread
        );
    }

    private MessageResponse toMessageResponse(Message msg) {
        return new MessageResponse(
                msg.getId(),
                msg.getConversation().getId(),
                msg.getSender().getId(),
                msg.getSender().getNickname(),
                msg.getContent(),
                msg.getCreatedAt()
        );
    }
}
