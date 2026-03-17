/**
 * Problem No. #156
 * Difficulty: Medium
 * Description: Added missing WebSocket routing to forward challenge replies back to the original challenger
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(1)
 * Space Complexity: O(1)
 */
package com.vk.gaming.nexus.controller;

import com.vk.gaming.nexus.dto.ChallengeMessage;
import com.vk.gaming.nexus.dto.GameMove;
import com.vk.gaming.nexus.dto.GameSystemMessage;
import com.vk.gaming.nexus.dto.TossRequest;
import com.vk.gaming.nexus.service.ChallengeService;
import com.vk.gaming.nexus.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChallengeService challengeService;
    private final GameService gameService;

    @MessageMapping("/challenge")
    public void sendChallenge(@Payload ChallengeMessage message) {
        log.info("Processing challenge: {} -> {}", message.getSender(), message.getReceiver());
        challengeService.createChallenge(message);
        messagingTemplate.convertAndSend("/topic/challenges/" + message.getReceiver(), message);
    }

    @MessageMapping("/challenge/reply")
    public void handleChallengeReply(@Payload ChallengeMessage message) {
        log.info("Challenge reply: {} -> {} | Status: {}", message.getReceiver(), message.getSender(), message.getStatus());

        // Process DB updates and Lobby broadcasts
        challengeService.respondToChallenge(message);

        // FIXED: Send the response back to the CHALLENGER's personal topic
        // This triggers the setupGame() function on the challenger's side
        messagingTemplate.convertAndSend("/topic/challenges/" + message.getSender(), message);
    }

    @MessageMapping("/toss/{roomId}")
    public void handleToss(@DestinationVariable String roomId, TossRequest request) {
        GameSystemMessage result = gameService.processToss(request);
        messagingTemplate.convertAndSend("/topic/game/" + roomId, result);
    }

    @MessageMapping("/move/{roomId}")
    public void handleGameMove(@DestinationVariable String roomId, GameMove incomingMove) {
        GameMove processedMove = gameService.processGameMove(incomingMove);
        if (processedMove != null) {
            messagingTemplate.convertAndSend("/topic/game/" + roomId, processedMove);
        }
    }

    @MessageMapping("/reset/{roomId}")
    public void handleReset(@DestinationVariable String roomId) {
        log.info("Resetting board for room: {}", roomId);
        gameService.resetGame(roomId);
        GameSystemMessage message = new GameSystemMessage();
        message.setType("GAME_RESET");
        message.setMessage("The board has been cleared. New game started!");
        messagingTemplate.convertAndSend("/topic/game/" + roomId, message);
    }

    @MessageMapping("/game.abort")
    public void handleAbort(@Payload ChallengeMessage message) {
        log.info("Game aborted in room: {} by {}", message.getRoomId(), message.getSender());
        gameService.markPlayersOnlineByRoom(message.getRoomId());
        message.setType(ChallengeMessage.MessageType.GAME_ABORTED);
        message.setStatus(ChallengeMessage.ChallengeStatus.CANCELLED);
        messagingTemplate.convertAndSend("/topic/game/" + message.getRoomId(), message);
    }

    @MessageMapping("/toss/decision/{roomId}")
    public void handleTossDecision(@DestinationVariable String roomId,
                                   @Payload GameSystemMessage msg) {

        GameSystemMessage response = gameService.processTossDecision(
                roomId,
                msg.getWinner(),
                msg.getLoser(),
                msg.getMessage()
        );

        messagingTemplate.convertAndSend("/topic/game/" + roomId, response);
    }
}