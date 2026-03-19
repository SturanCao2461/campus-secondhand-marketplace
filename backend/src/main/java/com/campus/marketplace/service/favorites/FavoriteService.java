package com.campus.marketplace.service.favorites;

import org.springframework.stereotype.Service;

/**
 * Favorites service.
 * TODO: Implement favorites management.
 */
@Service
public class FavoriteService {

    public Object getFavorites(Long userId) {
        // TODO: Return paginated favorites for user
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void addFavorite(Long userId, Long listingId) {
        // TODO: Create Favorite entity, prevent duplicates
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void removeFavorite(Long userId, Long listingId) {
        // TODO: Remove favorite entity
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
