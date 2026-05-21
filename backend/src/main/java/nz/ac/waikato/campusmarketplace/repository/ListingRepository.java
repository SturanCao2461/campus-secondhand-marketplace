package nz.ac.waikato.campusmarketplace.repository;

import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    Page<Listing> findByOwner(User owner, Pageable pageable);

    Page<Listing> findByOwnerAndStatus(User owner, ListingStatus status, Pageable pageable);

    Page<Listing> findByOwnerAndStatusNot(User owner, ListingStatus status, Pageable pageable);

    Optional<Listing> findByImagePath(String imagePath);

    @Query("SELECT l FROM Listing l JOIN FETCH l.owner WHERE l.id = :id")
    Optional<Listing> findByIdWithOwner(@Param("id") Long id);
}
