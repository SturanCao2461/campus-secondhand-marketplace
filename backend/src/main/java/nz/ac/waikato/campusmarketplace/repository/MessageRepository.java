package nz.ac.waikato.campusmarketplace.repository;

import nz.ac.waikato.campusmarketplace.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId AND m.id > :afterId ORDER BY m.createdAt ASC")
    List<Message> findAfterCursor(@Param("convId") Long conversationId,
                                  @Param("afterId") Long afterId,
                                  org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId ORDER BY m.createdAt ASC")
    List<Message> findAllByConversation(@Param("convId") Long conversationId,
                                        org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :convId AND m.sender.id != :userId AND m.createdAt > :since")
    long countUnread(@Param("convId") Long conversationId,
                     @Param("userId") Long userId,
                     @Param("since") LocalDateTime since);

    @Query("SELECT m FROM Message m WHERE m.conversation.id = :convId ORDER BY m.createdAt DESC LIMIT 1")
    java.util.Optional<Message> findLastMessage(@Param("convId") Long conversationId);
}
