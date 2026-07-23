// 15:30~16:00 시간외 데이터를 REST로 조회해 기존 실시간 화면과 주문 체결 흐름에 전달한다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OpenOrderRealtimeSubscriptionService;
import com.stock.mockstock.domain.order.service.OrderMatchingService;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimeOrderbookResponse;
import com.stock.mockstock.domain.stock.dto.kis.KisOvertimePriceResponse;
import com.stock.mockstock.domain.stock.provider.KisOvertimeProvider;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kis.provider", havingValue = "kis")
public class AfterMarketRealtimePollingService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    private final MarketSessionService marketSessionService;
    private final StockRealtimeSessionRegistry sessionRegistry;
    private final OpenOrderRealtimeSubscriptionService openOrderSubscriptionService;
    private final KisOvertimeProvider kisOvertimeProvider;
    private final KisOvertimeRealtimeMapper realtimeMapper;
    private final StockQuoteService stockQuoteService;
    private final StockRealtimeBroadcaster realtimeBroadcaster;
    private final OrderMatchingService orderMatchingService;
    private final KisRealtimeWebSocketClient realtimeWebSocketClient;

    private final Set<String> afterHoursSubscribedSymbols = ConcurrentHashMap.newKeySet();

    // 설정된 간격마다 거래 세션을 확인해 REST 폴링 또는 시간외 WebSocket 전환을 수행한다.
    @Scheduled(
            fixedDelayString = "${kis.after-market-polling-interval-ms:5000}",
            initialDelayString = "${kis.after-market-polling-initial-delay-ms:5000}"
    )
    public void routeAfterMarketRealtimeData() {
        MarketSession marketSession = marketSessionService.getCurrentSession();

        if (marketSessionService.isAfterMarketPollingSession(marketSession)) {
            afterHoursSubscribedSymbols.clear();
            pollTargetSymbols(marketSession);
            return;
        }

        if (marketSession == MarketSession.AFTER_HOURS_SINGLE_PRICE) {
            subscribeAfterHoursWebSocketTargets();
            return;
        }

        afterHoursSubscribedSymbols.clear();
    }

    // 브라우저 구독 종목과 미체결 주문 종목을 중복 없이 합쳐 시간외 REST 데이터를 조회한다.
    private void pollTargetSymbols(MarketSession marketSession) {
        Set<String> targetSymbols = getTargetSymbols();

        for (String symbol : targetSymbols) {
            pollSymbol(symbol, marketSession);
        }

        if (!targetSymbols.isEmpty()) {
            log.debug(
                    "After-market REST polling completed. session={}, symbolCount={}",
                    marketSession,
                    targetSymbols.size()
            );
        }
    }

    // 한 종목의 시간외 현재가와 호가를 조회해 화면 갱신과 필요한 주문 체결을 수행한다.
    private void pollSymbol(String symbol, MarketSession marketSession) {
        broadcastTradeSafely(symbol, marketSession);
        broadcastOrderbookSafely(symbol, marketSession);
    }

    // 시간외 현재가 조회 실패가 다른 종목의 폴링을 막지 않도록 개별 처리한다.
    private void broadcastTradeSafely(String symbol, MarketSession marketSession) {
        try {
            KisOvertimePriceResponse response = kisOvertimeProvider.getPrice(symbol);
            KisRealtimeTradeMessage tradeMessage = realtimeMapper.toTradeMessage(
                    symbol,
                    marketSession,
                    response
            );

            if (tradeMessage.getCurrentPrice() <= 0L) {
                tradeMessage = createRegularQuoteFallback(symbol, marketSession);
            }

            if (tradeMessage != null && tradeMessage.getCurrentPrice() > 0L) {
                realtimeBroadcaster.broadcastTrade(tradeMessage);
            }
        } catch (RuntimeException e) {
            KisRealtimeTradeMessage fallbackMessage = createRegularQuoteFallback(symbol, marketSession);

            if (fallbackMessage != null) {
                realtimeBroadcaster.broadcastTrade(fallbackMessage);
            }

            log.warn(
                    "After-market price polling failed. symbol={}, session={}",
                    symbol,
                    marketSession,
                    e
            );
        }
    }

    // 시간외 호가 조회 실패가 다른 종목의 폴링을 막지 않도록 개별 처리한다.
    private void broadcastOrderbookSafely(String symbol, MarketSession marketSession) {
        try {
            KisOvertimeOrderbookResponse response = kisOvertimeProvider.getOrderbook(symbol);
            KisRealtimeOrderbookMessage orderbookMessage = realtimeMapper.toOrderbookMessage(
                    symbol,
                    marketSession,
                    response
            );

            realtimeBroadcaster.broadcastOrderbook(orderbookMessage);

            if (marketSession == MarketSession.AFTER_MARKET_CLOSING_PRICE) {
                orderMatchingService.matchByRealtimeOrderbook(orderbookMessage);
            }
        } catch (RuntimeException e) {
            log.warn(
                    "After-market orderbook polling failed. symbol={}, session={}",
                    symbol,
                    marketSession,
                    e
            );
        }
    }

    // 시간외 현재가가 아직 형성되지 않았을 때 정규 현재가 API의 마지막 가격으로 화면을 유지한다.
    private KisRealtimeTradeMessage createRegularQuoteFallback(String symbol, MarketSession marketSession) {
        try {
            StockQuoteResponse quote = stockQuoteService.getQuote(symbol);

            if (quote == null || quote.getCurrentPrice() == null || quote.getCurrentPrice() <= 0L) {
                return null;
            }

            return KisRealtimeTradeMessage.builder()
                    .symbol(normalizeSymbol(symbol))
                    .tradeTime(LocalTime.now(KOREA_ZONE).format(TRADE_TIME_FORMATTER))
                    .currentPrice(valueOrZero(quote.getCurrentPrice()))
                    .changeSign(resolveChangeSign(quote.getChangePrice()))
                    .changePrice(valueOrZero(quote.getChangePrice()))
                    .changeRate(decimalOrZero(quote.getChangeRate()))
                    .openPrice(valueOrZero(quote.getOpenPrice()))
                    .highPrice(valueOrZero(quote.getHighPrice()))
                    .lowPrice(valueOrZero(quote.getLowPrice()))
                    .askPrice(0L)
                    .bidPrice(0L)
                    .tradeVolume(0L)
                    .accumulatedVolume(valueOrZero(quote.getVolume()))
                    .marketSession(marketSession)
                    .build();
        } catch (RuntimeException e) {
            log.warn("Regular quote fallback failed. symbol={}, session={}", symbol, marketSession, e);
            return null;
        }
    }

    // 16시 전부터 보고 있던 종목을 시간외 단일가 WebSocket 구독으로 전환한다.
    private void subscribeAfterHoursWebSocketTargets() {
        Set<String> targetSymbols = getTargetSymbols();

        for (String symbol : targetSymbols) {
            if (!afterHoursSubscribedSymbols.add(symbol)) {
                continue;
            }

            realtimeWebSocketClient.subscribeTrade(symbol);
            realtimeWebSocketClient.subscribeOrderbook(symbol);
        }
    }

    // 화면 구독 종목과 미체결 주문 종목을 하나의 조회 대상 목록으로 합친다.
    private Set<String> getTargetSymbols() {
        Set<String> targetSymbols = new HashSet<>(sessionRegistry.getSubscribedSymbols());
        targetSymbols.addAll(openOrderSubscriptionService.getOpenOrderSymbols());

        return targetSymbols;
    }

    // KIS 등락 부호 코드 형식에 맞춰 가격 방향을 변환한다.
    private String resolveChangeSign(Long changePrice) {
        if (changePrice == null || changePrice == 0L) {
            return "3";
        }

        return changePrice > 0L ? "2" : "5";
    }

    // nullable Long 값을 실시간 메시지에서 사용할 기본값으로 변환한다.
    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    // nullable BigDecimal 등락률을 실시간 메시지에서 사용할 값으로 변환한다.
    private double decimalOrZero(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    // KIS와 브라우저 구독 기준을 맞추기 위해 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }
}
