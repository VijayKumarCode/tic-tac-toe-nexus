package com.vk.gaming.nexus.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_moves")
@Data
public class GameMoveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String playerUsername;
    private int boardPosition;
    private String symbol;

    private LocalDateTime createDate = LocalDateTime.now();
}
