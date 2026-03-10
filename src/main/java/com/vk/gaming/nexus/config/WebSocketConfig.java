package com.vk.gaming.nexus.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {

      // Enables a memory-based message broker to carry game moves back to the players
        config.enableSimpleBroker("/topic");

      // Designates the prefix for messages bound for methods annotated with @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

      // This is the URL players will connect to: ws://localhost:8080/game-websocket
        registry.addEndpoint("/game-websocket")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
