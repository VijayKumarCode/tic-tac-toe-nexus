/**
 * Problem No. #125
 * Difficulty: Medium
 * Description: Production-ready WebSocket presence management.
 * Link: N/A
 * Time Complexity: O(1) for map lookups and status updates.
 * Space Complexity: O(n) where n is the number of active WebSocket sessions.
 */
package com.vk.gaming.nexus.config;

import com.vk.gaming.nexus.dto.PlayerStatus;
import com.vk.gaming.nexus.entity.User;
import com.vk.gaming.nexus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserRepository userRepository;
    // FIXED: Inject template for broadcasting
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        // Extract the custom 'user' header we sent from the frontend
        String username = accessor.getFirstNativeHeader("user");
        String sessionId = accessor.getSessionId();

        if (username != null) {
            sessionUserMap.put(sessionId, username);
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setStatus(User.UserStatus.ONLINE);
                user.setIsOnline(true);
                userRepository.save(user);
                log.info("User {} connected and marked ONLINE", username);
            });
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String username = sessionUserMap.remove(sessionId);

        if (username != null) {
            if (!sessionUserMap.containsValue(username)) {
                userRepository.findByUsername(username).ifPresent(user -> {
                    user.setStatus(User.UserStatus.OFFLINE);
                    user.setIsOnline(false);
                    userRepository.save(user);
                    log.info("User {} is now truly OFFLINE", username);

                    // FIXED: Tell the frontend to remove this user from the lobby
                    messagingTemplate.convertAndSend("/topic/lobby.status",
                            new PlayerStatus(username, "OFFLINE"));
                });
            }
        }
    }
}
