// KIS 실시간 체결가 payload가 DTO로 정상 변환되는지 검증하는 테스트
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisRealtimeTradeMessageParserTest {

    private final KisRealtimeTradeMessageParser parser = new KisRealtimeTradeMessageParser();

    @Test
    void parseRealtimeTradePayload() {
        // KIS 문서 예시 형식의 H0UNCNT0 통합 실시간 체결가 payload
        String payload = "0|H0UNCNT0|001|005930^093354^71900^5^-100^-0.14^72023.83^72100^72400^71700^71900^71800^1^3052507";

        KisRealtimeTradeMessage result = parser.parse(payload);

        assertThat(result.getSymbol()).isEqualTo("005930");
        assertThat(result.getTradeTime()).isEqualTo("093354");
        assertThat(result.getCurrentPrice()).isEqualTo(71900L);
        assertThat(result.getChangeSign()).isEqualTo("5");
        assertThat(result.getChangePrice()).isEqualTo(-100L);
        assertThat(result.getChangeRate()).isEqualTo(-0.14);
        assertThat(result.getOpenPrice()).isEqualTo(72100L);
        assertThat(result.getHighPrice()).isEqualTo(72400L);
        assertThat(result.getLowPrice()).isEqualTo(71700L);
        assertThat(result.getAskPrice()).isEqualTo(71900L);
        assertThat(result.getBidPrice()).isEqualTo(71800L);
        assertThat(result.getTradeVolume()).isEqualTo(1L);
        assertThat(result.getAccumulatedVolume()).isEqualTo(3052507L);
        assertThat(result.getMarketSession()).isEqualTo(MarketSession.REGULAR);
    }

    // 시간외 체결가 payload를 파싱할 때 시간외 단일가 세션 값이 유지되는지 검증한다.
    @Test
    void parseAfterHoursTradePayload() {
        String payload = "0|H0STOUP0|001|005930^160354^71900^5^-100^-0.14^72023.83^72100^72400^71700^71900^71800^1^3052507";

        KisRealtimeTradeMessage result = parser.parse(payload, MarketSession.AFTER_HOURS_SINGLE_PRICE);

        assertThat(result.getSymbol()).isEqualTo("005930");
        assertThat(result.getCurrentPrice()).isEqualTo(71900L);
        assertThat(result.getMarketSession()).isEqualTo(MarketSession.AFTER_HOURS_SINGLE_PRICE);
    }
}
