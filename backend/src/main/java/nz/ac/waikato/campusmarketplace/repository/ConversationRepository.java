package nz.ac.waikato.campusmarketplace.repository;

import nz.ac.waikato.campusmarketplace.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByListingIdAndBuyerId(Long listingId, Long buyerId);

    @Query("SELECT c FROM Conversation c WHERE c.buyer.id = :userId OR c.seller.id = :userId ORDER BY c.updatedAt DESC")
    List<Conversation> findAllByParticipant(@Param("userId") Long userId);
}
