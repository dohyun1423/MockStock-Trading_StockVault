// 활성 거래시간에 브라우저와 미체결 주문 종목의 KIS 통합 실시간 구독을 보장한다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OpenOrderRealtimeSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kis.provider", havingValue = "kis")
public class RealtimeMarketSubscriptionService {

    private final MarketSessionService marketSessionService;
    private final StockRealtimeSessionRegistry sessionRegistry;
    private final OpenOrderRealtimeSubscriptionService openOrderSubscriptionService;
    private final KisRealtimeWebSocketClient realtimeWebSocketClient;

    private final Set<String> routedSymbols = ConcurrentHashMap.newKeySet();

    // 현재 세션이 실시간 거래 가능 상태가 되면 필요한 모든 종목을 통합 WebSocket에 구독한다.
    @Scheduled(
            fixedDelayString = "${kis.realtime-subscription-refresh-interval-ms:5000}",
            initialDelayString = "${kis.realtime-subscription-initial-delay-ms:5000}"
    )
    public void refreshRealtimeSubscriptions() {
        MarketSession marketSession = marketSessionService.getCurrentSession();

        if (!marketSessionService.isRealtimeDataAvailable(marketSession)) {
            routedSymbols.clear();
            return;
        }

        Set<String> targetSymbols = getTargetSymbols();

        for (String symbol : targetSymbols) {
            if (!routedSymbols.add(symbol)) {
                continue;
            }

            realtimeWebSocketClient.subscribeTrade(symbol);
            realtimeWebSocketClient.subscribeOrderbook(symbol);
        }

        if (!targetSymbols.isEmpty()) {
            log.debug(
                    "Unified realtime subscription targets checked. session={}, symbolCount={}",
                    marketSession,
                    targetSymbols.size()
            );
        }
    }

    // 현재 화면에서 보는 종목과 자동체결 감시가 필요한 미체결 주문 종목을 합친다.
    private Set<String> getTargetSymbols() {
        Set<String> targetSymbols = new HashSet<>(sessionRegistry.getSubscribedSymbols());
        targetSymbols.addAll(openOrderSubscriptionService.getOpenOrderSymbols());

        return targetSymbols;
    }
}
