// KIS 호가 API를 사용하지 않을 때 화면 테스트용 더미 호가 데이터를 제공한다.
package com.stock.mockstock.domain.stock.provider;

import com.stock.mockstock.domain.stock.dto.OrderbookLevelResponse;
import com.stock.mockstock.domain.stock.dto.OrderbookResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "kis.provider",
        havingValue = "dummy",
        matchIfMissing = true
)
public class DummyOrderbookProvider implements OrderbookProvider {

    // KIS 호가 API 연결 전까지 상세페이지 호가 탭을 채우는 더미 호가 데이터다.
    @Override
    public OrderbookResponse getOrderbook(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        long basePrice = getBasePrice(normalizedSymbol);
        long tickSize = getTickSize(basePrice);

        List<OrderbookLevelResponse> levels = new ArrayList<>();
        long totalAskQuantity = 0L;
        long totalBidQuantity = 0L;

        for (int i = 1; i <= 10; i++) {
            long askPrice = basePrice + tickSize * i;
            long bidPrice = basePrice - tickSize * i;
            long askQuantity = 12_000L + i * 2_700L;
            long bidQuantity = 14_000L + i * 2_400L;

            totalAskQuantity += askQuantity;
            totalBidQuantity += bidQuantity;

            levels.add(new OrderbookLevelResponse(
                    i,
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
                basePrice,
                basePrice,
                basePrice,
                basePrice + tickSize * 3,
                basePrice - tickSize * 3,
                1_000_000L,
                levels,
                totalAskQuantity,
                totalBidQuantity
        );
    }

    // 종목코드를 비교와 조회에 쓰기 좋은 형식으로 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    // 더미 호가 생성에 사용할 종목별 기준 가격을 반환한다.
    private long getBasePrice(String symbol) {
        return switch (symbol) {
            case "005930" -> 71_400L;
            case "000660" -> 188_500L;
            case "035420" -> 219_500L;
            default -> 50_000L;
        };
    }

    // 국내 주식 호가 단위에 맞춰 더미 가격 간격을 계산한다.
    private long getTickSize(long price) {
        if (price >= 500_000L) {
            return 1_000L;
        }

        if (price >= 100_000L) {
            return 500L;
        }

        if (price >= 50_000L) {
            return 100L;
        }

        if (price >= 10_000L) {
            return 50L;
        }

        return 10L;
    }

    // 기준가 대비 등락률을 계산한다.
    private double calculateRate(long price, long basePrice) {
        if (basePrice == 0L) {
            return 0.0;
        }

        return Math.round(((price - basePrice) * 10000.0 / basePrice)) / 100.0;
    }
}
