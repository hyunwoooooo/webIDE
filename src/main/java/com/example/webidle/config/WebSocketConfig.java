package com.example.webidle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    protected String generateSessionId(Map<String, Object> attributes) {
                        String sessionId = (String) attributes.get("session-id");
                        if (sessionId != null && !sessionId.isEmpty()) {
                            return sessionId;
                        }
                        return "session-" + System.currentTimeMillis();
                    }
                })
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOriginPatterns("http://localhost:*")
                .withSockJS();
    }
} 