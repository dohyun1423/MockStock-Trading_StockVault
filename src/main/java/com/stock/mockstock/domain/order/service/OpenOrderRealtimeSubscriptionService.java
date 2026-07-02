// 미체결 주문이 있는 종목을 KIS 실시간 체결가 구독 대상에 등록하는 서비스
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.repository.StockOrderRepository;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenOrderRealtimeSubscriptionService {

    private static final List<StockOrderStatus> OPEN_ORDER_STATUSES = List.of(
            StockOrderStatus.PENDING,
            StockOrderStatus.PARTIALLY_FILLED
    );

    private final StockOrderRepository stockOrderRepository;
    private final KisRealtimeWebSocketClient kisRealtimeWebSocketClient;

    // 주문 트랜잭션이 커밋된 뒤에 해당 종목의 실시간 체결가를 구독한다.
    public void subscribeTradeAfterCommit(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        if (normalizedSymbol.isBlank()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            subscribeTrade(normalizedSymbol);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                subscribeTrade(normalizedSymbol);
            }
        });
    }

    // 서버 시작 시 DB에 이미 남아있는 미체결 주문 종목들을 다시 구독한다.
    @Transactional(readOnly = true)
    public void subscribeOpenOrderSymbols() {
        Set<String> symbols = stockOrderRepository.findAllByStatusIn(OPEN_ORDER_STATUSES)
                .stream()
                .map(StockOrder::getStock)
                .map((stock) -> normalizeSymbol(stock.getSymbol()))
                .filter((symbol) -> !symbol.isBlank())
                .collect(Collectors.toSet());

        symbols.forEach(this::subscribeTrade);

        log.info("Open order realtime subscriptions restored. count={}, symbols={}", symbols.size(), symbols);
    }

    // KIS 실시간 체결가 구독을 요청한다.
    private void subscribeTrade(String symbol) {
        kisRealtimeWebSocketClient.subscribeTrade(symbol);
    }

    // KIS와 DB 조회 기준을 맞추기 위해 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }
}
