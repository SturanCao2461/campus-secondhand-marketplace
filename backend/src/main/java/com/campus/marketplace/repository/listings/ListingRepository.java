package com.campus.marketplace.repository.listings;

import com.campus.marketplace.model.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Listing repository.
 * TODO: Add custom queries for search, filtering, category lookup.
 */
@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {
    List<Listing> findBySellerId(Long sellerId);
    // TODO: Add findByCategory, findByPriceLessThan, full-text search
}
