// KIS 정규장/시간외 실시간 TR ID 매핑이 올바른지 검증하는 테스트다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisRealtimeTrIdTest {

    // 정규장 세션에서는 KRX·NXT 통합 체결가/호가 TR ID를 선택하는지 검증한다.
    @Test
    void resolveRegularRealtimeTrIds() {
        assertThat(KisRealtimeTrId.resolveTradeTrId(MarketSession.REGULAR)).isEqualTo("H0UNCNT0");
        assertThat(KisRealtimeTrId.resolveOrderbookTrId(MarketSession.REGULAR)).isEqualTo("H0UNASP0");
    }

    // NXT 애프터마켓도 동일한 통합 체결가/호가 TR ID를 사용하는지 검증한다.
    @Test
    void resolveNxtAfterMarketRealtimeTrIds() {
        assertThat(KisRealtimeTrId.resolveTradeTrId(MarketSession.NXT_AFTER_MARKET))
                .isEqualTo("H0UNCNT0");
        assertThat(KisRealtimeTrId.resolveOrderbookTrId(MarketSession.NXT_AFTER_MARKET))
                .isEqualTo("H0UNASP0");
    }

    // KIS WebSocket payload에서 TR ID를 추출하는지 검증한다.
    @Test
    void extractTrIdFromPayload() {
        String payload = "0|H0UNCNT0|001|005930^160354^71900";

        assertThat(KisRealtimeTrId.extractTrId(payload)).isEqualTo("H0UNCNT0");
    }

    // TR ID를 기준으로 거래 세션을 판별하는지 검증한다.
    @Test
    void resolveMarketSessionByTrId() {
        assertThat(KisRealtimeTrId.resolveMarketSession("H0STCNT0")).isEqualTo(MarketSession.REGULAR);
        assertThat(KisRealtimeTrId.resolveMarketSession("H0STOUP0")).isEqualTo(MarketSession.AFTER_HOURS_SINGLE_PRICE);
    }

    // NXT 단독 및 통합 TR ID를 현재 시장 세션 판별 대상으로 인식하는지 검증한다.
    @Test
    void identifyNxtAndUnifiedTrIds() {
        assertThat(KisRealtimeTrId.isNxtOrUnifiedTrId("H0NXCNT0")).isTrue();
        assertThat(KisRealtimeTrId.isNxtOrUnifiedTrId("H0NXASP0")).isTrue();
        assertThat(KisRealtimeTrId.isNxtOrUnifiedTrId("H0UNCNT0")).isTrue();
        assertThat(KisRealtimeTrId.isNxtOrUnifiedTrId("H0UNASP0")).isTrue();
        assertThat(KisRealtimeTrId.isNxtOrUnifiedTrId("H0STCNT0")).isFalse();
    }
}
