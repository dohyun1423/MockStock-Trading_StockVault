// KIS 호가 API에서 실제 매도/매수 호가 데이터를 조회하는 provider다.
package com.stock.mockstock.domain.stock.provider;

import com.stock.mockstock.domain.stock.dto.OrderbookLevelResponse;
import com.stock.mockstock.domain.stock.dto.OrderbookResponse;
import com.stock.mockstock.domain.stock.dto.kis.KisOrderbookResponse;
import com.stock.mockstock.domain.stock.kis.KisTokenService;
import com.stock.mockstock.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kis.provider", havingValue = "kis")
public class KisOrderbookProvider implements OrderbookProvider {

    private static final String ORDERBOOK_PATH = "/uapi/domestic-stock/v1/quotations/inquire-asking-price-exp-ccn";
    private static final String DOMESTIC_STOCK_MARKET_CODE = "J";
    private static final String ORDERBOOK_TR_ID = "FHKST01010200";

    private final KisProperties kisProperties;
    private final KisTokenService kisTokenService;
    private final RestClient.Builder restClientBuilder;

    // KIS 호가 API를 호출해서 실제 매도/매수 10단계 호가를 조회한다.
    @Override
    public OrderbookResponse getOrderbook(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        log.info("KIS orderbook provider used. symbol={}", normalizedSymbol);

        KisOrderbookResponse response = restClientBuilder
                .baseUrl(kisProperties.getBaseUrl())
                .build()
                .get()
                .uri((uriBuilder) -> uriBuilder
                        .path(ORDERBOOK_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_STOCK_MARKET_CODE)
                        .queryParam("FID_INPUT_ISCD", normalizedSymbol)
                        .build()
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kisTokenService.getAccessToken())
                .header("appkey", kisProperties.getAppKey())
                .header("appsecret", kisProperties.getAppSecret())
                .header("tr_id", ORDERBOOK_TR_ID)
                .retrieve()
                .body(KisOrderbookResponse.class);

        validateResponse(response);

        Map<String, String> output1 = response.getOutput1();
        Map<String, String> output2 = response.getOutput2();

        long currentPrice = parseLong(output2.get("stck_prpr"));
        long basePrice = parseLong(output2.get("stck_sdpr"));
        long openPrice = parseLong(output2.get("stck_oprc"));
        long highPrice = parseLong(output2.get("stck_hgpr"));
        long lowPrice = parseLong(output2.get("stck_lwpr"));
        long volume = parseLong(output2.get("acml_vol"));

        List<OrderbookLevelResponse> levels = new ArrayList<>();

        for (int level = 1; level <= 6; level++) {
            long askPrice = parseLong(output1.get("askp" + level));
            long askQuantity = parseLong(output1.get("askp_rsqn" + level));
            long bidPrice = parseLong(output1.get("bidp" + level));
            long bidQuantity = parseLong(output1.get("bidp_rsqn" + level));

            levels.add(new OrderbookLevelResponse(
                    level,
                    askPrice,
                    askQuantity,
                    calculateRate(askPrice, basePrice),
                    bidPrice,
                    bidQuantity,
                    calculateRate(bidPrice, basePrice)
            ));
        }

        return new OrderbookResponse(
                normalizedSymbol,
                currentPrice,
                basePrice,
                openPrice,
                highPrice,
                lowPrice,
                volume,
                levels,
                parseLong(output1.get("total_askp_rsqn")),
                parseLong(output1.get("total_bidp_rsqn"))
        );
    }

    // KIS 호가 응답이 정상 구조와 성공 코드를 가지고 있는지 검증한다.
    private void validateResponse(KisOrderbookResponse response) {
        if (response == null || response.getOutput1() == null || response.getOutput2() == null) {
            throw new IllegalStateException("KIS orderbook response is empty.");
        }

        if (!"0".equals(response.getRtCd())) {
            throw new IllegalStateException("KIS orderbook request failed. message=" + response.getMsg1());
        }
    }

    // 종목코드를 KIS 요청에 사용할 수 있는 형식으로 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    // KIS 문자열 숫자 값을 long 타입으로 변환한다.
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        return Long.parseLong(value.replaceAll("[^0-9-]", ""));
    }

    // 기준가 대비 호가 등락률을 계산한다.
    private double calculateRate(long price, long basePrice) {
        if (basePrice == 0L || price == 0L) {
            return 0.0;
        }

        return Math.round(((price - basePrice) * 10000.0 / basePrice)) / 100.0;
    }
}
