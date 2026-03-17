package com.vk.gaming.nexus.service;

import com.vk.gaming.nexus.dto.GameMove;
import com.vk.gaming.nexus.dto.GameSystemMessage;
import com.vk.gaming.nexus.dto.TossRequest;
import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.model.GameMoveEntity;
import com.vk.gaming.nexus.repository.GameMoveRepository;
import com.vk.gaming.nexus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameMoveRepository gameMoveRepository;
    private final UserRepository userRepository;

    // 🔥 GAME STATE (PER ROOM)
    private final Map<String, String> currentTurnMap = new ConcurrentHashMap<>();
    private final Map<String, String> playerXMap = new ConcurrentHashMap<>();
    private final Map<String, String> playerOMap = new ConcurrentHashMap<>();

    // ==============================
    // 🎯 GAME MOVE (CORE LOGIC)
    // ==============================
    @Transactional
    public GameMove processGameMove(GameMove incomingMove) {

        String rId = incomingMove.getRoomId();

        // ❌ Block if position already taken
        if (gameMoveRepository.existsByRoomIdAndBoardPosition(rId, incomingMove.getBoardPosition())) {
            log.warn("Position occupied in room {}", rId);
            return null;
        }

        // ❌ Enforce TURN
        String currentTurn = currentTurnMap.get(rId);
        if (currentTurn != null && !currentTurn.equals(incomingMove.getPlayerUsername())) {
            log.warn("Invalid turn attempt by {}", incomingMove.getPlayerUsername());
            return null;
        }

        // ✅ Assign symbol based on toss decision
        String symbol = incomingMove.getPlayerUsername().equals(playerXMap.get(rId)) ? "X" : "O";

        GameMoveEntity entity = new GameMoveEntity();
        entity.setRoomId(rId);
        entity.setBoardPosition(incomingMove.getBoardPosition());
        entity.setPlayerUsername(incomingMove.getPlayerUsername());
        entity.setSymbol(symbol);

        // 1. Save move
        GameMoveEntity saved = gameMoveRepository.save(entity);

        // 2. Check game state
        String state = checkGameState(rId);
        saved.setGameState(state);

        // 3. If game ended
        if (state.startsWith("WINNER_") || state.equals("DRAW")) {

            processWin(incomingMove.getPlayerUsername());

            markPlayersOnlineByRoom(rId);

            // 🔥 CLEAR GAME STATE
            currentTurnMap.remove(rId);
            playerXMap.remove(rId);
            playerOMap.remove(rId);
        } else {
            // 🔁 SWITCH TURN
            String nextPlayer = incomingMove.getPlayerUsername().equals(playerXMap.get(rId))
                    ? playerOMap.get(rId)
                    : playerXMap.get(rId);

            currentTurnMap.put(rId, nextPlayer);
        }

        return mapToDto(saved);
    }

    // ==============================
    // 🧠 GAME STATE CHECK
    // ==============================
    private String checkGameState(String rId) {

        List<GameMoveEntity> moves = gameMoveRepository.findByRoomId(rId);
        String[] board = new String[9];

        for (GameMoveEntity m : moves) {
            board[m.getBoardPosition()] = m.getSymbol();
        }

        int[][] winPatterns = {
                {0,1,2},{3,4,5},{6,7,8},
                {0,3,6},{1,4,7},{2,5,8},
                {0,4,8},{2,4,6}
        };

        for (int[] p : winPatterns) {
            if (board[p[0]] != null &&
                    board[p[0]].equals(board[p[1]]) &&
                    board[p[0]].equals(board[p[2]])) {
                return "WINNER_" + board[p[0]];
            }
        }

        return moves.size() == 9 ? "DRAW" : "ONGOING";
    }

    // ==============================
    // 🏆 WIN HANDLING
    // ==============================
    @Transactional
    public void processWin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setWins(user.getWins() + 1);
            userRepository.save(user);
        });
    }

    // ==============================
    // 🎲 TOSS LOGIC
    // ==============================
    public GameSystemMessage processToss(TossRequest request) {

        String winner = Math.random() < 0.5 ? request.getPlayerOne() : request.getPlayerTwo();

        GameSystemMessage res = new GameSystemMessage();
        res.setType("TOSS_RESULT");
        res.setPayload(winner);
        res.setMessage("Toss winner is " + winner + ". Choose PLAY or PASS.");

        return res;
    }

    // ==============================
    // 🧠 TOSS DECISION (PLAY / PASS)
    // ==============================
    public GameSystemMessage processTossDecision(String roomId, String winner, String loser, String choice) {
        // Check if the game is already started for this room to avoid resetting symbols mid-choice
        if (playerXMap.containsKey(roomId)) {
            log.warn("Toss decision already processed for room: {}", roomId);
            return null;
        }

        String firstPlayer = "PLAY".equalsIgnoreCase(choice) ? winner : loser;
        String secondPlayer = firstPlayer.equals(winner) ? loser : winner;

        playerXMap.put(roomId, firstPlayer);
        playerOMap.put(roomId, secondPlayer);
        currentTurnMap.put(roomId, firstPlayer);

        GameSystemMessage res = new GameSystemMessage();
        res.setType("GAME_START");
        res.setPayload(firstPlayer);
        res.setMessage(firstPlayer + " starts as X. " + secondPlayer + " is O.");

        return res;
    }

    // ==============================
    // 🔄 RESET GAME
    // ==============================
    @Transactional
    public void resetGame(String roomId) {
        gameMoveRepository.deleteByRoomId(roomId);

        currentTurnMap.remove(roomId);
        playerXMap.remove(roomId);
        playerOMap.remove(roomId);
    }

    // ==============================
    // 👥 USER STATUS
    // ==============================
    @Transactional
    public void markPlayersOnlineByRoom(String roomId) {

        String[] parts = roomId.split("_");

        if (parts.length >= 2) {
            resetUser(parts[0]);
            resetUser(parts[1]);
        }
    }

    private void resetUser(String username) {
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setStatus(User.UserStatus.ONLINE);
            u.setLastSeen(System.currentTimeMillis());
            userRepository.save(u);
        });
    }

    // ==============================
    // 🔁 MAPPER
    // ==============================
    private GameMove mapToDto(GameMoveEntity e) {
        GameMove dto = new GameMove();
        dto.setBoardPosition(e.getBoardPosition());
        dto.setPlayerUsername(e.getPlayerUsername());
        dto.setSymbol(e.getSymbol());
        dto.setGameState(e.getGameState());
        dto.setRoomId(e.getRoomId());
        return dto;
    }
}