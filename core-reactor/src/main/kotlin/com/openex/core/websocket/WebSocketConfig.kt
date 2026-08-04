package com.openex.core.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * STOMP over WebSocket. Clients connect to /ws, subscribe to /topic/orderbook/{pair}
 * to receive live order book snapshots whenever the matching engine's state changes.
 *
 * Broker prefix "/topic" is for server -> client broadcasts (pub-sub).
 * Application prefix "/app" would be for client -> server messages, not used yet
 * since order placement still goes through the REST API.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // withSockJS() gives a fallback for browsers/networks that block raw WebSockets.
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()

        // Also expose a raw (non-SockJS) endpoint for simple WebSocket clients/tests.
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
    }
}
