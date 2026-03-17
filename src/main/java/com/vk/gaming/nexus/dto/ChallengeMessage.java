/*
Problem No. #N/A
Difficulty: N/A
Description: ChallengeMessage DTO Fix
Link: N/A
Time Complexity: O(1)
Space Complexity: O(1)
*/
package com.vk.gaming.nexus.dto;

import lombok.Data;

@Data
public class ChallengeMessage {
    private String sender;
    private String receiver;
    private String roomId;
    private ChallengeStatus status;
    private MessageType type;

    public enum ChallengeStatus {
        PENDING, ACCEPTED, REJECTED, CANCELLED
    }

    public enum MessageType {
        GAME_ABORTED, CHALLENGE_REQUEST, CHALLENGE_RESPONSE
    }
}
