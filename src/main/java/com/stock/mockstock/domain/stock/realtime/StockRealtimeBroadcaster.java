// KIS에서 파싱한 실시간 데이터를 브라우저 WebSocket 구독자에게 전달하는 브로드캐스터
package com.stock.mockstock.domain.stock.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.domain.order.dto.OrderExecutionNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StockRealtimeBroadcaster {

    private final ObjectMapper objectMapper;
    private final StockRealtimeSessionRegistry sessionRegistry;

    // 실시간 체결가 데이터를 브라우저로 전달한다.
    public void broadcastTrade(KisRealtimeTradeMessage tradeMessage) {
        broadcast(tradeMessage.getSymbol(), "TRADE", tradeMessage);
    }

    // 실시간 호가 데이터를 브라우저로 전달한다.
    public void broadcastOrderbook(KisRealtimeOrderbookMessage orderbookMessage) {
        broadcast(orderbookMessage.getSymbol(), "ORDERBOOK", orderbookMessage);
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
}
