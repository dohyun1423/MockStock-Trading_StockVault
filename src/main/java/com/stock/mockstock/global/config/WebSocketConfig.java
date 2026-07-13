// 브라우저가 접속할 수 있는 MockStock 실시간 WebSocket endpoint를 등록한다.
package com.stock.mockstock.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeWebSocketClient;
import com.stock.mockstock.domain.stock.realtime.OrderNotificationWebSocketHandler;
import com.stock.mockstock.domain.stock.realtime.StockRealtimeSessionRegistry;
import com.stock.mockstock.domain.stock.realtime.StockRealtimeWebSocketHandler;
import com.stock.mockstock.global.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final StockRealtimeSessionRegistry sessionRegistry;
    private final KisRealtimeWebSocketClient kisRealtimeWebSocketClient;

    // 브라우저 실시간 구독 endpoint를 등록한다.
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                        new StockRealtimeWebSocketHandler(
                                objectMapper,
                                jwtUtil,
                                sessionRegistry,
                                kisRealtimeWebSocketClient
                        ),
                        "/ws/stocks"
                )
                .setAllowedOriginPatterns("*");

        registry.addHandler(
                        new OrderNotificationWebSocketHandler(
                                objectMapper,
                                jwtUtil,
                                sessionRegistry
                        ),
                        "/ws/orders"
                )
                .setAllowedOriginPatterns("*");
    }
}
