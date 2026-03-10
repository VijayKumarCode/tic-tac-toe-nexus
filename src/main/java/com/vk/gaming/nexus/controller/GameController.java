package com.vk.gaming.nexus.controller;

import com.vk.gaming.nexus.dto.GameMove;
import com.vk.gaming.nexus.model.GameMoveEntity;
import com.vk.gaming.nexus.repository.GameMoveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class GameController {
    @Autowired
    private GameMoveRepository repository;

    /**
     * When a player makes a move, the frontend sends it to "/app/game.makeMove".
     * The broker then instantly broadcasts it to all subscribers on "/topic/game-progress".
     */
    @MessageMapping("/game.makeMove")
    @SendTo("/topic/game-progress")
    public GameMove handleGameMove(@Payload GameMove move) {

        GameMoveEntity entity = new GameMoveEntity();
        entity.setPlayerUsername(move.getPlayerUsername());
        entity.setBoardPosition(move.getBoardPosition());
        entity.setSymbol(move.getSymbol());
        repository.save(entity);
        // In a fully robust system, we would validate the move against the database here.
        // For now, we act as a pure, low-latency relay switch to get the game working.
        return move;
    }
}