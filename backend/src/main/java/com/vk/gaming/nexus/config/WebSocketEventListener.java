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

        // The connected event wraps the original CONNECT frame — read from native headers
        String username = extractFromNativeHeader(accessor, "username");
        if (username == null) return;

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            attrs.put("username", username);
            log.info("WebSocket session stored username={}", username);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String username = extractFromSessionAttributes(event);
        if (username == null) {
            log.debug("WebSocket disconnect — no username in session (user may not have been authenticated)");
            return;
        }

        log.info("User disconnected: {}", username);
        userService.logoutUser(username);
        challengeService.cancelStaleChallenges(username);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private String extractFromSessionAttributes(SessionDisconnectEvent event) {
        if (event.getMessage() == null) return null;
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return null;
        Object val = attrs.get("username");
        return val != null ? val.toString() : null;
    }

    private String extractFromNativeHeader(StompHeaderAccessor accessor, String headerName) {
        try {
            var nativeHeaders = accessor.toNativeHeaderMap();
            if (nativeHeaders == null) return null;
            var values = nativeHeaders.get(headerName);
            return (values != null && !values.isEmpty()) ? values.get(0) : null;
        } catch (Exception e) {
            return null;
        }
    }
}