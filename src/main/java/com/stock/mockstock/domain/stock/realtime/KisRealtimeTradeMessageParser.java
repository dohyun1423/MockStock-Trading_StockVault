// KIS H0STCNT0 실시간 체결가 payload를 DTO로 변환하는 파서
package com.stock.mockstock.domain.stock.realtime;

import org.springframework.stereotype.Component;

@Component
public class KisRealtimeTradeMessageParser {

    private static final int SYMBOL = 0;
    private static final int TRADE_TIME = 1;
    private static final int CURRENT_PRICE = 2;
    private static final int CHANGE_SIGN = 3;
    private static final int CHANGE_PRICE = 4;
    private static final int CHANGE_RATE = 5;
    private static final int OPEN_PRICE = 7;
    private static final int HIGH_PRICE = 8;
    private static final int LOW_PRICE = 9;
    private static final int ASK_PRICE = 10;
    private static final int BID_PRICE = 11;
    private static final int TRADE_VOLUME = 12;
    private static final int ACCUMULATED_VOLUME = 13;

    // 0|H0STCNT0|001|005930^093354^71900^... 형식에서 ^ 뒤 데이터를 분리한다.
    public KisRealtimeTradeMessage parse(String payload) {
        String[] pipeParts = payload.split("\\|", 4);

        if (pipeParts.length < 4) {
            throw new IllegalArgumentException("Invalid KIS realtime trade payload: " + payload);
        }

        String[] values = pipeParts[3].split("\\^");

        if (values.length <= ACCUMULATED_VOLUME) {
            throw new IllegalArgumentException("Invalid KIS realtime trade values: " + payload);
        }

        return KisRealtimeTradeMessage.builder()
                .symbol(values[SYMBOL])
                .tradeTime(values[TRADE_TIME])
                .currentPrice(parseLong(values[CURRENT_PRICE]))
                .changeSign(values[CHANGE_SIGN])
                .changePrice(parseLong(values[CHANGE_PRICE]))
                .changeRate(parseDouble(values[CHANGE_RATE]))
                .openPrice(parseLong(values[OPEN_PRICE]))
                .highPrice(parseLong(values[HIGH_PRICE]))
                .lowPrice(parseLong(values[LOW_PRICE]))
                .askPrice(parseLong(values[ASK_PRICE]))
                .bidPrice(parseLong(values[BID_PRICE]))
                .tradeVolume(parseLong(values[TRADE_VOLUME]))
                .accumulatedVolume(parseLong(values[ACCUMULATED_VOLUME]))
                .build();
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        return Long.parseLong(value.replaceAll("[^0-9-]", ""));
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }

        return Double.parseDouble(value.replaceAll("[^0-9.-]", ""));
    }
}