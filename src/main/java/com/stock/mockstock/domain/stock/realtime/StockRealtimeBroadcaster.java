// KIS에서 파싱한 실시간 데이터를 브라우저 WebSocket 구독자에게 전달하는 브로드캐스터
package com.stock.mockstock.domain.stock.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.domain.order.dto.OrderExecutionNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRealtimeBroadcaster {

    private final ObjectMapper objectMapper;
    private final StockRealtimeSessionRegistry sessionRegistry;
    private final StockRealtimeSnapshotCache snapshotCache;

    // 유효한 실시간 체결가만 캐시에 저장하고 브라우저로 전달한다.
    public boolean broadcastTrade(KisRealtimeTradeMessage tradeMessage) {
        return snapshotCache.storeTrade(tradeMessage)
                .map(storedMessage -> {
                    broadcast(storedMessage.getSymbol(), "TRADE", storedMessage);
                    return true;
                })
                .orElseGet(() -> {
                    log.debug("Invalid realtime trade ignored.");
                    return false;
                });
    }

    // 모든 값이 0인 호가를 제외한 정상 호가만 캐시에 저장하고 브라우저로 전달한다.
    public boolean broadcastOrderbook(KisRealtimeOrderbookMessage orderbookMessage) {
        return snapshotCache.storeOrderbook(orderbookMessage)
                .map(storedMessage -> {
                    broadcast(storedMessage.getSymbol(), "ORDERBOOK", storedMessage);
                    return true;
                })
                .orElseGet(() -> {
                    log.debug("Invalid realtime orderbook ignored.");
                    return false;
                });
    }

    // 새로 구독한 브라우저에 마지막 정상 체결가와 호가를 즉시 전달한다.
    public void sendLatestSnapshots(String symbol, WebSocketSession session) {
        snapshotCache.getTrade(symbol)
                .ifPresent(tradeMessage -> sendToSession(session, symbol, "TRADE", tradeMessage));
        snapshotCache.getOrderbook(symbol)
                .ifPresent(orderbookMessage ->
                        sendToSession(session, symbol, "ORDERBOOK", orderbookMessage)
                );
    }

    // 주문이 체결되었을 때 해당 사용자 브라우저로만 알림을 전달한다.
    public void broadcastOrderExecution(String email, OrderExecutionNotification notification) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "ORDER_EXECUTED",
                    "data", notification
            ));

            sessionRegistry.broadcastToUser(email, message);
        } catch (Exception e) {
            throw new IllegalStateException("Order notification serialization failed.", e);
        }
    }

    private void broadcast(String symbol, String type, Object data) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "symbol", symbol,
                    "data", data
            ));

            sessionRegistry.broadcast(symbol, message);
        } catch (Exception e) {
            throw new IllegalStateException("Realtime message serialization failed.", e);
        }
    }

    // 지정한 브라우저 WebSocket 세션 하나에 실시간 메시지를 전달한다.
    private void sendToSession(
            WebSocketSession session,
            String symbol,
            String type,
            Object data
    ) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "symbol", symbol,
                    "data", data
            ));

            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.warn(
                    "Realtime snapshot send failed. symbol={}, type={}, sessionId={}",
                    symbol,
                    type,
                    session.getId(),
                    e
            );
        }
    }
}
