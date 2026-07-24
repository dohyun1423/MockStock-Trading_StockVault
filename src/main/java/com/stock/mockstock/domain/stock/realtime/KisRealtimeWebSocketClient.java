// KIS WebSocket 연결 하나를 재사용해서 국내주식 실시간 체결가와 호가를 구독하는 클라이언트
package com.stock.mockstock.domain.stock.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OrderMatchingService;
import com.stock.mockstock.domain.stock.kis.KisApprovalKeyService;
import com.stock.mockstock.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisRealtimeWebSocketClient {

    private static final String CUSTOMER_TYPE_PERSONAL = "P";
    private static final String SUBSCRIBE = "1";

    private final KisApprovalKeyService kisApprovalKeyService;
    private final KisProperties kisProperties;
    private final ObjectMapper objectMapper;
    private final MarketSessionService marketSessionService;
    private final KisRealtimeTradeMessageParser tradeMessageParser;
    private final KisRealtimeOrderbookMessageParser orderbookMessageParser;
    private final StockRealtimeBroadcaster stockRealtimeBroadcaster;
    private final OrderMatchingService orderMatchingService;

    private WebSocketSession session;
    private final Set<String> subscribedKeys = ConcurrentHashMap.newKeySet();

    // 지정한 종목코드의 실시간 체결가를 기존 KIS WebSocket 연결에 구독 요청한다.
    public void subscribeTrade(String symbol) {
        MarketSession marketSession = marketSessionService.getCurrentSession();

        if (marketSessionService.isRealtimeSubscriptionPausedSession(marketSession)) {
            log.debug("KIS realtime trade subscription paused. symbol={}, session={}", symbol, marketSession);
            return;
        }

        String trId = KisRealtimeTrId.resolveTradeTrId(marketSession);

        subscribe(symbol, trId, "trade", marketSession);
    }

    // 지정한 종목코드의 실시간 호가를 기존 KIS WebSocket 연결에 구독 요청한다.
    public void subscribeOrderbook(String symbol) {
        MarketSession marketSession = marketSessionService.getCurrentSession();

        if (marketSessionService.isRealtimeSubscriptionPausedSession(marketSession)) {
            log.debug("KIS realtime orderbook subscription paused. symbol={}, session={}", symbol, marketSession);
            return;
        }

        String trId = KisRealtimeTrId.resolveOrderbookTrId(marketSession);

        subscribe(symbol, trId, "orderbook", marketSession);
    }

    // KIS WebSocket 연결을 재사용해서 전달받은 TR ID로 구독 메시지를 전송한다.
    private void subscribe(String symbol, String trId, String logType, MarketSession marketSession) {
        try {
            String normalizedSymbol = normalizeSymbol(symbol);
            String subscribeKey = trId + ":" + normalizedSymbol;
            String approvalKey = kisApprovalKeyService.getApprovalKey();
            WebSocketSession currentSession = getOrCreateSession();

            if (!subscribedKeys.add(subscribeKey)) {
                log.info("KIS realtime already subscribed. key={}", subscribeKey);
                return;
            }

            currentSession.sendMessage(new TextMessage(createSubscribeMessage(
                    approvalKey,
                    trId,
                    normalizedSymbol
            )));

            log.info(
                    "KIS realtime {} subscribed. symbol={}, session={}, trId={}",
                    logType,
                    normalizedSymbol,
                    marketSession,
                    trId
            );
        } catch (Exception e) {
            log.error("KIS realtime {} subscribe failed. symbol={}", logType, symbol, e);
        }
    }

    // 같은 appkey로 여러 WebSocket 연결을 만들지 않도록 하나의 session을 재사용한다.
    private synchronized WebSocketSession getOrCreateSession() throws Exception {
        if (session != null && session.isOpen()) {
            return session;
        }

        StandardWebSocketClient client = new StandardWebSocketClient();
        KisRealtimeWebSocketHandler handler = new KisRealtimeWebSocketHandler(
                tradeMessageParser,
                orderbookMessageParser,
                stockRealtimeBroadcaster,
                orderMatchingService
        );

        subscribedKeys.clear();
        session = client.execute(
                handler,
                new WebSocketHttpHeaders(),
                URI.create(kisProperties.getWebsocketUrl())
        ).get();

        return session;
    }

    // KIS WebSocket 구독 요청 JSON을 만든다.
    private String createSubscribeMessage(String approvalKey, String trId, String symbol) throws Exception {
        Map<String, Object> message = Map.of(
                "header", Map.of(
                        "approval_key", approvalKey,
                        "custtype", CUSTOMER_TYPE_PERSONAL,
                        "tr_type", SUBSCRIBE,
                        "content-type", "utf-8"
                ),
                "body", Map.of(
                        "input", Map.of(
                                "tr_id", trId,
                                "tr_key", symbol
                        )
                )
        );

        return objectMapper.writeValueAsString(message);
    }

    // KIS 요청과 구독 키에 사용할 수 있도록 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }
}
