// 현재가 Provider를 통해 종목 현재가 정보를 조회하는 서비스
package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.provider.StockQuoteProvider;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeTradeMessage;
import com.stock.mockstock.domain.stock.realtime.StockRealtimeSnapshotCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class StockQuoteService {

    private final StockQuoteProvider stockQuoteProvider;
    private final StockRealtimeSnapshotCache snapshotCache;
    private final MarketSessionService marketSessionService;

    // 종목코드로 현재가 정보 조회
    public StockQuoteResponse getQuote(String symbol) {
        StockQuoteResponse providerQuote = stockQuoteProvider.getQuote(symbol);
        MarketSession marketSession = marketSessionService.getCurrentSession();

        if (!marketSessionService.isRealtimeDataAvailable(marketSession)) {
            return providerQuote;
        }

        return snapshotCache.getTrade(symbol)
                .filter((tradeMessage) -> tradeMessage.getMarketSession() == marketSession)
                .map((tradeMessage) -> mergeRealtimeQuote(providerQuote, tradeMessage))
                .orElse(providerQuote);
    }

    // REST 종목 정보 위에 같은 시장 세션의 최신 통합 실시간 체결 정보를 덮어쓴다.
    private StockQuoteResponse mergeRealtimeQuote(
            StockQuoteResponse providerQuote,
            KisRealtimeTradeMessage tradeMessage
    ) {
        return new StockQuoteResponse(
                providerQuote.getSymbol(),
                providerQuote.getName(),
                tradeMessage.getCurrentPrice(),
                tradeMessage.getChangePrice(),
                BigDecimal.valueOf(tradeMessage.getChangeRate()),
                positiveOrFallback(tradeMessage.getAccumulatedVolume(), providerQuote.getVolume()),
                providerQuote.getTradingValue(),
                positiveOrFallback(tradeMessage.getOpenPrice(), providerQuote.getOpenPrice()),
                positiveOrFallback(tradeMessage.getHighPrice(), providerQuote.getHighPrice()),
                positiveOrFallback(tradeMessage.getLowPrice(), providerQuote.getLowPrice()),
                providerQuote.getBasePrice()
        );
    }

    // 실시간 값이 0이면 REST 응답에 포함된 마지막 정상 값을 유지한다.
    private Long positiveOrFallback(long realtimeValue, Long fallbackValue) {
        return realtimeValue > 0L ? realtimeValue : fallbackValue;
    }
}
