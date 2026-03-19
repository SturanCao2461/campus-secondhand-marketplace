package com.campus.marketplace.controller.favorites;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Favorites controller.
 * TODO: Implement favorites management for authenticated users.
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getFavorites() {
        // TODO: Return favorite listings for the authenticated user
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{listingId}")
    public ResponseEntity<Map<String, String>> addFavorite(@PathVariable Long listingId) {
        // TODO: Add listing to user's favorites
        return ResponseEntity.ok(Map.of("message", "Add favorite placeholder"));
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Map<String, String>> removeFavorite(@PathVariable Long listingId) {
        // TODO: Remove listing from user's favorites
        return ResponseEntity.ok(Map.of("message", "Remove favorite placeholder"));
    }
}
