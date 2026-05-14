package nz.ac.waikato.campusmarketplace.controller;

import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.filter.AuthPrincipal;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.service.ImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class ImageController {

    private final ListingRepository listings;
    private final ImageStorageService imageStorage;

    public ImageController(ListingRepository listings, ImageStorageService imageStorage) {
        this.listings = listings;
        this.imageStorage = imageStorage;
    }

    @GetMapping("/api/uploads/listings/{filename:.+}")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Invalid image path.");
        }

        String relativePath = "listings/" + filename;
        Listing listing = listings.findByImagePath(relativePath)
                .orElseThrow(() -> new ApiException(ErrorCode.LISTING_NOT_FOUND, "Image not found."));

        if (!listing.getOwner().getId().equals(principal.userId())) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Image not found.");
        }

        Resource resource = imageStorage.load(relativePath);
        MediaType contentType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(resource);
    }
}
