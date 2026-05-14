package nz.ac.waikato.campusmarketplace.controller;

import jakarta.validation.Valid;
import nz.ac.waikato.campusmarketplace.dto.ChangeStatusRequest;
import nz.ac.waikato.campusmarketplace.dto.CreateListingRequest;
import nz.ac.waikato.campusmarketplace.dto.ListingResponse;
import nz.ac.waikato.campusmarketplace.dto.PagedListings;
import nz.ac.waikato.campusmarketplace.dto.UpdateListingRequest;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.filter.AuthPrincipal;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import nz.ac.waikato.campusmarketplace.service.ListingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private static final int PAGE_SIZE = 12;
    // Phase 1 has no real image upload; T19 will replace this with a path
    // returned by ImageStorageService.store(file).
    private static final String PLACEHOLDER_IMAGE_PATH = "listings/placeholder.jpg";

    private final ListingService listingService;
    private final UserRepository users;

    public ListingController(ListingService listingService, UserRepository users) {
        this.listingService = listingService;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @RequestBody @Valid CreateListingRequest req) {
        ListingResponse body = listingService.create(currentUser(principal), req, PLACEHOLDER_IMAGE_PATH);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/me")
    public ResponseEntity<PagedListings> listMine(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(required = false) ListingStatus status,
                                                  @RequestParam(defaultValue = "CREATED_DESC") String sort,
                                                  @RequestParam(defaultValue = "false") boolean includeRemoved) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, mapSort(sort));
        PagedListings body = listingService.listMine(currentUser(principal), pageable, status, includeRemoved);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getOne(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long id) {
        return ResponseEntity.ok(listingService.getOne(currentUser(principal), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ListingResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long id,
                                                  @RequestBody @Valid UpdateListingRequest req) {
        // Phase 1: no image upload; pass null so the existing image_path is kept.
        // T19 will pass imageStorage.store(file) when a new image part is present.
        ListingResponse body = listingService.update(currentUser(principal), id, req, null);
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ListingResponse> changeStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable Long id,
                                                        @RequestBody @Valid ChangeStatusRequest req) {
        return ResponseEntity.ok(listingService.changeStatus(currentUser(principal), id, req.newStatus()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long id) {
        listingService.remove(currentUser(principal), id);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(AuthPrincipal principal) {
        // getReferenceById returns a lazy proxy with the id set; no SELECT until a field is read.
        // Service-layer code only uses the id (FK association), so this avoids an extra roundtrip.
        return users.getReferenceById(principal.userId());
    }

    private Sort mapSort(String sortKey) {
        return switch (sortKey) {
            case "CREATED_ASC" -> Sort.by("createdAt").ascending();
            case "PRICE_DESC"  -> Sort.by("price").descending();
            case "PRICE_ASC"   -> Sort.by("price").ascending();
            default            -> Sort.by("createdAt").descending();
        };
    }
}
