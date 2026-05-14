package nz.ac.waikato.campusmarketplace.repository;

import nz.ac.waikato.campusmarketplace.entity.Listing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {
}
