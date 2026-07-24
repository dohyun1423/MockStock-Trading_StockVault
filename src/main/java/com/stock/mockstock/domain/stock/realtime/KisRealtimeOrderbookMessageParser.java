// KIS KRX·NXT·통합 실시간 호가 payload를 공통 DTO로 변환하는 파서
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KisRealtimeOrderbookMessageParser {

    private static final int SYMBOL = 0;
    private static final int BUSINESS_TIME = 1;
    private static final int HOUR_CLASS_CODE = 2;
    private static final int ASK_PRICE_START = 3;
    private static final int BID_PRICE_START = 13;
    private static final int ASK_QUANTITY_START = 23;
    private static final int BID_QUANTITY_START = 33;
    private static final int TOTAL_ASK_QUANTITY = 43;
    private static final int TOTAL_BID_QUANTITY = 44;
    private static final int ORDERBOOK_LEVEL_COUNT = 10;
    private static final int AFTER_HOURS_ASK_PRICE_START = 3;
    private static final int AFTER_HOURS_BID_PRICE_START = 12;
    private static final int AFTER_HOURS_ASK_QUANTITY_START = 21;
    private static final int AFTER_HOURS_BID_QUANTITY_START = 30;
    private static final int AFTER_HOURS_TOTAL_ASK_QUANTITY = 41;
    private static final int AFTER_HOURS_TOTAL_BID_QUANTITY = 42;
    private static final int AFTER_HOURS_ORDERBOOK_LEVEL_COUNT = 9;

    // 0|H0STASP0|001|005930^093730^0^... 형식에서 ^ 뒤 데이터를 분리한다.
    public KisRealtimeOrderbookMessage parse(String payload) {
        return parse(payload, MarketSession.REGULAR);
    }

    // 정규장/시간외 호가 payload를 공통 DTO로 변환하고 데이터가 들어온 거래 세션을 함께 담는다.
    public KisRealtimeOrderbookMessage parse(String payload, MarketSession marketSession) {
        String[] pipeParts = payload.split("\\|", 4);

        if (pipeParts.length < 4) {
            throw new IllegalArgumentException("Invalid KIS realtime orderbook payload: " + payload);
        }

        String[] values = pipeParts[3].split("\\^");

        if (marketSession == MarketSession.AFTER_HOURS_SINGLE_PRICE) {
            return parseAfterHoursOrderbook(payload, values, marketSession);
        }

        if (values.length <= TOTAL_BID_QUANTITY) {
            throw new IllegalArgumentException("Invalid KIS realtime orderbook values: " + payload);
        }

        List<KisRealtimeOrderbookLevel> levels = new ArrayList<>();

        for (int index = 0; index < ORDERBOOK_LEVEL_COUNT; index++) {
            levels.add(KisRealtimeOrderbookLevel.builder()
                    .level(index + 1)
                    .askPrice(parseLong(values[ASK_PRICE_START + index]))
                    .askQuantity(parseLong(values[ASK_QUANTITY_START + index]))
                    .bidPrice(parseLong(values[BID_PRICE_START + index]))
                    .bidQuantity(parseLong(values[BID_QUANTITY_START + index]))
                    .build());
        }

        return KisRealtimeOrderbookMessage.builder()
                .symbol(values[SYMBOL])
                .businessTime(values[BUSINESS_TIME])
                .hourClassCode(values[HOUR_CLASS_CODE])
                .levels(levels)
                .totalAskQuantity(parseLong(values[TOTAL_ASK_QUANTITY]))
                .totalBidQuantity(parseLong(values[TOTAL_BID_QUANTITY]))
                .marketSession(marketSession)
                .build();
    }

    // 시간외 단일가 호가 payload는 9단계 구조이므로 전용 인덱스로 DTO를 생성한다.
    private KisRealtimeOrderbookMessage parseAfterHoursOrderbook(
            String payload,
            String[] values,
            MarketSession marketSession
    ) {
        if (values.length <= AFTER_HOURS_TOTAL_BID_QUANTITY) {
            throw new IllegalArgumentException("Invalid KIS after-hours orderbook values: " + payload);
        }

        List<KisRealtimeOrderbookLevel> levels = new ArrayList<>();

        for (int index = 0; index < AFTER_HOURS_ORDERBOOK_LEVEL_COUNT; index++) {
            levels.add(KisRealtimeOrderbookLevel.builder()
                    .level(index + 1)
                    .askPrice(parseLong(values[AFTER_HOURS_ASK_PRICE_START + index]))
                    .askQuantity(parseLong(values[AFTER_HOURS_ASK_QUANTITY_START + index]))
                    .bidPrice(parseLong(values[AFTER_HOURS_BID_PRICE_START + index]))
                    .bidQuantity(parseLong(values[AFTER_HOURS_BID_QUANTITY_START + index]))
                    .build());
        }

        return KisRealtimeOrderbookMessage.builder()
                .symbol(values[SYMBOL])
                .businessTime(values[BUSINESS_TIME])
                .hourClassCode(values[HOUR_CLASS_CODE])
                .levels(levels)
                .totalAskQuantity(parseLong(values[AFTER_HOURS_TOTAL_ASK_QUANTITY]))
                .totalBidQuantity(parseLong(values[AFTER_HOURS_TOTAL_BID_QUANTITY]))
                .marketSession(marketSession)
                .build();
    }

    // KIS 문자열 숫자 값을 long 타입으로 변환한다.
    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        return Long.parseLong(value.replaceAll("[^0-9-]", ""));
    }
}
