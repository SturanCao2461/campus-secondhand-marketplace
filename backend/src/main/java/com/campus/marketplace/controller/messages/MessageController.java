package com.campus.marketplace.controller.messages;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Messages controller.
 * TODO: Implement in-app messaging between buyers and sellers.
 * TODO: Consider WebSocket for real-time messaging.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getConversations() {
        // TODO: Return all conversations for the authenticated user
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable Long conversationId) {
        // TODO: Return messages for a specific conversation
        return ResponseEntity.ok(List.of());
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> sendMessage(@RequestBody Map<String, Object> request) {
        // TODO: Save message, notify recipient
        return ResponseEntity.ok(Map.of("message", "Send message placeholder"));
    }
}
