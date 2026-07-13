// KIS H0STASP0 실시간 호가 payload를 DTO로 변환하는 파서
package com.stock.mockstock.domain.stock.realtime;

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

    // 0|H0STASP0|001|005930^093730^0^... 형식에서 ^ 뒤 데이터를 분리한다.
    public KisRealtimeOrderbookMessage parse(String payload) {
        String[] pipeParts = payload.split("\\|", 4);

        if (pipeParts.length < 4) {
            throw new IllegalArgumentException("Invalid KIS realtime orderbook payload: " + payload);
        }

        String[] values = pipeParts[3].split("\\^");

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
                .build();
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        return Long.parseLong(value.replaceAll("[^0-9-]", ""));
    }
}