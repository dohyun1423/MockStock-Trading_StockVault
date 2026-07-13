// KIS 주식당일분봉조회 응답을 매핑하는 DTO
package com.stock.mockstock.domain.stock.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KisMinuteChartResponse {

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg1")
    private String msg1;

    @JsonProperty("output2")
    private List<Output> output2;

    @Getter
    @Setter
    public static class Output {

        // 주식 영업일자
        @JsonProperty("stck_bsop_date")
        private String businessDate;

        // 주식 체결시간
        @JsonProperty("stck_cntg_hour")
        private String tradeTime;

        // 현재가를 분봉의 종가로 사용한다.
        @JsonProperty("stck_prpr")
        private String closePrice;

        // 시가
        @JsonProperty("stck_oprc")
        private String openPrice;

        // 고가
        @JsonProperty("stck_hgpr")
        private String highPrice;

        // 저가
        @JsonProperty("stck_lwpr")
        private String lowPrice;

        // 체결 거래량
        @JsonProperty("cntg_vol")
        private String volume;
    }
}