// 차트 가격 이력 조회와 짧은 캐싱을 담당하는 서비스다.
package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.stock.dto.StockPriceHistoryResponse;
import com.stock.mockstock.domain.stock.provider.StockPriceHistoryProvider;
import com.stock.mockstock.global.policy.ApplicationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceHistoryService {

    private static final long CHART_CACHE_SECONDS = 20;

    private final StockPriceHistoryProvider stockPriceHistoryProvider;
    private final Map<String, CachedHistories> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                // 가장 오래 사용하지 않은 항목부터 제거해 차트 캐시 크기를 제한한다.
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedHistories> eldest) {
                    return size() > ApplicationPolicy.MAX_CHART_CACHE_ENTRIES;
                }
            }
    );

    // 차트 초기 데이터만 짧게 캐싱해서 같은 종목/기간 반복 조회 시 KIS 호출을 줄인다.
    public List<StockPriceHistoryResponse> getPriceHistories(String symbol, String period) {
        String cacheKey = normalizeCacheKey(symbol, period);
        CachedHistories cached = cache.get(cacheKey);

        if (cached != null && cached.isUsable()) {
            log.info("Stock price history cache hit. key={}", cacheKey);
            return cached.histories();
        }

        try {
            List<StockPriceHistoryResponse> histories = stockPriceHistoryProvider.getPriceHistories(symbol, period);
            cache.put(cacheKey, new CachedHistories(histories, LocalDateTime.now().plusSeconds(CHART_CACHE_SECONDS)));
            return histories;
        } catch (RuntimeException e) {
            log.warn("Stock price history lookup failed. symbol={}, period={}", symbol, period, e);
            return List.of();
        }
    }

    // 종목코드와 기간을 캐시 키로 사용할 수 있게 정규화한다.
    private String normalizeCacheKey(String symbol, String period) {
        return String.valueOf(symbol).trim().toUpperCase() + ":" + String.valueOf(period).trim().toUpperCase();
    }

    // 캐시된 차트 데이터와 만료 시각을 함께 보관한다.
    private record CachedHistories(
            List<StockPriceHistoryResponse> histories,
            LocalDateTime expiresAt
    ) {
        // 캐시 만료 전인지 확인한다.
        private boolean isUsable() {
            return expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
        }
    }
}
