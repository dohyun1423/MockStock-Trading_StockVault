package com.stock.mockstock.domain.order.repository;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StockOrderRepository extends JpaRepository<StockOrder, Long> {

    // 미체결/부분체결 상태인 모든 주문을 조회한다.
    List<StockOrder> findAllByStatusIn(Collection<StockOrderStatus> statuses);

    // 사용자의 미체결/부분체결 주문 목록을 최신순으로 조회한다.
    List<StockOrder> findAllByUserAndStatusInOrderByCreatedAtDesc(
            User user,
            Collection<StockOrderStatus> statuses
    );

    // 특정 종목의 매칭 대상 주문을 접수 순서대로 조회한다.
    List<StockOrder> findAllByStockSymbolAndStatusInOrderByCreatedAtAsc(
            String symbol,
            Collection<StockOrderStatus> statuses
    );

    // 매수 주문은 높은 주문 가격을 먼저 보고, 같은 가격이면 먼저 접수된 주문을 우선한다.
    List<StockOrder> findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceDescCreatedAtAsc(
            String symbol,
            OrderType orderType,
            Collection<StockOrderStatus> statuses
    );

    // 매도 주문은 낮은 주문 가격을 먼저 보고, 같은 가격이면 먼저 접수된 주문을 우선한다.
    List<StockOrder> findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceAscCreatedAtAsc(
            String symbol,
            OrderType orderType,
            Collection<StockOrderStatus> statuses
    );
}
