package com.stock.mockstock.domain.stock.dto.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisVolumeRankResponse(
        @JsonProperty("rt_cd") String rtCd,
        List<Output> output
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("mksc_shrn_iscd") String symbol,
            @JsonProperty("hts_kor_isnm") String name,
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("prdy_ctrt") String changeRate,
            @JsonProperty("acml_vol") String volume
    ) {
    }
}
