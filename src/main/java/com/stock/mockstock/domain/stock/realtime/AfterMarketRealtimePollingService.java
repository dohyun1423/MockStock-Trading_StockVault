// Routes subscribed symbols to the KIS single-price WebSocket only during its supported session.
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
public class AfterMarketRealtimePollingService {

    private final MarketSessionService marketSessionService;
    private final StockRealtimeSessionRegistry sessionRegistry;
    private final OpenOrderRealtimeSubscriptionService openOrderSubscriptionService;
    private final KisRealtimeWebSocketClient realtimeWebSocketClient;

    private final Set<String> afterHoursSubscribedSymbols = ConcurrentHashMap.newKeySet();

    // Starts single-price subscriptions at 16:00 and keeps the last valid snapshot before then.
    @Scheduled(
            fixedDelayString = "${kis.after-market-polling-interval-ms:5000}",
            initialDelayString = "${kis.after-market-polling-initial-delay-ms:5000}"
    )
    public void routeAfterMarketRealtimeData() {
        MarketSession marketSession = marketSessionService.getCurrentSession();

        if (marketSession == MarketSession.AFTER_HOURS_SINGLE_PRICE) {
            subscribeAfterHoursWebSocketTargets();
            return;
        }

        afterHoursSubscribedSymbols.clear();
    }

    // Subscribes browser and open-order symbols once per single-price session.
    private void subscribeAfterHoursWebSocketTargets() {
        Set<String> targetSymbols = getTargetSymbols();

        for (String symbol : targetSymbols) {
            if (!afterHoursSubscribedSymbols.add(symbol)) {
                continue;
            }

            realtimeWebSocketClient.subscribeTrade(symbol);
            realtimeWebSocketClient.subscribeOrderbook(symbol);
        }

        if (!targetSymbols.isEmpty()) {
            log.debug(
                    "After-hours single-price WebSocket targets checked. symbolCount={}",
                    targetSymbols.size()
            );
        }
    }

    // Combines symbols viewed in browsers with symbols required by open orders.
    private Set<String> getTargetSymbols() {
        Set<String> targetSymbols = new HashSet<>(sessionRegistry.getSubscribedSymbols());
        targetSymbols.addAll(openOrderSubscriptionService.getOpenOrderSymbols());

        return targetSymbols;
    }
}
