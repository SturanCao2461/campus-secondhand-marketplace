package nz.ac.waikato.campusmarketplace.dto;

import nz.ac.waikato.campusmarketplace.entity.Listing;
import org.springframework.data.domain.Page;

import java.util.List;

public record PagedListings(
        List<ListingSummary> items,
        int page,
        int pageSize,
        int totalPages,
        long totalItems
) {
    public static PagedListings from(Page<Listing> page) {
        List<ListingSummary> items = page.getContent().stream()
                .map(ListingSummary::from)
                .toList();
        return new PagedListings(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }
}
