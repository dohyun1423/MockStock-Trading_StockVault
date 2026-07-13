// 로그인 사용자의 주문 체결 알림 구독 요청을 처리하는 WebSocket 핸들러
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
public class OrderNotificationWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final StockRealtimeSessionRegistry sessionRegistry;

    // 브라우저가 보낸 주문 알림 구독 메시지를 JWT로 검증하고 사용자 email 기준으로 세션을 등록한다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        OrderNotificationSubscribeRequest request;

        try {
            request = objectMapper.readValue(message.getPayload(), OrderNotificationSubscribeRequest.class);
        } catch (Exception e) {
            log.warn("Invalid order notification websocket message. sessionId={}", session.getId(), e);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        if (!"ORDER_NOTIFICATION_SUBSCRIBE".equalsIgnoreCase(request.type())) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        if (request.token() == null || !jwtUtil.validateToken(request.token())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String email = jwtUtil.getEmailFromToken(request.token());
        sessionRegistry.subscribeUser(email, session);

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "ORDER_NOTIFICATION_SUBSCRIBED"
        ))));

        log.info("Browser order notification subscribed. email={}, sessionId={}", email, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        log.info("Browser order notification websocket closed. sessionId={}, status={}", session.getId(), status);
    }

    private record OrderNotificationSubscribeRequest(
            String type,
            String token
    ) {
    }
}
