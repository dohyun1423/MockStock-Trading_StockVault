// KIS 시간외 REST 응답이 기존 실시간 체결가와 호가 메시지로 정확히 변환되는지 검증한다.
package com.stock.mockstock.domain.stock.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimeOrderbookResponse;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimePriceResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KisOvertimeRealtimeMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KisOvertimeRealtimeMapper mapper = new KisOvertimeRealtimeMapper();

    // 시간외 현재가 필드가 체결가 메시지의 가격, 등락률, 거래량으로 변환되는지 검증한다.
    @Test
    void mapOvertimePriceResponse() throws Exception {
        String json = """
                {
                  "rt_cd": "0",
                  "msg_cd": "MCA00000",
                  "msg1": "정상처리 되었습니다.",
                  "output": {
                    "ovtm_untp_prpr": "83600",
                    "ovtm_untp_prdy_vrss": "-100",
                    "ovtm_untp_prdy_vrss_sign": "5",
                    "ovtm_untp_prdy_ctrt": "-0.12",
                    "ovtm_untp_vol": "3500",
                    "ovtm_untp_oprc": "83600",
                    "ovtm_untp_hgpr": "83700",
                    "ovtm_untp_lwpr": "83500",
                    "ovtm_untp_sdpr": "83700",
                    "askp": "83700",
                    "bidp": "83600"
                  }
                }
                """;
        KisOvertimePriceResponse response = objectMapper.readValue(json, KisOvertimePriceResponse.class);

        KisRealtimeTradeMessage result = mapper.toTradeMessage(
                "005930",
                MarketSession.AFTER_MARKET_CLOSING_PRICE,
                response
        );

        assertThat(result.getSymbol()).isEqualTo("005930");
        assertThat(result.getCurrentPrice()).isEqualTo(83_600L);
        assertThat(result.getChangePrice()).isEqualTo(-100L);
        assertThat(result.getChangeRate()).isEqualTo(-0.12);
        assertThat(result.getAskPrice()).isEqualTo(83_700L);
        assertThat(result.getBidPrice()).isEqualTo(83_600L);
        assertThat(result.getAccumulatedVolume()).isEqualTo(3_500L);
        assertThat(result.getMarketSession()).isEqualTo(MarketSession.AFTER_MARKET_CLOSING_PRICE);
    }

    // 시간외 현재가가 0이면 예상 체결가가 화면용 현재가로 사용되는지 검증한다.
    @Test
    void useExpectedPriceWhenCurrentPriceIsZero() throws Exception {
        String json = """
                {
                  "rt_cd": "0",
                  "output": {
                    "ovtm_untp_prpr": "0",
                    "ovtm_untp_antc_cnpr": "83500"
                  }
                }
                """;
        KisOvertimePriceResponse response = objectMapper.readValue(json, KisOvertimePriceResponse.class);

        KisRealtimeTradeMessage result = mapper.toTradeMessage(
                "005930",
                MarketSession.AFTER_MARKET_WAIT,
                response
        );

        assertThat(result.getCurrentPrice()).isEqualTo(83_500L);
        assertThat(result.getMarketSession()).isEqualTo(MarketSession.AFTER_MARKET_WAIT);
    }

    // 시간외 10단계 호가와 총잔량이 기존 호가 메시지로 변환되는지 검증한다.
    @Test
    void mapOvertimeOrderbookResponse() throws Exception {
        String json = """
                {
                  "rt_cd": "0",
                  "output": {
                    "ovtm_untp_last_hour": "154501",
                    "ovtm_untp_askp1": "83700",
                    "ovtm_untp_askp_rsqn1": "4498",
                    "ovtm_untp_bidp1": "83600",
                    "ovtm_untp_bidp_rsqn1": "1219",
                    "ovtm_untp_total_askp_rsqn": "25794",
                    "ovtm_untp_total_bidp_rsqn": "34615"
                  }
                }
                """;
        KisOvertimeOrderbookResponse response = objectMapper.readValue(
                json,
                KisOvertimeOrderbookResponse.class
        );

        KisRealtimeOrderbookMessage result = mapper.toOrderbookMessage(
                "005930",
                MarketSession.AFTER_MARKET_CLOSING_PRICE,
                response
        );

        assertThat(result.getBusinessTime()).isEqualTo("154501");
        assertThat(result.getLevels()).hasSize(10);
        assertThat(result.getLevels().get(0).getAskPrice()).isEqualTo(83_700L);
        assertThat(result.getLevels().get(0).getAskQuantity()).isEqualTo(4_498L);
        assertThat(result.getLevels().get(0).getBidPrice()).isEqualTo(83_600L);
        assertThat(result.getLevels().get(0).getBidQuantity()).isEqualTo(1_219L);
        assertThat(result.getTotalAskQuantity()).isEqualTo(25_794L);
        assertThat(result.getTotalBidQuantity()).isEqualTo(34_615L);
        assertThat(result.getMarketSession()).isEqualTo(MarketSession.AFTER_MARKET_CLOSING_PRICE);
    }
}
