// 실시간 체결가를 기준으로 미체결/부분체결 주문의 자동 체결을 처리하는 서비스
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.repository.StockOrderRepository;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeTradeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private static final List<StockOrderStatus> MATCHABLE_STATUSES = List.of(
            StockOrderStatus.PENDING,
            StockOrderStatus.PARTIALLY_FILLED
    );

    private final StockOrderRepository stockOrderRepository;
    private final OrderExecutionService orderExecutionService;

    // KIS 실시간 체결가가 들어올 때 해당 종목의 미체결 주문을 검사하고 조건이 맞으면 체결한다.
    @Transactional
    public void matchByRealtimeTrade(KisRealtimeTradeMessage tradeMessage) {
        if (tradeMessage == null || tradeMessage.getSymbol() == null) {
            return;
        }

        Long currentPrice = tradeMessage.getCurrentPrice();

        if (currentPrice == null || currentPrice <= 0) {
            return;
        }

        List<StockOrder> stockOrders = stockOrderRepository.findAllByStockSymbolAndStatusInOrderByCreatedAtAsc(
                normalizeSymbol(tradeMessage.getSymbol()),
                MATCHABLE_STATUSES
        );

        for (StockOrder stockOrder : stockOrders) {
            matchOrder(stockOrder, currentPrice);
        }
    }

    // 지정가 조건이 현재 체결가와 맞으면 남은 수량 전체를 현재가로 체결한다.
    private void matchOrder(StockOrder stockOrder, Long currentPrice) {
        if (!stockOrder.isExecutableByPrice(currentPrice)) {
            return;
        }

        try {
            Integer executionQuantity = stockOrder.getRemainingQuantity();
            Long executedAmount = orderExecutionService.executeStockOrder(
                    stockOrder,
                    currentPrice,
                    executionQuantity
            );

            log.info(
                    "Pending order matched. orderId={}, symbol={}, type={}, price={}, quantity={}, amount={}",
                    stockOrder.getId(),
                    stockOrder.getStock().getSymbol(),
                    stockOrder.getOrderType(),
                    currentPrice,
                    executionQuantity,
                    executedAmount
            );
        } catch (RuntimeException e) {
            stockOrder.markFailed(e.getMessage());

            log.warn(
                    "Pending order matching failed. orderId={}, symbol={}, reason={}",
                    stockOrder.getId(),
                    stockOrder.getStock().getSymbol(),
                    e.getMessage(),
                    e
            );
        }
    }

    // KIS와 DB 조회 기준을 맞추기 위해 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }
}
