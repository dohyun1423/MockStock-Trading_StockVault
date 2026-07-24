// 통합 실시간 호가가 현재 시장 세션의 자동체결 정책에 맞게 처리되는지 검증한다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OrderMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KisRealtimeWebSocketHandlerTest {

    @Mock
    private StockRealtimeBroadcaster stockRealtimeBroadcaster;

    @Mock
    private OrderMatchingService orderMatchingService;

    @Mock
    private MarketSessionService marketSessionService;

    @Mock
    private WebSocketSession webSocketSession;

    private KisRealtimeWebSocketHandler handler;

    // 실제 파서와 목 객체를 조합해 통합 WebSocket 핸들러를 준비한다.
    @BeforeEach
    void setUp() {
        handler = new KisRealtimeWebSocketHandler(
                new KisRealtimeTradeMessageParser(),
                new KisRealtimeOrderbookMessageParser(),
                stockRealtimeBroadcaster,
                orderMatchingService,
                marketSessionService,
                () -> true
        );
    }

    // NXT 애프터마켓 통합 호가는 화면 전송 후 자동체결에 사용되는지 검증한다.
    @Test
    void matchOrdersWithNxtAfterMarketOrderbook() throws Exception {
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.NXT_AFTER_MARKET);
        when(marketSessionService.isRealtimeDataAvailable(MarketSession.NXT_AFTER_MARKET))
                .thenReturn(true);
        when(marketSessionService.isImmediateExecution(MarketSession.NXT_AFTER_MARKET))
                .thenReturn(true);
        when(stockRealtimeBroadcaster.broadcastOrderbook(any())).thenReturn(true);

        handler.handleTextMessage(webSocketSession, new TextMessage(createUnifiedOrderbookPayload()));

        verify(orderMatchingService).matchByRealtimeOrderbook(any());
    }

    // NXT 거래 시작 전 대기 구간의 통합 호가는 화면에만 반영하고 주문을 체결하지 않는지 검증한다.
    @Test
    void doNotMatchOrdersDuringAfterMarketWait() throws Exception {
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.AFTER_MARKET_WAIT);
        when(marketSessionService.isRealtimeDataAvailable(MarketSession.AFTER_MARKET_WAIT))
                .thenReturn(false);

        handler.handleTextMessage(webSocketSession, new TextMessage(createUnifiedOrderbookPayload()));

        verify(stockRealtimeBroadcaster, never()).broadcastOrderbook(any());
        verify(orderMatchingService, never()).matchByRealtimeOrderbook(any());
    }

    // KIS H0UNASP0 통합 실시간 호가 테스트 payload를 만든다.
    private String createUnifiedOrderbookPayload() {
        return "0|H0UNASP0|001|"
                + "005930^154100^0^"
                + "71900^72000^72100^72200^72300^72400^72500^72600^72700^72800^"
                + "71800^71700^71600^71500^71400^71300^71200^71100^71000^70900^"
                + "91918^117942^92673^79708^106729^141988^176192^113906^134077^104229^"
                + "95221^159371^220746^284657^212742^195370^182710^209747^376432^158171^"
                + "1159362^2095167^0^0^0^0^0^0^0^0^0^0^"
                + "71950^150000^1^71940^160000^1";
    }
}
