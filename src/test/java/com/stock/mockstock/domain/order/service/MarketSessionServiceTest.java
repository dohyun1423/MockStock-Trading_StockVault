// Verifies order and realtime policies for each Korean stock market session.
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketSessionServiceTest {

    private final MarketSessionService marketSessionService = new MarketSessionService();

    // 통합 실시간 데이터가 제공되는 정규장과 NXT 세션에서 즉시 체결을 허용하는지 검증한다.
    @Test
    void allowImmediateExecutionOnlyForSupportedRealtimeSessions() {
        assertThat(marketSessionService.isImmediateExecution(MarketSession.PRE_MARKET)).isTrue();
        assertThat(marketSessionService.isImmediateExecution(MarketSession.REGULAR)).isTrue();
        assertThat(
                marketSessionService.isImmediateExecution(MarketSession.NXT_AFTER_MARKET)
        ).isTrue();
        assertThat(
                marketSessionService.isImmediateExecution(MarketSession.AFTER_MARKET_CLOSING_PRICE)
        ).isFalse();
    }

    // 통합 실시간 데이터가 없는 장 마감 전환 구간은 예약 주문으로 처리하는지 검증한다.
    @Test
    void acceptAfterMarketClosingPriceOrderAsReservation() {
        assertThat(
                marketSessionService.isReservationAvailable(
                        MarketSession.AFTER_MARKET_CLOSING_PRICE
                )
        ).isTrue();
    }

    // Verifies that new KIS subscriptions pause only during the unsupported transition interval.
    @Test
    void pauseRealtimeSubscriptionsDuringTransitionSessions() {
        assertThat(
                marketSessionService.isRealtimeSubscriptionPausedSession(
                        MarketSession.AFTER_MARKET_WAIT
                )
        ).isTrue();
        assertThat(
                marketSessionService.isRealtimeSubscriptionPausedSession(
                        MarketSession.AFTER_MARKET_CLOSING_PRICE
                )
        ).isTrue();
        assertThat(
                marketSessionService.isRealtimeSubscriptionPausedSession(
                        MarketSession.NXT_AFTER_MARKET
                )
        ).isFalse();
    }

    // 실제 시간 경계가 NXT 프리마켓과 애프터마켓 세션으로 정확히 분리되는지 검증한다.
    @Test
    void resolveNxtTradingHours() {
        assertThat(marketSessionService.resolveSession(java.time.LocalTime.of(8, 0)))
                .isEqualTo(MarketSession.PRE_MARKET);
        assertThat(marketSessionService.resolveSession(java.time.LocalTime.of(8, 49, 59)))
                .isEqualTo(MarketSession.PRE_MARKET);
        assertThat(marketSessionService.resolveSession(java.time.LocalTime.of(8, 50)))
                .isEqualTo(MarketSession.OPENING_AUCTION);
        assertThat(marketSessionService.resolveSession(java.time.LocalTime.of(15, 39, 59)))
                .isEqualTo(MarketSession.AFTER_MARKET_WAIT);
        assertThat(marketSessionService.resolveSession(java.time.LocalTime.of(15, 40)))
                .isEqualTo(MarketSession.NXT_AFTER_MARKET);
        assertThat(marketSessionService.resolveSession(java.time.LocalTime.of(19, 59, 59)))
                .isEqualTo(MarketSession.NXT_AFTER_MARKET);
        assertThat(marketSessionService.resolveSession(java.time.LocalTime.of(20, 0)))
                .isEqualTo(MarketSession.CLOSED);
    }

    // 화면 갱신과 호가 자동체결이 가능한 통합 실시간 세션을 검증한다.
    @Test
    void identifyUnifiedRealtimeSessions() {
        assertThat(marketSessionService.isRealtimeDataAvailable(MarketSession.PRE_MARKET)).isTrue();
        assertThat(marketSessionService.isRealtimeDataAvailable(MarketSession.REGULAR)).isTrue();
        assertThat(
                marketSessionService.isRealtimeDataAvailable(MarketSession.NXT_AFTER_MARKET)
        ).isTrue();
        assertThat(
                marketSessionService.isRealtimeDataAvailable(MarketSession.AFTER_MARKET_WAIT)
        ).isFalse();
    }
}
