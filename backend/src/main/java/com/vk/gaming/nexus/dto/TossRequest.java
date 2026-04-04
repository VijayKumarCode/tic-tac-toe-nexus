package com.vk.gaming.nexus.dto;

import lombok.Data;

@Data
public class TossRequest {
    private String playerOne;
    private String playerTwo;
    private String roomId;
}