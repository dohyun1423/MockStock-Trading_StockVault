package com.stock.mockstock.domain.stock.provider;

import com.stock.mockstock.domain.stock.dto.VolumeRankItem;
import com.stock.mockstock.domain.stock.dto.kis.KisVolumeRankResponse;
import com.stock.mockstock.domain.stock.kis.KisTokenService;
import com.stock.mockstock.domain.stock.service.VolumeRankingUnavailableException;
import com.stock.mockstock.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KisVolumeRankingProvider {
    private final KisProperties kisProperties;
    private final KisTokenService kisTokenService;
    private final RestClient.Builder restClientBuilder;

    // 전체 시장 순위는 개별 종목 WebSocket 대신 KIS 순위 API 한 번으로 조회한다.
    public List<VolumeRankItem> getVolumeRanking() {
        if (!"kis".equals(kisProperties.getProvider())) {
            throw new VolumeRankingUnavailableException("거래량 순위는 KIS 연결 설정 후 이용할 수 있습니다.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        KisVolumeRankResponse response = restClientBuilder.clone()
                .baseUrl(kisProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build().get()
                .uri(builder -> builder.path("/uapi/domestic-stock/v1/quotations/volume-rank")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_COND_SCR_DIV_CODE", "20171")
                        .queryParam("FID_INPUT_ISCD", "0000")
                        .queryParam("FID_DIV_CLS_CODE", "0")
                        .queryParam("FID_BLNG_CLS_CODE", "0")
                        .queryParam("FID_TRGT_CLS_CODE", "111111111")
                        .queryParam("FID_TRGT_EXLS_CLS_CODE", "0000000000")
                        .queryParam("FID_INPUT_PRICE_1", "")
                        .queryParam("FID_INPUT_PRICE_2", "")
                        .queryParam("FID_VOL_CNT", "")
                        .queryParam("FID_INPUT_DATE_1", "")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kisTokenService.getAccessToken())
                .header("appkey", kisProperties.getAppKey())
                .header("appsecret", kisProperties.getAppSecret())
                .header("tr_id", "FHPST01710000")
                .header("custtype", "P")
                .retrieve().body(KisVolumeRankResponse.class);

        if (response == null || !"0".equals(response.rtCd()) || response.output() == null
                || response.output().isEmpty()) {
            throw new VolumeRankingUnavailableException("거래량 순위를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.");
        }
        return response.output().stream().map(this::toItem).toList();
    }

    private VolumeRankItem toItem(KisVolumeRankResponse.Output row) {
        String symbol = row.symbol() == null ? "" : row.symbol().trim();
        // KIS 전체 순위에는 6자리 주식/ETF 코드와 Q 접두어를 가진 7자리 ETN 코드가 함께 온다.
        if (!symbol.matches("(?:[0-9A-Z]{6}|Q[0-9]{6})") || row.name() == null || row.name().isBlank()) {
            throw new IllegalStateException("Invalid volume ranking symbol");
        }
        long price = Long.parseLong(number(row.currentPrice()));
        long volume = Long.parseLong(number(row.volume()));
        if (price <= 0 || volume < 0) {
            throw new IllegalStateException("Invalid volume ranking price or volume");
        }
        return new VolumeRankItem(0, symbol, row.name().trim(), price,
                new BigDecimal(number(row.changeRate())), volume, false);
    }

    private String number(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing volume ranking number");
        }
        return value.trim().replace(",", "");
    }
}
