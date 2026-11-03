package com.stock.mockstock.domain.stock.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stock.mockstock.domain.stock.kis.KisTokenService;
import com.stock.mockstock.domain.stock.service.VolumeRankingUnavailableException;
import com.stock.mockstock.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KisStockCatalogProvider {
    private final KisProperties properties;
    private final KisTokenService tokens;
    private final RestClient.Builder restClientBuilder;

    // 탐색에서 선택한 미등록 종목만 실제 현재가/시장/상장수량으로 확인한다.
    public Output getCatalogQuote(String symbol) {
        if (!"kis".equals(properties.getProvider())) {
            throw new VolumeRankingUnavailableException("KIS 연결 후 종목 정보를 확인할 수 있습니다.");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        Response response = restClientBuilder.clone().baseUrl(properties.getBaseUrl()).requestFactory(factory)
                .build().get().uri(builder -> builder.path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J").queryParam("FID_INPUT_ISCD", symbol).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getAccessToken())
                .header("appkey", properties.getAppKey()).header("appsecret", properties.getAppSecret())
                .header("tr_id", "FHKST01010100").header("custtype", "P")
                .retrieve().body(Response.class);
        if (response == null || !"0".equals(response.rtCd()) || response.output() == null) {
            throw new VolumeRankingUnavailableException("종목 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return response.output();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(@JsonProperty("rt_cd") String rtCd, Output output) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("stck_shrn_iscd") String symbol,
            @JsonProperty("rprs_mrkt_kor_name") String market,
            @JsonProperty("bstp_kor_isnm") String sector,
            @JsonProperty("stck_prpr") String price,
            @JsonProperty("prdy_vrss") String changePrice,
            @JsonProperty("prdy_ctrt") String changeRate,
            @JsonProperty("acml_vol") String volume,
            @JsonProperty("lstn_stcn") String listedShares,
            String per,
            String eps
    ) {}
}
