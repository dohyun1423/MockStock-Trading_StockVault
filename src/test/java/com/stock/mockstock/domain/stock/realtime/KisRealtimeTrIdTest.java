// KIS 정규장/시간외 실시간 TR ID 매핑이 올바른지 검증하는 테스트다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisRealtimeTrIdTest {

    // 정규장 세션에서는 정규장 체결가/호가 TR ID를 선택하는지 검증한다.
    @Test
    void resolveRegularRealtimeTrIds() {
        assertThat(KisRealtimeTrId.resolveTradeTrId(MarketSession.REGULAR)).isEqualTo("H0STCNT0");
        assertThat(KisRealtimeTrId.resolveOrderbookTrId(MarketSession.REGULAR)).isEqualTo("H0STASP0");
    }

    // 시간외 단일가 세션에서는 시간외 체결가/호가 TR ID를 선택하는지 검증한다.
    @Test
    void resolveAfterHoursRealtimeTrIds() {
        assertThat(KisRealtimeTrId.resolveTradeTrId(MarketSession.AFTER_HOURS_SINGLE_PRICE)).isEqualTo("H0STOUP0");
        assertThat(KisRealtimeTrId.resolveOrderbookTrId(MarketSession.AFTER_HOURS_SINGLE_PRICE)).isEqualTo("H0STOAA0");
    }

    // KIS WebSocket payload에서 TR ID를 추출하는지 검증한다.
    @Test
    void extractTrIdFromPayload() {
        String payload = "0|H0STOUP0|001|005930^160354^71900";

        assertThat(KisRealtimeTrId.extractTrId(payload)).isEqualTo("H0STOUP0");
    }

    // TR ID를 기준으로 거래 세션을 판별하는지 검증한다.
    @Test
    void resolveMarketSessionByTrId() {
        assertThat(KisRealtimeTrId.resolveMarketSession("H0STCNT0")).isEqualTo(MarketSession.REGULAR);
        assertThat(KisRealtimeTrId.resolveMarketSession("H0STOUP0")).isEqualTo(MarketSession.AFTER_HOURS_SINGLE_PRICE);
    }
}
