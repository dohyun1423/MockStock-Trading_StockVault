// 통합 실시간 체결 스냅샷이 현재가 REST 응답에 안전하게 반영되는지 검증하는 테스트다.
package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.provider.StockQuoteProvider;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeTradeMessage;
import com.stock.mockstock.domain.stock.realtime.StockRealtimeSnapshotCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockQuoteServiceTest {

    @Mock
    private StockQuoteProvider stockQuoteProvider;

    @Mock
    private MarketSessionService marketSessionService;

    private StockRealtimeSnapshotCache snapshotCache;
    private StockQuoteService stockQuoteService;

    // 각 테스트가 독립된 실시간 스냅샷 캐시를 사용하도록 서비스를 구성한다.
    @BeforeEach
    void setUp() {
        snapshotCache = new StockRealtimeSnapshotCache();
        stockQuoteService = new StockQuoteService(
                stockQuoteProvider,
                snapshotCache,
                marketSessionService
        );
    }

    // NXT 애프터마켓에서는 REST 종가 대신 같은 세션의 통합 실시간 체결가를 반환하는지 검증한다.
    @Test
    void useUnifiedRealtimeTradeDuringNxtAfterMarket() {
        StockQuoteResponse providerQuote = createProviderQuote();

        when(stockQuoteProvider.getQuote("005930")).thenReturn(providerQuote);
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.NXT_AFTER_MARKET);
        when(marketSessionService.isRealtimeDataAvailable(MarketSession.NXT_AFTER_MARKET))
                .thenReturn(true);

        snapshotCache.storeTrade(KisRealtimeTradeMessage.builder()
                .symbol("005930")
                .currentPrice(251_000L)
                .changePrice(1_500L)
                .changeRate(0.60)
                .openPrice(249_500L)
                .highPrice(252_000L)
                .lowPrice(248_500L)
                .accumulatedVolume(7_000_000L)
                .marketSession(MarketSession.NXT_AFTER_MARKET)
                .build());

        StockQuoteResponse result = stockQuoteService.getQuote("005930");

        assertThat(result.getCurrentPrice()).isEqualTo(251_000L);
        assertThat(result.getChangePrice()).isEqualTo(1_500L);
        assertThat(result.getChangeRate()).isEqualByComparingTo("0.6");
        assertThat(result.getVolume()).isEqualTo(7_000_000L);
    }

    // 현재 시장과 다른 세션의 오래된 실시간 스냅샷은 REST 현재가를 덮어쓰지 않는지 검증한다.
    @Test
    void keepProviderQuoteWhenSnapshotSessionDoesNotMatch() {
        StockQuoteResponse providerQuote = createProviderQuote();

        when(stockQuoteProvider.getQuote("005930")).thenReturn(providerQuote);
        when(marketSessionService.getCurrentSession()).thenReturn(MarketSession.REGULAR);
        when(marketSessionService.isRealtimeDataAvailable(MarketSession.REGULAR))
                .thenReturn(true);

        snapshotCache.storeTrade(KisRealtimeTradeMessage.builder()
                .symbol("005930")
                .currentPrice(251_000L)
                .marketSession(MarketSession.NXT_AFTER_MARKET)
                .build());

        StockQuoteResponse result = stockQuoteService.getQuote("005930");

        assertThat(result).isSameAs(providerQuote);
    }

    // 테스트에서 공통으로 사용하는 KRX REST 현재가 응답을 생성한다.
    private StockQuoteResponse createProviderQuote() {
        return new StockQuoteResponse(
                "005930",
                "삼성전자",
                249_500L,
                0L,
                BigDecimal.ZERO,
                6_100_000L,
                1_500_000_000_000L,
                249_500L,
                249_500L,
                249_500L,
                249_500L
        );
    }
}
