package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.dto.OrderExecutionNotification;
import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.entity.Trade;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.repository.TradeRepository;
import com.stock.mockstock.domain.portfolio.entity.Holding;
import com.stock.mockstock.domain.portfolio.repository.HoldingRepository;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.realtime.StockRealtimeBroadcaster;
import com.stock.mockstock.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderExecutionService {

    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final StockRealtimeBroadcaster stockRealtimeBroadcaster;

    // StockOrder의 남은 수량 중 지정된 수량을 실제 체결 처리한다.
    public Long executeStockOrder(
            StockOrder stockOrder,
            Long executionPrice,
            Integer executionQuantity
    ) {
        if (stockOrder.getOrderType() == OrderType.BUY) {
            return executeBuy(stockOrder, executionPrice, executionQuantity);
        }

        return executeSell(stockOrder, executionPrice, executionQuantity);
    }

    // 가격과 수량으로 총 주문/체결 금액을 계산한다.
    public Long calculateTotalAmount(Long price, Integer quantity) {
        try {
            return Math.multiplyExact(price, quantity.longValue());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("주문 금액이 너무 큽니다.");
        }
    }

    // 예약 현금으로 묶인 매수 주문을 체결하고 보유 종목을 늘린다.
    private Long executeBuy(
            StockOrder stockOrder,
            Long executionPrice,
            Integer executionQuantity
    ) {
        User user = stockOrder.getUser();
        Stock stock = stockOrder.getStock();
        Long executedAmount = calculateTotalAmount(executionPrice, executionQuantity);
        Long reservedAmount = calculateTotalAmount(stockOrder.getOrderPrice(), executionQuantity);

        user.executeReservedBuy(reservedAmount, executedAmount);

        Holding holding = holdingRepository.findByUserAndStockForUpdate(user, stock)
                .orElseGet(() -> Holding.builder()
                        .user(user)
                        .stock(stock)
                        .quantity(0)
                        .reservedQuantity(0)
                        .averagePrice(0L)
                        .build());

        holding.buy(executionQuantity, executionPrice);
        holdingRepository.save(holding);

        stockOrder.fill(executionQuantity);
        saveTrade(user, stock, stockOrder.getOrderType(), executionQuantity, executionPrice, executedAmount);
        notifyOrderExecuted(stockOrder, executionQuantity, executionPrice, executedAmount);

        return executedAmount;
    }

    // 예약 수량으로 묶인 매도 주문을 체결하고 현금을 늘린다.
    private Long executeSell(
            StockOrder stockOrder,
            Long executionPrice,
            Integer executionQuantity
    ) {
        User user = stockOrder.getUser();
        Stock stock = stockOrder.getStock();
        Long executedAmount = calculateTotalAmount(executionPrice, executionQuantity);

        Holding holding = holdingRepository.findByUserAndStockForUpdate(user, stock)
                .orElseThrow(() -> new IllegalArgumentException("보유 중인 종목이 아닙니다."));

        holding.executeReservedSell(executionQuantity);

        if (holding.isEmpty()) {
            holdingRepository.delete(holding);
        }

        user.increaseCash(executedAmount);
        stockOrder.fill(executionQuantity);
        saveTrade(user, stock, stockOrder.getOrderType(), executionQuantity, executionPrice, executedAmount);
        notifyOrderExecuted(stockOrder, executionQuantity, executionPrice, executedAmount);

        return executedAmount;
    }

    // 실제 체결 결과를 거래내역으로 저장한다.
    private void saveTrade(
            User user,
            Stock stock,
            OrderType orderType,
            Integer quantity,
            Long price,
            Long totalAmount
    ) {
        Trade trade = Trade.builder()
                .user(user)
                .stock(stock)
                .orderType(orderType)
                .quantity(quantity)
                .price(price)
                .totalAmount(totalAmount)
                .build();

        tradeRepository.save(trade);
    }

    // 체결된 주문을 로그인 중인 사용자 브라우저에 알림으로 전달한다.
    private void notifyOrderExecuted(
            StockOrder stockOrder,
            Integer quantity,
            Long price,
            Long totalAmount
    ) {
        User user = stockOrder.getUser();
        Stock stock = stockOrder.getStock();
        String orderTypeText = stockOrder.getOrderType() == OrderType.BUY ? "매수" : "매도";

        OrderExecutionNotification notification = new OrderExecutionNotification(
                stockOrder.getId(),
                stock.getName(),
                stock.getSymbol(),
                stockOrder.getOrderType(),
                quantity,
                price,
                totalAmount,
                stock.getName() + " " + orderTypeText + " 주문이 체결되었습니다."
        );

        stockRealtimeBroadcaster.broadcastOrderExecution(user.getEmail(), notification);
    }
}
