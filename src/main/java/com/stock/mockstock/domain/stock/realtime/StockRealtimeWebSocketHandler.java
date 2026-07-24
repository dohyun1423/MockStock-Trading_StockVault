// 브라우저에서 들어오는 실시간 종목 구독 요청을 처리하는 WebSocket 핸들러
package com.stock.mockstock.domain.stock.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.global.security.jwt.JwtTokenValidator;
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
    private final JwtTokenValidator jwtTokenValidator;
    private final StockRealtimeSessionRegistry sessionRegistry;
    private final KisRealtimeWebSocketClient kisRealtimeWebSocketClient;
    private final StockRealtimeBroadcaster stockRealtimeBroadcaster;
    private final MarketSessionService marketSessionService;

    // 브라우저가 보낸 SUBSCRIBE 메시지를 검증하고 종목 실시간 데이터를 구독한다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ClientSubscribeRequest request;

        try {
            request = objectMapper.readValue(message.getPayload(), ClientSubscribeRequest.class);
        } catch (Exception e) {
            log.warn("Invalid stock websocket message. sessionId={}", session.getId(), e);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        if (jwtTokenValidator.getValidUser(request.token()).isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        if (!"SUBSCRIBE".equalsIgnoreCase(request.type())) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        String symbol = normalizeSymbol(request.symbol());

        if (symbol.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        sessionRegistry.subscribe(symbol, session);
        kisRealtimeWebSocketClient.subscribeTrade(symbol);
        kisRealtimeWebSocketClient.subscribeOrderbook(symbol);

        MarketSession marketSession = marketSessionService.getCurrentSession();
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "SUBSCRIBED",
                "symbol", symbol,
                "marketSession", marketSession,
                "marketDisplayName", marketSessionService.getDisplayName(marketSession),
                "realtimePaused",
                marketSessionService.isRealtimeSubscriptionPausedSession(marketSession)
        ))));

        // Sends the last valid values immediately so a new page does not start with zero data.
        stockRealtimeBroadcaster.sendLatestSnapshots(symbol, session);

        log.info("Browser realtime subscribed. symbol={}, sessionId={}", symbol, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(session);
        log.info("Browser realtime websocket closed. sessionId={}, status={}", session.getId(), status);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }

        return symbol
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
