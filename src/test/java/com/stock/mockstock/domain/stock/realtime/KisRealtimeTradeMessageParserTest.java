// KIS 실시간 체결가 payload가 DTO로 정상 변환되는지 검증하는 테스트
package com.stock.mockstock.domain.stock.realtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisRealtimeTradeMessageParserTest {

    private final KisRealtimeTradeMessageParser parser = new KisRealtimeTradeMessageParser();

    @Test
    void parseRealtimeTradePayload() {
        // KIS 문서 예시 형식의 H0STCNT0 실시간 체결가 payload
        String payload = "0|H0STCNT0|001|005930^093354^71900^5^-100^-0.14^72023.83^72100^72400^71700^71900^71800^1^3052507";

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
    }
}