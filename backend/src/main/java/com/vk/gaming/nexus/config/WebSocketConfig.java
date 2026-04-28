package com.vk.gaming.nexus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties appProperties;

    public WebSocketConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String endpoint = "/game-websocket";

        // Add endpoint and configure allowed origins/patterns on the endpoint registration
        var endpointReg = registry.addEndpoint(endpoint);

        var origins = appProperties.getAllowedOrigins(); // List<String> from AppProperties
        if (origins != null && !origins.isEmpty()) {
            // If you need exact origins (recommended when allowCredentials=true)
            endpointReg.setAllowedOrigins(origins.toArray(new String[0]));

            // OR, if you need wildcard patterns (use only when necessary):
            // endpointReg.setAllowedOriginPatterns(origins.toArray(new String[0]));
        } else {
            endpointReg.setAllowedOrigins("http://localhost:3000", "http://localhost:8080");
        }

        // Enable SockJS after configuring allowed origins on the endpoint registration
        endpointReg.withSockJS();
    }

}