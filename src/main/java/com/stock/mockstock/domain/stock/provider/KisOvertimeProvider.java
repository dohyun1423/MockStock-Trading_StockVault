// KIS REST API로 시간외 현재가와 호가 원본 데이터를 조회하는 provider다.
package com.stock.mockstock.domain.stock.provider;

import com.stock.mockstock.domain.stock.dto.kis.KisOvertimeOrderbookResponse;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimePriceResponse;
import com.stock.mockstock.domain.stock.kis.KisTokenService;
import com.stock.mockstock.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kis.provider", havingValue = "kis")
public class KisOvertimeProvider {

    private static final String OVERTIME_PRICE_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-overtime-price";
    private static final String OVERTIME_ORDERBOOK_PATH =
            "/uapi/domestic-stock/v1/quotations/inquire-overtime-asking-price";
    private static final String OVERTIME_PRICE_TR_ID = "FHPST02300000";
    private static final String OVERTIME_ORDERBOOK_TR_ID = "FHPST02300400";
    private static final String DOMESTIC_STOCK_MARKET_CODE = "J";
    private static final String CUSTOMER_TYPE_PERSONAL = "P";

    private final KisProperties kisProperties;
    private final KisTokenService kisTokenService;
    private final RestClient.Builder restClientBuilder;

    // 종목코드 기준으로 KIS 시간외 현재가를 조회한다.
    public KisOvertimePriceResponse getPrice(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        KisOvertimePriceResponse response = restClientBuilder
                .baseUrl(kisProperties.getBaseUrl())
                .build()
                .get()
                .uri((uriBuilder) -> uriBuilder
                        .path(OVERTIME_PRICE_PATH)
                        .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_STOCK_MARKET_CODE)
                        .queryParam("FID_INPUT_ISCD", normalizedSymbol)
                        .build()
                )
                .headers(this::applyCommonHeaders)
                .header("tr_id", OVERTIME_PRICE_TR_ID)
                .retrieve()
                .body(KisOvertimePriceResponse.class);

        validatePriceResponse(response);
        log.debug("KIS overtime price loaded. symbol={}", normalizedSymbol);

        return response;
    }

    // 종목코드 기준으로 KIS 시간외 10단계 호가를 조회한다.
    public KisOvertimeOrderbookResponse getOrderbook(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        KisOvertimeOrderbookResponse response = restClientBuilder
                .baseUrl(kisProperties.getBaseUrl())
                .build()
                .get()
                .uri((uriBuilder) -> uriBuilder
                        .path(OVERTIME_ORDERBOOK_PATH)
                        .queryParam("FID_INPUT_ISCD", normalizedSymbol)
                        .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_STOCK_MARKET_CODE)
                        .build()
                )
                .headers(this::applyCommonHeaders)
                .header("tr_id", OVERTIME_ORDERBOOK_TR_ID)
                .retrieve()
                .body(KisOvertimeOrderbookResponse.class);

        validateOrderbookResponse(response);
        log.debug("KIS overtime orderbook loaded. symbol={}", normalizedSymbol);

        return response;
    }

    // KIS REST 요청에 공통 인증 헤더를 설정한다.
    private void applyCommonHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + kisTokenService.getAccessToken());
        headers.set("appkey", kisProperties.getAppKey());
        headers.set("appsecret", kisProperties.getAppSecret());
        headers.set("custtype", CUSTOMER_TYPE_PERSONAL);
    }

    // 시간외 현재가 응답이 정상인지 검증한다.
    private void validatePriceResponse(KisOvertimePriceResponse response) {
        if (response == null || response.getOutput() == null) {
            throw new IllegalStateException("KIS overtime price response is empty.");
        }

        if (!"0".equals(response.getRtCd())) {
            throw new IllegalStateException("KIS overtime price request failed. message=" + response.getMsg1());
        }
    }

    // 시간외 호가 응답이 정상인지 검증한다.
    private void validateOrderbookResponse(KisOvertimeOrderbookResponse response) {
        if (response == null || response.getEffectiveOutput() == null) {
            throw new IllegalStateException("KIS overtime orderbook response is empty.");
        }

        if (!"0".equals(response.getRtCd())) {
            throw new IllegalStateException("KIS overtime orderbook request failed. message=" + response.getMsg1());
        }
    }

    // KIS 요청에 사용할 수 있도록 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }
}
