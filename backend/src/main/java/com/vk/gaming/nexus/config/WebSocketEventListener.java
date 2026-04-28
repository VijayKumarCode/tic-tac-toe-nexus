package com.vk.gaming.nexus.config;

import com.vk.gaming.nexus.service.ChallengeService;
import com.vk.gaming.nexus.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserService userService;
    private final ChallengeService challengeService;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();

        String username = null;
        if (user != null) {
            username = user.getName();
        } else {
            // Fallback: read a validated header (only if you validate it elsewhere)
            username = extractHeader(accessor, "username");
        }

        if (username == null) {
            log.debug("WebSocket CONNECT without username/principal");
            return;
        }

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            attrs.put("username", username);
            log.info("WebSocket session stored username={}", username);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) {
            log.debug("WebSocket disconnect — no session attributes");
            return;
        }
        Object val = attrs.get("username");
        if (val == null) {
            log.debug("WebSocket disconnect — username not present in session attributes");
            return;
        }
        String username = val.toString();
        log.info("User disconnected: {}", username);
        try {
            userService.logoutUser(username);
            challengeService.cancelStaleChallenges(username);
        } catch (Exception e) {
            log.warn("Error during disconnect cleanup for {}: {}", username, e.getMessage());
        }
    }

    private String extractHeader(StompHeaderAccessor accessor, String headerName) {
        var nativeHeaders = accessor.toNativeHeaderMap();
        if (nativeHeaders == null) return null;
        var values = nativeHeaders.get(headerName);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }
}
