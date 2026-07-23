// 시간외 REST 폴링이 세션에 맞게 화면 갱신, 주문 체결, WebSocket 전환을 수행하는지 검증한다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OpenOrderRealtimeSubscriptionService;
import com.stock.mockstock.domain.order.service.OrderMatchingService;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimeOrderbookResponse;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimePriceResponse;
import com.stock.mockstock.domain.stock.provider.KisOvertimeProvider;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private KisOvertimeProvider kisOvertimeProvider;

    @Mock
    private KisOvertimeRealtimeMapper realtimeMapper;

    @Mock
    private StockQuoteService stockQuoteService;

    @Mock
    private StockRealtimeBroadcaster realtimeBroadcaster;

    @Mock
    private OrderMatchingService orderMatchingService;

    @Mock
    private KisRealtimeWebSocketClient realtimeWebSocketClient;

    @InjectMocks
    private AfterMarketRealtimePollingService pollingService;

    // 15:30~15:40에는 현재가와 호가를 전송하지만 주문 체결은 수행하지 않는지 검증한다.
    @Test
    void broadcastWithoutMatchingDuringAfterMarketWait() {
        KisOvertimePriceResponse priceResponse = new KisOvertimePriceResponse();
        KisOvertimeOrderbookResponse orderbookResponse = new KisOvertimeOrderbookResponse();
        KisRealtimeTradeMessage tradeMessage = createTradeMessage(MarketSession.AFTER_MARKET_WAIT);
        KisRealtimeOrderbookMessage orderbookMessage = createOrderbookMessage(MarketSession.AFTER_MARKET_WAIT);

        preparePollingSession(MarketSession.AFTER_MARKET_WAIT);
        when(kisOvertimeProvider.getPrice("005930")).thenReturn(priceResponse);
        when(kisOvertimeProvider.getOrderbook("005930")).thenReturn(orderbookResponse);
        when(realtimeMapper.toTradeMessage("005930", MarketSession.AFTER_MARKET_WAIT, priceResponse))
                .thenReturn(tradeMessage);
        when(realtimeMapper.toOrderbookMessage("005930", MarketSession.AFTER_MARKET_WAIT, orderbookResponse))
                .thenReturn(orderbookMessage);

        pollingService.routeAfterMarketRealtimeData();

        verify(realtimeBroadcaster).broadcastTrade(tradeMessage);
        verify(realtimeBroadcaster).broadcastOrderbook(orderbookMessage);
        verify(orderMatchingService, never()).matchByRealtimeOrderbook(orderbookMessage);
    }

    // 15:40~16:00에는 호가를 전송한 뒤 호가 잔량 기준 주문 체결을 수행하는지 검증한다.
    @Test
    void matchOrdersDuringAfterMarketClosingPrice() {
        KisOvertimePriceResponse priceResponse = new KisOvertimePriceResponse();
        KisOvertimeOrderbookResponse orderbookResponse = new KisOvertimeOrderbookResponse();
        KisRealtimeTradeMessage tradeMessage = createTradeMessage(MarketSession.AFTER_MARKET_CLOSING_PRICE);
        KisRealtimeOrderbookMessage orderbookMessage = createOrderbookMessage(
                MarketSession.AFTER_MARKET_CLOSING_PRICE
        );

        preparePollingSession(MarketSession.AFTER_MARKET_CLOSING_PRICE);
        when(kisOvertimeProvider.getPrice("005930")).thenReturn(priceResponse);
        when(kisOvertimeProvider.getOrderbook("005930")).thenReturn(orderbookResponse);
        when(realtimeMapper.toTradeMessage(
                "005930",
                MarketSession.AFTER_MARKET_CLOSING_PRICE,
                priceResponse
        )).thenReturn(tradeMessage);
        when(realtimeMapper.toOrderbookMessage(
                "005930",
                MarketSession.AFTER_MARKET_CLOSING_PRICE,
                orderbookResponse
        )).thenReturn(orderbookMessage);

        pollingService.routeAfterMarketRealtimeData();

        verify(realtimeBroadcaster).broadcastTrade(tradeMessage);
        verify(realtimeBroadcaster).broadcastOrderbook(orderbookMessage);
        verify(orderMatchingService).matchByRealtimeOrderbook(orderbookMessage);
    }

    // 16시가 되면 기존 폴링 대상 종목을 시간외 체결가와 호가 WebSocket으로 전환하는지 검증한다.
    @Test
    void subscribeAfterHoursWebSocketAtSinglePriceSession() {
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.AFTER_HOURS_SINGLE_PRICE);
        when(sessionRegistry.getSubscribedSymbols()).thenReturn(Set.of("005930"));
        when(openOrderSubscriptionService.getOpenOrderSymbols()).thenReturn(Set.of("000660"));

        pollingService.routeAfterMarketRealtimeData();

        verify(realtimeWebSocketClient).subscribeTrade("005930");
        verify(realtimeWebSocketClient).subscribeOrderbook("005930");
        verify(realtimeWebSocketClient).subscribeTrade("000660");
        verify(realtimeWebSocketClient).subscribeOrderbook("000660");
    }

    // 시간외 REST 폴링 테스트에 필요한 세션과 대상 종목을 준비한다.
    private void preparePollingSession(MarketSession marketSession) {
        when(marketSessionService.getCurrentSession()).thenReturn(marketSession);
        when(marketSessionService.isAfterMarketPollingSession(marketSession)).thenReturn(true);
        when(sessionRegistry.getSubscribedSymbols()).thenReturn(Set.of("005930"));
        when(openOrderSubscriptionService.getOpenOrderSymbols()).thenReturn(Set.of());
    }

    // 시간외 폴링 테스트에 사용할 체결가 메시지를 생성한다.
    private KisRealtimeTradeMessage createTradeMessage(MarketSession marketSession) {
        return KisRealtimeTradeMessage.builder()
                .symbol("005930")
                .tradeTime("154500")
                .currentPrice(83_600L)
                .marketSession(marketSession)
                .build();
    }

    // 시간외 폴링 테스트에 사용할 호가 메시지를 생성한다.
    private KisRealtimeOrderbookMessage createOrderbookMessage(MarketSession marketSession) {
        return KisRealtimeOrderbookMessage.builder()
                .symbol("005930")
                .businessTime("154500")
                .hourClassCode(marketSession.name())
                .levels(List.of(KisRealtimeOrderbookLevel.builder()
                        .level(1)
                        .askPrice(83_700L)
                        .askQuantity(10L)
                        .bidPrice(83_600L)
                        .bidQuantity(10L)
                        .build()))
                .totalAskQuantity(10L)
                .totalBidQuantity(10L)
                .marketSession(marketSession)
                .build();
    }
}
