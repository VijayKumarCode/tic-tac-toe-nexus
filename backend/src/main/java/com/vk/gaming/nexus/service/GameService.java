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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameMoveRepository gameMoveRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    private final Map<String, String[]> roomPlayersMap  = new ConcurrentHashMap<>();
    private final Map<String, String>   currentTurnMap  = new ConcurrentHashMap<>();
    private final Map<String, String>   playerXMap      = new ConcurrentHashMap<>();
    private final Map<String, String>   playerOMap      = new ConcurrentHashMap<>();
    private final Map<String, String>   tossWinnerMap   = new ConcurrentHashMap<>();

    // ========================= GAME MOVE =========================
    @Transactional
    public GameMove processGameMove(GameMove incomingMove) {
        String rId = incomingMove.getRoomId();

        /* ── GUARD: roomId must not be null ── */
        if (rId == null || rId.isBlank()) {
            log.error("processGameMove called with null/blank roomId — move rejected");
            return null;
        }

        log.info("processGameMove — room={} player={} pos={}",
                rId, incomingMove.getPlayerUsername(), incomingMove.getBoardPosition());

        /* ── RECOVER in-memory state if server restarted ── */
        if (!currentTurnMap.containsKey(rId)) {
            log.warn("currentTurnMap miss for room={} — attempting DB recovery", rId);
            recoverGameStateFromDB(rId);
        }

        /* ── Log map state so we can debug remotely ── */
        log.info("State maps — currentTurn={} playerX={} playerO={}",
                currentTurnMap.get(rId),
                playerXMap.get(rId),
                playerOMap.get(rId));

        /* ── TURN VALIDATION ── */
        String expectedPlayer = currentTurnMap.get(rId);
        if (expectedPlayer == null) {
            log.error("currentTurnMap has no entry for room={} after recovery — move rejected", rId);
            return null;
        }

        if (!incomingMove.getPlayerUsername().equals(expectedPlayer)) {
            log.warn("Invalid turn in room={}: expected={} got={}",
                    rId, expectedPlayer, incomingMove.getPlayerUsername());
            return null;
        }

        /* ── DUPLICATE MOVE CHECK ── */
        if (gameMoveRepository.existsByRoomIdAndBoardPosition(
                rId, incomingMove.getBoardPosition())) {
            log.warn("Duplicate move at pos={} in room={}", incomingMove.getBoardPosition(), rId);
            return null;
        }

        /* ── SYMBOL — always derived server-side, never trusted from client ── */
        String xPlayer = playerXMap.get(rId);
        String symbol  = incomingMove.getPlayerUsername().equals(xPlayer) ? "X" : "O";
        incomingMove.setSymbol(symbol);

        log.info("Move accepted — room={} player={} pos={} symbol={}",
                rId, incomingMove.getPlayerUsername(), incomingMove.getBoardPosition(), symbol);

        /* ── PERSIST ── */
        GameMoveEntity entity = new GameMoveEntity();
        entity.setRoomId(rId);
        entity.setPlayerUsername(incomingMove.getPlayerUsername());
        entity.setBoardPosition(incomingMove.getBoardPosition());
        entity.setSymbol(symbol);

        try {
            GameMoveEntity saved = gameMoveRepository.save(entity);
            log.info("✅ Move saved to DB — id={} room={} player={} pos={} symbol={}",
                    saved.getId(), rId, saved.getPlayerUsername(),
                    saved.getBoardPosition(), saved.getSymbol());
        } catch (Exception e) {
            log.error("❌ Database save failed — room={} error={}", rId, e.getMessage(), e);
            return null;
        }

        /* ── CHECK WIN / DRAW ── */
        char[] board = buildBoard(rId);

        String winnerSymbol = checkWinner(board);
        if (winnerSymbol != null) {
            String winner = "X".equals(winnerSymbol) ? playerXMap.get(rId) : playerOMap.get(rId);
            String loser  = "X".equals(winnerSymbol) ? playerOMap.get(rId) : playerXMap.get(rId);
            userService.incrementWins(winner);
            userService.incrementLosses(loser);
            incomingMove.setGameState("WINNER_" + winnerSymbol);
            log.info("Game finished — room={} winner={}", rId, winner);
            currentTurnMap.remove(rId);
            return incomingMove;
        }

        boolean isDraw = true;
        for (char c : board) { if (c == '-') { isDraw = false; break; } }
        if (isDraw) {
            incomingMove.setGameState("DRAW");
            log.info("Game drawn — room={}", rId);
            currentTurnMap.remove(rId);
            return incomingMove;
        }

        /* ── ADVANCE TURN ── */
        String nextPlayer = "X".equals(symbol) ? playerOMap.get(rId) : playerXMap.get(rId);
        currentTurnMap.put(rId, nextPlayer);
        log.info("Next turn — room={} nextPlayer={}", rId, nextPlayer);

        incomingMove.setGameState("ONGOING");
        return incomingMove;
    }

    // ========================= BOARD =========================
    private char[] buildBoard(String roomId) {
        List<GameMoveEntity> history =
                gameMoveRepository.findByRoomIdOrderByCreateDateAsc(roomId);
        char[] board = new char[9];
        Arrays.fill(board, '-');
        for (GameMoveEntity move : history) {
            board[move.getBoardPosition()] = move.getSymbol().charAt(0);
        }
        return board;
    }

    private String checkWinner(char[] b) {
        int[][] patterns = {
                {0,1,2},{3,4,5},{6,7,8},
                {0,3,6},{1,4,7},{2,5,8},
                {0,4,8},{2,4,6}
        };
        for (int[] p : patterns) {
            if (b[p[0]] != '-' && b[p[0]] == b[p[1]] && b[p[1]] == b[p[2]])
                return String.valueOf(b[p[0]]);
        }
        return null;
    }

    // ========================= RECOVERY =========================
    /* ══════════════════════════════════════════════════════════════
       This runs when the server restarts and in-memory maps are lost.
       Rebuilds state from DB history so a game in progress can continue.
    ══════════════════════════════════════════════════════════════ */
    private void recoverGameStateFromDB(String roomId) {
        List<GameMoveEntity> history =
                gameMoveRepository.findByRoomIdOrderByCreateDateAsc(roomId);

        if (history.isEmpty()) {
            log.warn("Recovery failed — no DB history for room={}", roomId);
            return;
        }

        /* Rebuild playerX and playerO from move history */
        for (GameMoveEntity m : history) {
            if ("X".equals(m.getSymbol())) playerXMap.put(roomId, m.getPlayerUsername());
            else                           playerOMap.put(roomId, m.getPlayerUsername());
        }

        /* Also rebuild roomPlayersMap */
        String px = playerXMap.get(roomId);
        String po = playerOMap.get(roomId);
        if (px != null && po != null) {
            roomPlayersMap.put(roomId, new String[]{px, po});
        }

        /* Next turn = opposite of last move */
        GameMoveEntity last = history.get(history.size() - 1);
        String next = "X".equals(last.getSymbol()) ? playerOMap.get(roomId) : playerXMap.get(roomId);
        currentTurnMap.put(roomId, next);

        log.info("Recovery complete — room={} playerX={} playerO={} nextTurn={}",
                roomId, playerXMap.get(roomId), playerOMap.get(roomId), next);
    }

    // ========================= TOSS =========================
    public GameSystemMessage processToss(TossRequest request) {
        String roomId = request.getRoomId();

        if (roomId == null || request.getPlayerOne() == null || request.getPlayerTwo() == null) {
            throw new RuntimeException("Invalid toss request — null fields. roomId="
                    + roomId + " p1=" + request.getPlayerOne() + " p2=" + request.getPlayerTwo());
        }

        boolean coin   = new Random().nextBoolean();
        String winner  = coin ? request.getPlayerOne() : request.getPlayerTwo();
        String loser   = coin ? request.getPlayerTwo() : request.getPlayerOne();

        tossWinnerMap.put(roomId, winner);
        roomPlayersMap.put(roomId, new String[]{request.getPlayerOne(), request.getPlayerTwo()});

        log.info("Toss — room={} winner={}", roomId, winner);

        GameSystemMessage res = new GameSystemMessage();
        res.setType("TOSS");
        res.setWinner(winner);
        res.setLoser(loser);
        res.setPayload(winner);
        res.setMessage(winner + " won the toss! Choose X or O.");
        return res;
    }

    @Transactional
    public GameSystemMessage processTossDecision(String roomId,
                                                 String ignoredWinner,
                                                 String ignoredLoser,
                                                 String choice) {
        String winner = tossWinnerMap.get(roomId);
        if (winner == null) {
            throw new RuntimeException("Toss not initialized for room=" + roomId);
        }

        String[] players = roomPlayersMap.get(roomId);
        if (players == null) {
            throw new RuntimeException("roomPlayersMap missing for room=" + roomId);
        }

        String loser = winner.equals(players[0]) ? players[1] : players[0];

        String firstPlayer;
        String secondPlayer;

        /* "X" means the toss winner plays first as X */
        if ("X".equalsIgnoreCase(choice)) {
            firstPlayer  = winner;
            secondPlayer = loser;
        } else {
            /* "O" means the toss winner chose O — loser plays X first */
            firstPlayer  = loser;
            secondPlayer = winner;
        }

        playerXMap.put(roomId, firstPlayer);
        playerOMap.put(roomId, secondPlayer);
        currentTurnMap.put(roomId, firstPlayer);

        log.info("Toss decision — room={} X(first)={} O={} choice={}",
                roomId, firstPlayer, secondPlayer, choice);

        GameSystemMessage res = new GameSystemMessage();
        res.setType("TOSS_RESULT");
        res.setWinner(winner);
        res.setLoser(loser);
        res.setPayload(firstPlayer);    // firstPlayer = the one who moves first (X)
        res.setMessage(firstPlayer + " starts as X. " + secondPlayer + " is O.");
        return res;
    }

    // ========================= CLEANUP =========================
    @Transactional
    public void resetGame(String roomId) {
        gameMoveRepository.deleteByRoomId(roomId);
        currentTurnMap.remove(roomId);
        playerXMap.remove(roomId);
        playerOMap.remove(roomId);
        tossWinnerMap.remove(roomId);
        roomPlayersMap.remove(roomId);
        log.info("Game reset — room={}", roomId);
    }

    @Transactional
    public void markPlayersOnlineByRoom(String roomId) {
        resetGame(roomId);
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
}