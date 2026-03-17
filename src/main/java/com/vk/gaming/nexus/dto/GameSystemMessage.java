package com.vk.gaming.nexus.dto;

import lombok.Data;

@Data
public class GameSystemMessage {
    private String type; // e.g., "TOSS_RESULT", "TURN_PASSED"
    private String payload; // The username of the relevant player
    private String message; // A human-readable description for the UI

    // 🔥 ADD THESE
    private String winner;
    private String loser;
}
