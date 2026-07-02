// 애플리케이션 시작 후 기존 미체결 주문 종목의 KIS 실시간 구독을 복구한다.
package com.stock.mockstock.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenOrderRealtimeSubscriptionInitializer {

    private final OpenOrderRealtimeSubscriptionService openOrderRealtimeSubscriptionService;
    private final OrderMatchingService orderMatchingService;

    // 서버가 완전히 뜬 뒤 기존 미체결 주문을 현재가로 먼저 검사하고 남은 주문 종목을 실시간 구독한다.
    @EventListener(ApplicationReadyEvent.class)
    public void initializeOpenOrderSubscriptions() {
        orderMatchingService.matchOpenOrdersByCurrentQuotes();
        openOrderRealtimeSubscriptionService.subscribeOpenOrderSymbols();
    }
}
