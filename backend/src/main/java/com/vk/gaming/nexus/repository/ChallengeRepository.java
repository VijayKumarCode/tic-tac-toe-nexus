package com.vk.gaming.nexus.repository;

import com.vk.gaming.nexus.dto.ChallengeStatus;
import com.vk.gaming.nexus.model.ChallengeEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;   // Spring, not jakarta

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/*
 * BUG FIX: @Repository annotation was missing.
 * Spring Data usually discovers repositories without it, but its absence
 * means Spring's exception translation (converting SQL exceptions to
 * Spring DataAccessException) is NOT applied. Any unchecked JDBC exception
 * would bubble up as a raw SQLException instead of a clean Spring exception.
 */
@Repository
public interface ChallengeRepository extends JpaRepository<ChallengeEntity, Long> {

    // Returns most recent challenge for a room — uses createdAt which is now populated
    Optional<ChallengeEntity> findTopByRoomIdOrderByCreatedAtDesc(String roomId);

    @Transactional
    void deleteByRoomId(String roomId);

    @Query("""
        SELECT c FROM ChallengeEntity c
        WHERE (c.sender = :username OR c.receiver = :username)
        AND c.status = :status
    """)
    List<ChallengeEntity> findPendingByUser(
            @Param("username") String username,
            @Param("status") ChallengeStatus status
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE ChallengeEntity c
        SET c.status = :cancelled
        WHERE (c.sender = :username OR c.receiver = :username)
        AND c.status = :pending
    """)
    int cancelAllPendingForUser(
            @Param("username")  String username,
            @Param("cancelled") ChallengeStatus cancelled,
            @Param("pending")   ChallengeStatus pending
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE ChallengeEntity c
        SET c.status = :cancelled
        WHERE c.status = :pending
        AND c.expiresAt < :now
    """)
    int cancelExpiredChallenges(
            @Param("now")       LocalDateTime now,
            @Param("cancelled") ChallengeStatus cancelled,
            @Param("pending")   ChallengeStatus pending
    );

    List<ChallengeEntity> findByStatusAndExpiresAtBefore(
            ChallengeStatus status,
            LocalDateTime now
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE ChallengeEntity c
        SET c.status = :cancelled
        WHERE c.sender = :username
        AND c.roomId != :roomId
        AND c.status = :pending
    """)
    int cancelOtherPending(
            @Param("username")  String username,
            @Param("roomId")    String roomId,
            @Param("cancelled") ChallengeStatus cancelled,
            @Param("pending")   ChallengeStatus pending
    );
}