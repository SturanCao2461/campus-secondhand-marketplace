package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.dto.ConversationDetail;
import nz.ac.waikato.campusmarketplace.dto.ConversationSummary;
import nz.ac.waikato.campusmarketplace.dto.MessageResponse;
import nz.ac.waikato.campusmarketplace.dto.UnreadCountResponse;
import nz.ac.waikato.campusmarketplace.entity.Conversation;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.Message;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.ConversationRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConversationServiceTest {

    private ConversationRepository conversations;
    private MessageRepository messages;
    private ListingRepository listings;
    private RateLimitService rateLimit;
    private ConversationService svc;

    private User buyer;
    private User seller;
    private Listing listing;
    private Conversation conv;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        messages = mock(MessageRepository.class);
        listings = mock(ListingRepository.class);
        rateLimit = mock(RateLimitService.class);
        svc = new ConversationService(conversations, messages, listings, rateLimit);

        buyer = User.builder().id(1L).email("buyer@x").nickname("Buyer").emailVerified(true).build();
        seller = User.builder().id(2L).email("seller@x").nickname("Seller").emailVerified(true).build();

        listing = Listing.builder().id(10L).title("Test Item").imagePath("listings/test.jpg").owner(seller).build();

        conv = Conversation.builder()
                .id(100L)
                .listing(listing)
                .buyer(buyer)
                .seller(seller)
                .buyerLastReadAt(LocalDateTime.now().minusHours(1))
                .sellerLastReadAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusMinutes(30))
                .build();

        when(rateLimit.increment(anyString(), any())).thenReturn(1L);
        when(rateLimit.check(anyString(), anyLong(), any())).thenReturn(RateLimitDecision.allowed());
    }

    @Test
    void createOrGet_createsNewConversation() {
        when(listings.findById(10L)).thenReturn(Optional.of(listing));
        when(conversations.findByListingIdAndBuyerId(10L, 1L)).thenReturn(Optional.empty());
        when(conversations.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(100L);
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        ConversationDetail detail = svc.createOrGetConversation(10L, buyer);

        assertThat(detail.id()).isEqualTo(100L);
        assertThat(detail.listingId()).isEqualTo(10L);
        assertThat(detail.counterpartNickname()).isEqualTo("Seller");
        verify(conversations).save(any(Conversation.class));
    }

    @Test
    void createOrGet_returnsExistingConversation() {
        when(listings.findById(10L)).thenReturn(Optional.of(listing));
        when(conversations.findByListingIdAndBuyerId(10L, 1L)).thenReturn(Optional.of(conv));

        ConversationDetail detail = svc.createOrGetConversation(10L, buyer);

        assertThat(detail.id()).isEqualTo(100L);
        verify(conversations, never()).save(any());
    }

    @Test
    void createOrGet_sellerCannotMessageSelf() {
        when(listings.findById(10L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> svc.createOrGetConversation(10L, seller))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CANNOT_MESSAGE_SELF));
    }

    @Test
    void createOrGet_listingNotFound() {
        when(listings.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.createOrGetConversation(99L, buyer))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.LISTING_NOT_FOUND));
    }

    @Test
    void getConversationDetail_nonParticipantForbidden() {
        User stranger = User.builder().id(99L).email("s@x").nickname("Stranger").emailVerified(true).build();
        when(conversations.findById(100L)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> svc.getConversationDetail(100L, stranger))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CONVERSATION_FORBIDDEN));
    }

    @Test
    void getConversationDetail_notFound() {
        when(conversations.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.getConversationDetail(999L, buyer))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    void sendMessage_success() {
        when(conversations.findById(100L)).thenReturn(Optional.of(conv));
        when(messages.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(500L);
            m.setCreatedAt(LocalDateTime.now());
            return m;
        });
        when(conversations.save(any(Conversation.class))).thenReturn(conv);

        MessageResponse resp = svc.sendMessage(100L, buyer, "Hello!");

        assertThat(resp.id()).isEqualTo(500L);
        assertThat(resp.content()).isEqualTo("Hello!");
        assertThat(resp.senderNickname()).isEqualTo("Buyer");
        verify(conversations).save(conv);
    }

    @Test
    void sendMessage_nonParticipantForbidden() {
        User stranger = User.builder().id(99L).email("s@x").nickname("Stranger").emailVerified(true).build();
        when(conversations.findById(100L)).thenReturn(Optional.of(conv));

        assertThatThrownBy(() -> svc.sendMessage(100L, stranger, "Hi"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CONVERSATION_FORBIDDEN));
    }

    @Test
    void sendMessage_rateLimitExceeded() {
        when(conversations.findById(100L)).thenReturn(Optional.of(conv));
        when(rateLimit.check(eq("ratelimit:message:send:1"), eq(30L), any()))
                .thenReturn(RateLimitDecision.blocked(45L));

        assertThatThrownBy(() -> svc.sendMessage(100L, buyer, "Spam"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS));
    }

    @Test
    void getMessages_updatesLastReadAt() {
        when(conversations.findById(100L)).thenReturn(Optional.of(conv));
        when(messages.findAllByConversation(eq(100L), any())).thenReturn(List.of());
        when(conversations.save(any(Conversation.class))).thenReturn(conv);

        LocalDateTime before = conv.getBuyerLastReadAt();
        svc.getMessages(100L, buyer, null, 50);

        verify(conversations).save(conv);
        assertThat(conv.getBuyerLastReadAt()).isAfter(before);
    }

    @Test
    void getMessages_sellerLastReadAtUpdated() {
        when(conversations.findById(100L)).thenReturn(Optional.of(conv));
        when(messages.findAllByConversation(eq(100L), any())).thenReturn(List.of());
        when(conversations.save(any(Conversation.class))).thenReturn(conv);

        LocalDateTime before = conv.getSellerLastReadAt();
        svc.getMessages(100L, seller, null, 50);

        verify(conversations).save(conv);
        assertThat(conv.getSellerLastReadAt()).isAfter(before);
    }

    @Test
    void getUnreadCount_sumsAcrossConversations() {
        Conversation conv2 = Conversation.builder()
                .id(101L).listing(listing).buyer(buyer).seller(seller)
                .buyerLastReadAt(LocalDateTime.now().minusHours(2))
                .sellerLastReadAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();

        when(conversations.findAllByParticipant(1L)).thenReturn(List.of(conv, conv2));
        when(messages.countUnread(eq(100L), eq(1L), any())).thenReturn(3L);
        when(messages.countUnread(eq(101L), eq(1L), any())).thenReturn(2L);

        UnreadCountResponse resp = svc.getUnreadCount(buyer);

        assertThat(resp.total()).isEqualTo(5L);
    }

    @Test
    void getConversationsForUser_returnsSummaries() {
        when(conversations.findAllByParticipant(1L)).thenReturn(List.of(conv));
        when(messages.countUnread(eq(100L), eq(1L), any())).thenReturn(1L);
        when(messages.findLastMessage(100L)).thenReturn(Optional.of(
                Message.builder().id(50L).conversation(conv).sender(seller)
                        .content("Last msg").createdAt(LocalDateTime.now()).build()
        ));

        List<ConversationSummary> result = svc.getConversationsForUser(buyer);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).counterpartNickname()).isEqualTo("Seller");
        assertThat(result.get(0).lastMessagePreview()).isEqualTo("Last msg");
        assertThat(result.get(0).unreadCount()).isEqualTo(1L);
    }

    @Test
    void createOrGet_rateLimitOnCreate() {
        when(listings.findById(10L)).thenReturn(Optional.of(listing));
        when(conversations.findByListingIdAndBuyerId(10L, 1L)).thenReturn(Optional.empty());
        when(rateLimit.check(eq("ratelimit:conversation:create:1"), eq(10L), any()))
                .thenReturn(RateLimitDecision.blocked(30L));

        assertThatThrownBy(() -> svc.createOrGetConversation(10L, buyer))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS));
    }
}
