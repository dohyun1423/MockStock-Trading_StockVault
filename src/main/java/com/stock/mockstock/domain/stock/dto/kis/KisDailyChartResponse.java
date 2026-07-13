// KIS 일봉 차트 API 응답을 매핑하는 DTO다.
package com.stock.mockstock.domain.stock.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KisDailyChartResponse {

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg1")
    private String msg1;

    @JsonProperty("output2")
    private List<Output> output2;

    @Getter
    @Setter
    public static class Output {

        // 영업일자
        @JsonProperty("stck_bsop_date")
        private String businessDate;

        // 종가
        @JsonProperty("stck_clpr")
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

        // 누적 거래량
        @JsonProperty("acml_vol")
        private String accumulatedVolume;
    }
}
