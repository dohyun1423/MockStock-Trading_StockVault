// KIS WebSocket에서 수신한 실시간 메시지를 구분하고 파싱한 뒤 브라우저 구독자에게 전달하는 핸들러
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.service.OrderMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;

@Slf4j
@RequiredArgsConstructor
public class KisRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final String REALTIME_TRADE_TR_ID = "H0STCNT0";
    private static final String REALTIME_ORDERBOOK_TR_ID = "H0STASP0";

    private final KisRealtimeTradeMessageParser tradeMessageParser;
    private final KisRealtimeOrderbookMessageParser orderbookMessageParser;
    private final StockRealtimeBroadcaster stockRealtimeBroadcaster;
    private final OrderMatchingService orderMatchingService;

    // KIS에서 오는 JSON 응답, 실시간 체결 데이터, 실시간 호가 데이터, PINGPONG 메시지를 구분해서 처리한다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (payload.startsWith("0|" + REALTIME_TRADE_TR_ID + "|")) {
            KisRealtimeTradeMessage tradeMessage = tradeMessageParser.parse(payload);
            stockRealtimeBroadcaster.broadcastTrade(tradeMessage);
            orderMatchingService.matchByRealtimeTrade(tradeMessage);

            log.info(
                    "KIS realtime trade parsed. symbol={}, price={}, changeRate={}, volume={}",
                    tradeMessage.getSymbol(),
                    tradeMessage.getCurrentPrice(),
                    tradeMessage.getChangeRate(),
                    tradeMessage.getAccumulatedVolume()
            );
            return;
        }

        if (payload.startsWith("0|" + REALTIME_ORDERBOOK_TR_ID + "|")) {
            KisRealtimeOrderbookMessage orderbookMessage = orderbookMessageParser.parse(payload);
            stockRealtimeBroadcaster.broadcastOrderbook(orderbookMessage);

            log.info(
                    "KIS realtime orderbook parsed. symbol={}, levels={}, totalAsk={}, totalBid={}",
                    orderbookMessage.getSymbol(),
                    orderbookMessage.getLevels().size(),
                    orderbookMessage.getTotalAskQuantity(),
                    orderbookMessage.getTotalBidQuantity()
            );
            return;
        }

        if (payload.contains("\"tr_id\":\"PINGPONG\"")) {
            session.sendMessage(new PongMessage(ByteBuffer.wrap(payload.getBytes())));
            log.debug("KIS PINGPONG handled.");
            return;
        }

        log.info("KIS websocket message={}", payload);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("KIS websocket connected. sessionId={}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("KIS websocket transport error. sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        log.warn("KIS websocket closed. sessionId={}, status={}", session.getId(), status);
    }
}
