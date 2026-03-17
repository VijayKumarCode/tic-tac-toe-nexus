/**
 * Problem No. #157
 * Difficulty: Medium
 * Description: Refactored respondToChallenge to dynamically handle ACCEPTED and DECLINED statuses safely
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(1)
 * Space Complexity: O(1)
 */
package com.vk.gaming.nexus.service;

import com.vk.gaming.nexus.dto.ChallengeMessage;
import com.vk.gaming.nexus.dto.PlayerStatus;
import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.model.ChallengeEntity;
import com.vk.gaming.nexus.repository.ChallengeRepository;
import com.vk.gaming.nexus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final ChallengeRepository challengeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @Transactional
    public void createChallenge(ChallengeMessage message) {
        ChallengeEntity entity = ChallengeEntity.builder()
                .sender(message.getSender())
                .receiver(message.getReceiver())
                .roomId(message.getRoomId())
                .status(ChallengeMessage.ChallengeStatus.PENDING)
                .build();
        challengeRepository.save(entity);
    }

    @Transactional
    public void respondToChallenge(ChallengeMessage message) {
        ChallengeEntity challenge = challengeRepository.findByRoomId(message.getRoomId())
                .orElseThrow(() -> new RuntimeException("Challenge not found"));

        // Set status dynamically (Accept or Decline)
        challenge.setStatus(message.getStatus());
        challengeRepository.save(challenge);

        // Logic for transitioning to Game State
        if (message.getStatus() == ChallengeMessage.ChallengeStatus.ACCEPTED) {

            challengeRepository.cancelAllOtherPendingChallenges(
                    message.getSender(), message.getRoomId(),
                    ChallengeMessage.ChallengeStatus.CANCELLED, ChallengeMessage.ChallengeStatus.PENDING
            );
            challengeRepository.cancelAllOtherPendingChallenges(
                    message.getReceiver(), message.getRoomId(),
                    ChallengeMessage.ChallengeStatus.CANCELLED, ChallengeMessage.ChallengeStatus.PENDING
            );

            // Update Database statuses to IN_GAME
            updatePlayerStatus(message.getSender(), User.UserStatus.IN_GAME);
            updatePlayerStatus(message.getReceiver(), User.UserStatus.IN_GAME);

            // Broadcast UI Sync to Lobby
            messagingTemplate.convertAndSend("/topic/lobby.status", new PlayerStatus(message.getSender(), "IN_GAME"));
            messagingTemplate.convertAndSend("/topic/lobby.status", new PlayerStatus(message.getReceiver(), "IN_GAME"));
        }
    }

    private void updatePlayerStatus(String username, User.UserStatus status) {
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setStatus(status);
            u.setLastSeen(System.currentTimeMillis());
            userRepository.save(u);
        });
    }
}