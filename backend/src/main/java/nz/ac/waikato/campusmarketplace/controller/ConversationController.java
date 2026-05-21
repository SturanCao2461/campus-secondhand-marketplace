package nz.ac.waikato.campusmarketplace.controller;

import jakarta.validation.Valid;
import nz.ac.waikato.campusmarketplace.dto.*;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.filter.AuthPrincipal;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import nz.ac.waikato.campusmarketplace.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository users;

    public ConversationController(ConversationService conversationService,
                                  UserRepository users) {
        this.conversationService = conversationService;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<ConversationDetail> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody @Valid CreateConversationRequest req) {
        User buyer = currentUser(principal);
        ConversationDetail detail = conversationService.createOrGetConversation(req.listingId(), buyer);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @GetMapping
    public ResponseEntity<List<ConversationSummary>> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        User user = currentUser(principal);
        return ResponseEntity.ok(conversationService.getConversationsForUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationDetail> getOne(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id) {
        User user = currentUser(principal);
        return ResponseEntity.ok(conversationService.getConversationDetail(id, user));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) Long after,
            @RequestParam(defaultValue = "50") int limit) {
        User user = currentUser(principal);
        return ResponseEntity.ok(conversationService.getMessages(id, user, after, limit));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody @Valid SendMessageRequest req) {
        User sender = currentUser(principal);
        MessageResponse msg = conversationService.sendMessage(id, sender, req.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(msg);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal AuthPrincipal principal) {
        User user = currentUser(principal);
        return ResponseEntity.ok(conversationService.getUnreadCount(user));
    }

    private User currentUser(AuthPrincipal principal) {
        return users.getReferenceById(principal.userId());
    }
}
