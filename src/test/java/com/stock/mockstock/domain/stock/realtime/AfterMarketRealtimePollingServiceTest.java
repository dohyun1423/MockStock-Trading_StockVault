// Verifies that unsupported transition sessions keep snapshots and 16:00 starts KIS subscriptions.
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
class AfterMarketRealtimePollingServiceTest {

    @Mock
    private MarketSessionService marketSessionService;

    @Mock
    private StockRealtimeSessionRegistry sessionRegistry;

    @Mock
    private OpenOrderRealtimeSubscriptionService openOrderSubscriptionService;

    @Mock
    private KisRealtimeWebSocketClient realtimeWebSocketClient;

    @InjectMocks
    private AfterMarketRealtimePollingService pollingService;

    // Verifies that 15:30-15:40 does not request incompatible overtime data.
    @Test
    void keepLastSnapshotDuringAfterMarketWait() {
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.AFTER_MARKET_WAIT);

        pollingService.routeAfterMarketRealtimeData();

        verifyNoInteractions(
                sessionRegistry,
                openOrderSubscriptionService,
                realtimeWebSocketClient
        );
    }

    // Verifies that 15:40-16:00 does not request incompatible single-price data.
    @Test
    void keepLastSnapshotDuringAfterMarketClosingPrice() {
        when(marketSessionService.getCurrentSession())
                .thenReturn(MarketSession.AFTER_MARKET_CLOSING_PRICE);

        pollingService.routeAfterMarketRealtimeData();

        verifyNoInteractions(
                sessionRegistry,
                openOrderSubscriptionService,
                realtimeWebSocketClient
        );
    }

    // Verifies that browser and open-order symbols subscribe at the 16:00 single-price session.
    @Test
    void subscribeAfterHoursWebSocketAtSinglePriceSession() {
        when(marketSessionService.getCurrentSession())
                .thenReturn(MarketSession.AFTER_HOURS_SINGLE_PRICE);
        when(sessionRegistry.getSubscribedSymbols()).thenReturn(Set.of("005930"));
        when(openOrderSubscriptionService.getOpenOrderSymbols()).thenReturn(Set.of("000660"));

        pollingService.routeAfterMarketRealtimeData();

        verify(realtimeWebSocketClient).subscribeTrade("005930");
        verify(realtimeWebSocketClient).subscribeOrderbook("005930");
        verify(realtimeWebSocketClient).subscribeTrade("000660");
        verify(realtimeWebSocketClient).subscribeOrderbook("000660");
    }
}
