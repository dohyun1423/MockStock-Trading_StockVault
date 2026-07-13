// KIS 실시간 호가 payload가 DTO로 정상 변환되는지 검증하는 테스트
package com.stock.mockstock.domain.stock.realtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisRealtimeOrderbookMessageParserTest {

    private final KisRealtimeOrderbookMessageParser parser = new KisRealtimeOrderbookMessageParser();

    @Test
    void parseRealtimeOrderbookPayload() {
        // KIS 문서 예시 형식의 H0STASP0 실시간 호가 payload
        String payload = "0|H0STASP0|001|"
                + "005930^093730^0^"
                + "71900^72000^72100^72200^72300^72400^72500^72600^72700^72800^"
                + "71800^71700^71600^71500^71400^71300^71200^71100^71000^70900^"
                + "91918^117942^92673^79708^106729^141988^176192^113906^134077^104229^"
                + "95221^159371^220746^284657^212742^195370^182710^209747^376432^158171^"
                + "1159362^2095167";

        KisRealtimeOrderbookMessage result = parser.parse(payload);

        assertThat(result.getSymbol()).isEqualTo("005930");
        assertThat(result.getBusinessTime()).isEqualTo("093730");
        assertThat(result.getHourClassCode()).isEqualTo("0");
        assertThat(result.getLevels()).hasSize(10);

        KisRealtimeOrderbookLevel firstLevel = result.getLevels().get(0);
        assertThat(firstLevel.getLevel()).isEqualTo(1);
        assertThat(firstLevel.getAskPrice()).isEqualTo(71900L);
        assertThat(firstLevel.getAskQuantity()).isEqualTo(91918L);
        assertThat(firstLevel.getBidPrice()).isEqualTo(71800L);
        assertThat(firstLevel.getBidQuantity()).isEqualTo(95221L);

        assertThat(result.getTotalAskQuantity()).isEqualTo(1159362L);
        assertThat(result.getTotalBidQuantity()).isEqualTo(2095167L);
    }
}