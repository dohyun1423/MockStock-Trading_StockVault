// KIS WebSocket에서 수신한 실시간 메시지를 구분하고 파싱한 뒤 브라우저 구독자에게 전달하는 핸들러
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.service.OrderMatchingService;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;

@Slf4j
@RequiredArgsConstructor
public class KisRealtimeWebSocketHandler extends TextWebSocketHandler {

    private final KisRealtimeTradeMessageParser tradeMessageParser;
    private final KisRealtimeOrderbookMessageParser orderbookMessageParser;
    private final StockRealtimeBroadcaster stockRealtimeBroadcaster;
    private final OrderMatchingService orderMatchingService;
    private final MarketSessionService marketSessionService;
    private final BooleanSupplier messageHandlingEnabled;

    // KIS에서 오는 JSON 응답, 실시간 체결 데이터, 실시간 호가 데이터, PINGPONG 메시지를 구분해서 처리한다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!messageHandlingEnabled.getAsBoolean()) {
            return;
        }

        String payload = message.getPayload();
        String trId = KisRealtimeTrId.extractTrId(payload);
        MarketSession marketSession = resolveMarketSession(trId);

        if (
                KisRealtimeTrId.isNxtOrUnifiedTrId(trId)
                        && !marketSessionService.isRealtimeDataAvailable(marketSession)
        ) {
            log.debug(
                    "KIS unified realtime message ignored outside active session. session={}, trId={}",
                    marketSession,
                    trId
            );
            return;
        }

        if (KisRealtimeTrId.isTradeTrId(trId)) {
            KisRealtimeTradeMessage tradeMessage = tradeMessageParser.parse(payload, marketSession);
            boolean broadcasted = stockRealtimeBroadcaster.broadcastTrade(tradeMessage);

            if (!broadcasted) {
                log.debug(
                        "Invalid KIS realtime trade dropped. symbol={}, session={}, trId={}",
                        tradeMessage.getSymbol(),
                        marketSession,
                        trId
                );
                return;
            }

            log.info(
                    "KIS realtime trade parsed. symbol={}, session={}, trId={}, price={}, changeRate={}, volume={}",
                    tradeMessage.getSymbol(),
                    marketSession,
                    trId,
                    tradeMessage.getCurrentPrice(),
                    tradeMessage.getChangeRate(),
                    tradeMessage.getAccumulatedVolume()
            );
            return;
        }

        if (KisRealtimeTrId.isOrderbookTrId(trId)) {
            KisRealtimeOrderbookMessage orderbookMessage = orderbookMessageParser.parse(payload, marketSession);
            boolean broadcasted = stockRealtimeBroadcaster.broadcastOrderbook(orderbookMessage);

            if (!broadcasted) {
                log.debug(
                        "Invalid KIS realtime orderbook dropped. symbol={}, session={}, trId={}",
                        orderbookMessage.getSymbol(),
                        marketSession,
                        trId
                );
                return;
            }

            if (marketSessionService.isImmediateExecution(marketSession)) {
                orderMatchingService.matchByRealtimeOrderbook(orderbookMessage);
            }

            log.info(
                    "KIS realtime orderbook parsed. symbol={}, session={}, trId={}, levels={}, totalAsk={}, totalBid={}",
                    orderbookMessage.getSymbol(),
                    marketSession,
                    trId,
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

        log.debug("KIS websocket message={}", payload);
    }

    // 통합/NXT 데이터는 수신 시각의 실제 시장 세션으로, 기존 KRX 데이터는 TR 기준으로 판별한다.
    private MarketSession resolveMarketSession(String trId) {
        if (KisRealtimeTrId.isNxtOrUnifiedTrId(trId)) {
            return marketSessionService.getCurrentSession();
        }

        return KisRealtimeTrId.resolveMarketSession(trId);
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
        if (messageHandlingEnabled.getAsBoolean()) {
            log.warn("KIS websocket closed. sessionId={}, status={}", session.getId(), status);
            return;
        }

        log.info("KIS websocket closed during application shutdown. sessionId={}", session.getId());
    }
}
