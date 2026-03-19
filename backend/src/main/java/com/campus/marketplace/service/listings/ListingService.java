package com.campus.marketplace.service.listings;

import org.springframework.stereotype.Service;

/**
 * Listings service.
 * TODO: Implement CRUD operations for listings.
 * TODO: Add search and filtering support.
 */
@Service
public class ListingService {

    public Object getAllListings() {
        // TODO: Query database with pagination and filters
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Object getListingById(Long id) {
        // TODO: Fetch by ID, throw NotFoundException if not found
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void createListing(Object dto) {
        // TODO: Validate, persist, associate with user
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void updateListing(Long id, Object dto) {
        // TODO: Check ownership, update fields
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void deleteListing(Long id) {
        // TODO: Check ownership, delete or mark as inactive
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
