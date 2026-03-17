/**
 * Problem No. #143
 * Difficulty: Hard
 * Description: Corrected JPQL type-safety issues for Enum cancellation updates
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(n)
 * Space Complexity: O(1)
 */
package com.vk.gaming.nexus.repository;

import com.vk.gaming.nexus.model.ChallengeEntity;
import com.vk.gaming.nexus.dto.ChallengeMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChallengeRepository extends JpaRepository<ChallengeEntity, Long> {
    Optional<ChallengeEntity> findByRoomId(String roomId);

    @Modifying
    @Query("UPDATE ChallengeEntity c SET c.status = :cancelledStatus " +
            "WHERE (c.sender = :username OR c.receiver = :username) " +
            "AND c.status = :pendingStatus AND c.roomId <> :activeRoomId")
    void cancelAllOtherPendingChallenges(@Param("username") String username,
                                         @Param("activeRoomId") String activeRoomId,
                                         @Param("cancelledStatus") ChallengeMessage.ChallengeStatus cancelledStatus,
                                         @Param("pendingStatus") ChallengeMessage.ChallengeStatus pendingStatus);
}