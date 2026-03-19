package com.campus.marketplace.repository.favorites;

import com.campus.marketplace.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Favorite repository.
 */
@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserId(Long userId);
    Optional<Favorite> findByUserIdAndListingId(Long userId, Long listingId);
    boolean existsByUserIdAndListingId(Long userId, Long listingId);
}
