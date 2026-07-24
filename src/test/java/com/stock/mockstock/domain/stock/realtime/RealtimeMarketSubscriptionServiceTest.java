// 통합 실시간 구독이 활성 거래 세션에만 수행되는지 검증한다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OpenOrderRealtimeSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeMarketSubscriptionServiceTest {

    @Mock
    private MarketSessionService marketSessionService;

    @Mock
    private StockRealtimeSessionRegistry sessionRegistry;

    @Mock
    private OpenOrderRealtimeSubscriptionService openOrderSubscriptionService;

    @Mock
    private KisRealtimeWebSocketClient realtimeWebSocketClient;

    @InjectMocks
    private RealtimeMarketSubscriptionService subscriptionService;

    // 장 마감 동시호가와 NXT 시작 전 대기 구간에서는 신규 실시간 구독을 만들지 않는다.
    @Test
    void skipSubscriptionDuringPausedSession() {
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.AFTER_MARKET_WAIT);
        when(marketSessionService.isRealtimeDataAvailable(MarketSession.AFTER_MARKET_WAIT))
                .thenReturn(false);

        subscriptionService.refreshRealtimeSubscriptions();

        verifyNoInteractions(
                sessionRegistry,
                openOrderSubscriptionService,
                realtimeWebSocketClient
        );
    }

    // NXT 애프터마켓이 시작되면 화면과 미체결 주문 종목을 모두 구독한다.
    @Test
    void subscribeTargetsDuringNxtAfterMarket() {
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.NXT_AFTER_MARKET);
        when(marketSessionService.isRealtimeDataAvailable(MarketSession.NXT_AFTER_MARKET))
                .thenReturn(true);
        when(sessionRegistry.getSubscribedSymbols()).thenReturn(Set.of("005930"));
        when(openOrderSubscriptionService.getOpenOrderSymbols()).thenReturn(Set.of("000660"));

        subscriptionService.refreshRealtimeSubscriptions();

        verify(realtimeWebSocketClient).subscribeTrade("005930");
        verify(realtimeWebSocketClient).subscribeOrderbook("005930");
        verify(realtimeWebSocketClient).subscribeTrade("000660");
        verify(realtimeWebSocketClient).subscribeOrderbook("000660");
    }
}
