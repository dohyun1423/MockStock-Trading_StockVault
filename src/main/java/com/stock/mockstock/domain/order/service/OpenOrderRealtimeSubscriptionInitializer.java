// 애플리케이션 시작 후 기존 미체결 주문 종목의 KIS 실시간 구독을 복구한다.
package com.stock.mockstock.domain.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kis.provider", havingValue = "kis")
public class OpenOrderRealtimeSubscriptionInitializer {

    private final OpenOrderRealtimeSubscriptionService openOrderRealtimeSubscriptionService;

    // 서버가 완전히 뜬 뒤 기존 미체결 주문 종목을 실시간 구독한다.
    // 시작 시 현재가만 보고 전량 체결하지 않도록 체결은 이후 KIS 실시간 이벤트에서만 수행한다.
    @EventListener(ApplicationReadyEvent.class)
    public void initializeOpenOrderSubscriptions() {
        openOrderRealtimeSubscriptionService.subscribeOpenOrderSymbols();
    }
}
