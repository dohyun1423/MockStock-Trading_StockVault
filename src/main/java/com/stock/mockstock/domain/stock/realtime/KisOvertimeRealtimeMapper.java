// KIS 시간외 REST 응답을 기존 실시간 체결가와 호가 메시지로 변환한다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimeOrderbookResponse;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimePriceResponse;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class KisOvertimeRealtimeMapper {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private static final int ORDERBOOK_LEVEL_COUNT = 10;

    // 시간외 현재가 응답을 브라우저 체결가 메시지로 변환한다.
    public KisRealtimeTradeMessage toTradeMessage(
            String symbol,
            MarketSession marketSession,
            KisOvertimePriceResponse response
    ) {
        KisOvertimePriceResponse.Output output = response.getOutput();
        long currentPrice = firstPositive(output.getCurrentPrice(), output.getExpectedPrice());

        return KisRealtimeTradeMessage.builder()
                .symbol(normalizeSymbol(symbol))
                .tradeTime(LocalTime.now(KOREA_ZONE).format(TRADE_TIME_FORMATTER))
                .currentPrice(currentPrice)
                .changeSign(output.getChangeSign())
                .changePrice(parseLong(output.getChangePrice()))
                .changeRate(parseDouble(output.getChangeRate()))
                .openPrice(parseLong(output.getOpenPrice()))
                .highPrice(parseLong(output.getHighPrice()))
                .lowPrice(parseLong(output.getLowPrice()))
                .askPrice(parseLong(output.getAskp()))
                .bidPrice(parseLong(output.getBidp()))
                .tradeVolume(0L)
                .accumulatedVolume(parseLong(output.getVolume()))
                .marketSession(marketSession)
                .build();
    }

    // 시간외 호가 응답을 브라우저 호가 메시지로 변환한다.
    public KisRealtimeOrderbookMessage toOrderbookMessage(
            String symbol,
            MarketSession marketSession,
            KisOvertimeOrderbookResponse response
    ) {
        Map<String, String> output = response.getEffectiveOutput();
        List<KisRealtimeOrderbookLevel> levels = new ArrayList<>();

        for (int level = 1; level <= ORDERBOOK_LEVEL_COUNT; level++) {
            levels.add(KisRealtimeOrderbookLevel.builder()
                    .level(level)
                    .askPrice(parseLong(output.get("ovtm_untp_askp" + level)))
                    .askQuantity(parseLong(output.get("ovtm_untp_askp_rsqn" + level)))
                    .bidPrice(parseLong(output.get("ovtm_untp_bidp" + level)))
                    .bidQuantity(parseLong(output.get("ovtm_untp_bidp_rsqn" + level)))
                    .build());
        }

        return KisRealtimeOrderbookMessage.builder()
                .symbol(normalizeSymbol(symbol))
                .businessTime(output.get("ovtm_untp_last_hour"))
                .hourClassCode(marketSession.name())
                .levels(levels)
                .totalAskQuantity(parseLong(output.get("ovtm_untp_total_askp_rsqn")))
                .totalBidQuantity(parseLong(output.get("ovtm_untp_total_bidp_rsqn")))
                .marketSession(marketSession)
                .build();
    }

    // 현재가가 비어 있을 때 예상 체결가를 대신 사용할 수 있도록 첫 양수 값을 반환한다.
    private long firstPositive(String first, String second) {
        long firstValue = parseLong(first);

        if (firstValue > 0L) {
            return firstValue;
        }

        return parseLong(second);
    }

    // KIS 문자열 숫자를 long 값으로 안전하게 변환한다.
    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        String normalizedValue = value.replaceAll("[^0-9-]", "");

        if (normalizedValue.isBlank() || "-".equals(normalizedValue)) {
            return 0L;
        }

        return Long.parseLong(normalizedValue);
    }

    // KIS 등락률 문자열을 double 값으로 안전하게 변환한다.
    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }

        return Double.parseDouble(value.trim());
    }

    // KIS와 브라우저 구독 기준을 맞추기 위해 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }
}
