package com.vk.gaming.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameMove {
    private String playerUsername;
    private int  boardPosition; // 0 through 8 representing the nexus grid
    private String symbol; // "X" or "O"
    private String gameState;
    private String roomId; // Changed from Object to String for consistency
}
