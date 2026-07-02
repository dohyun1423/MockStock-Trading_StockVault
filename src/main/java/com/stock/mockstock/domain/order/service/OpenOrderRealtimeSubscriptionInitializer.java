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

    // 서버가 완전히 뜬 뒤 DB에 남아있던 미체결 주문 종목을 실시간 체결가 구독 대상으로 등록한다.
    @EventListener(ApplicationReadyEvent.class)
    public void initializeOpenOrderSubscriptions() {
        openOrderRealtimeSubscriptionService.subscribeOpenOrderSymbols();
    }
}
