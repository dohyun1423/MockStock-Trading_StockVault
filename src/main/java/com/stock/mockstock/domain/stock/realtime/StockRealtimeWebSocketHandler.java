// 브라우저에서 들어오는 실시간 종목 구독 요청을 처리하는 WebSocket 핸들러
package com.stock.mockstock.domain.stock.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.global.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class StockRealtimeWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final StockRealtimeSessionRegistry sessionRegistry;
    private final KisRealtimeWebSocketClient kisRealtimeWebSocketClient;

    // 브라우저가 보낸 SUBSCRIBE 또는 ORDER_NOTIFICATION_SUBSCRIBE 메시지를 검증하고 실시간 데이터를 구독한다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ClientSubscribeRequest request = objectMapper.readValue(
                message.getPayload(),
                ClientSubscribeRequest.class
        );

        if (request.token() == null || !jwtUtil.validateToken(request.token())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        if ("ORDER_NOTIFICATION_SUBSCRIBE".equalsIgnoreCase(request.type())) {
            String email = jwtUtil.getEmailFromToken(request.token());
            sessionRegistry.subscribeUser(email, session);

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "ORDER_NOTIFICATION_SUBSCRIBED"
            ))));

            log.info("Browser order notification subscribed. email={}, sessionId={}", email, session.getId());
            return;
        }

        if (!"SUBSCRIBE".equalsIgnoreCase(request.type())) {
            return;
        }

        String symbol = normalizeSymbol(request.symbol());

        sessionRegistry.subscribe(symbol, session);
        kisRealtimeWebSocketClient.subscribeTrade(symbol);
        kisRealtimeWebSocketClient.subscribeOrderbook(symbol);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "SUBSCRIBED",
                "symbol", symbol
        ))));

        log.info("Browser realtime subscribed. symbol={}, sessionId={}", symbol, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        log.info("Browser realtime websocket closed. sessionId={}, status={}", session.getId(), status);
    }

    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    private record ClientSubscribeRequest(
            String type,
            String symbol,
            String token
    ) {
    }
}
