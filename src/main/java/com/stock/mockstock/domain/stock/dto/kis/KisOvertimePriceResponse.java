// KIS 국내주식 시간외현재가 API 응답 값을 매핑하는 DTO다.
package com.stock.mockstock.domain.stock.dto.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KisOvertimePriceResponse {

    @JsonProperty("rt_cd")
    private String rtCd;

    @JsonProperty("msg_cd")
    private String msgCd;

    private String msg1;

    private Output output;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output {

        @JsonProperty("ovtm_untp_prpr")
        private String currentPrice;

        @JsonProperty("ovtm_untp_prdy_vrss")
        private String changePrice;

        @JsonProperty("ovtm_untp_prdy_vrss_sign")
        private String changeSign;

        @JsonProperty("ovtm_untp_prdy_ctrt")
        private String changeRate;

        @JsonProperty("ovtm_untp_vol")
        private String volume;

        @JsonProperty("ovtm_untp_tr_pbmn")
        private String tradingValue;

        @JsonProperty("ovtm_untp_oprc")
        private String openPrice;

        @JsonProperty("ovtm_untp_hgpr")
        private String highPrice;

        @JsonProperty("ovtm_untp_lwpr")
        private String lowPrice;

        @JsonProperty("ovtm_untp_sdpr")
        private String basePrice;

        @JsonProperty("ovtm_untp_antc_cnpr")
        private String expectedPrice;

        private String askp;

        private String bidp;
    }
}
