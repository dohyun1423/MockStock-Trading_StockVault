// Verifies storage, validation, and fallback behavior of the realtime stock snapshot cache.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockRealtimeSnapshotCacheTest {

    private final StockRealtimeSnapshotCache snapshotCache = new StockRealtimeSnapshotCache();

    // Verifies that an orderbook containing only zero values never replaces the valid screen state.
    @Test
    void rejectAllZeroOrderbook() {
        KisRealtimeOrderbookMessage zeroOrderbook = createOrderbook(0L, 0L, 0L, 0L);

        assertThat(snapshotCache.storeOrderbook(zeroOrderbook)).isEmpty();
        assertThat(snapshotCache.getOrderbook("005930")).isEmpty();
    }

    // Verifies that partial updates retain the last positive OHLC and accumulated volume values.
    @Test
    void retainPreviousTradeFieldsWhenNewValuesAreZero() {
        snapshotCache.storeTrade(createTrade(83_500L, 82_000L, 84_000L, 81_500L, 1_000L));

        KisRealtimeTradeMessage storedMessage = snapshotCache.storeTrade(
                        createTrade(83_600L, 0L, 0L, 0L, 0L)
                )
                .orElseThrow();

        assertThat(storedMessage.getCurrentPrice()).isEqualTo(83_600L);
        assertThat(storedMessage.getOpenPrice()).isEqualTo(82_000L);
        assertThat(storedMessage.getHighPrice()).isEqualTo(84_000L);
        assertThat(storedMessage.getLowPrice()).isEqualTo(81_500L);
        assertThat(storedMessage.getAccumulatedVolume()).isEqualTo(1_000L);
    }

    // Verifies that a valid orderbook is normalized and can be read by symbol.
    @Test
    void storeValidOrderbook() {
        KisRealtimeOrderbookMessage orderbook = createOrderbook(83_700L, 10L, 83_600L, 20L);

        KisRealtimeOrderbookMessage storedMessage = snapshotCache.storeOrderbook(orderbook)
                .orElseThrow();

        assertThat(storedMessage.getSymbol()).isEqualTo("005930");
        assertThat(snapshotCache.getOrderbook(" 005930 ")).contains(storedMessage);
    }

    // Creates a trade message with configurable values for cache tests.
    private KisRealtimeTradeMessage createTrade(
            long currentPrice,
            long openPrice,
            long highPrice,
            long lowPrice,
            long accumulatedVolume
    ) {
        return KisRealtimeTradeMessage.builder()
                .symbol(" 005930 ")
                .tradeTime("153000")
                .currentPrice(currentPrice)
                .openPrice(openPrice)
                .highPrice(highPrice)
                .lowPrice(lowPrice)
                .accumulatedVolume(accumulatedVolume)
                .marketSession(MarketSession.REGULAR)
                .build();
    }

    // Creates an orderbook message with one configurable level for cache tests.
    private KisRealtimeOrderbookMessage createOrderbook(
            long askPrice,
            long askQuantity,
            long bidPrice,
            long bidQuantity
    ) {
        return KisRealtimeOrderbookMessage.builder()
                .symbol(" 005930 ")
                .businessTime("153000")
                .hourClassCode("REGULAR")
                .levels(List.of(KisRealtimeOrderbookLevel.builder()
                        .level(1)
                        .askPrice(askPrice)
                        .askQuantity(askQuantity)
                        .bidPrice(bidPrice)
                        .bidQuantity(bidQuantity)
                        .build()))
                .totalAskQuantity(askQuantity)
                .totalBidQuantity(bidQuantity)
                .marketSession(MarketSession.REGULAR)
                .build();
    }
}
