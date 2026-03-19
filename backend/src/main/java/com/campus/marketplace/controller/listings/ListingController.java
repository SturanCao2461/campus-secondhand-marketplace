package com.campus.marketplace.controller.listings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Listings controller.
 * TODO: Wire up ListingService and return real data.
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllListings() {
        // TODO: Fetch paginated listings, support filtering by category/price/condition
        return ResponseEntity.ok(List.of(
            Map.of("id", 1, "title", "Placeholder listing", "price", 0)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getListingById(@PathVariable Long id) {
        // TODO: Fetch listing by ID from database
        return ResponseEntity.ok(Map.of("id", id, "title", "Placeholder listing detail"));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> getMyListings() {
        // TODO: Return listings for the currently authenticated user
        return ResponseEntity.ok(List.of());
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createListing(@RequestBody Map<String, Object> request) {
        // TODO: Validate request, save to DB, associate with authenticated user
        return ResponseEntity.ok(Map.of("message", "Create listing placeholder"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> updateListing(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        // TODO: Validate ownership, update listing fields
        return ResponseEntity.ok(Map.of("message", "Update listing placeholder"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteListing(@PathVariable Long id) {
        // TODO: Validate ownership, soft-delete or hard-delete
        return ResponseEntity.ok(Map.of("message", "Delete listing placeholder"));
    }
}
