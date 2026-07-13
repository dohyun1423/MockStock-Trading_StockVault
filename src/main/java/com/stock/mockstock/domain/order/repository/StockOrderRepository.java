// 주문 DB 조회를 담당하는 Repository
package com.stock.mockstock.domain.order.repository;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockOrderRepository extends JpaRepository<StockOrder, Long> {

    // 미체결, 부분체결 상태의 모든 주문을 조회한다.
    List<StockOrder> findAllByStatusIn(Collection<StockOrderStatus> statuses);

    // 주문 수정/취소는 같은 주문을 동시에 바꾸지 못하도록 쓰기 락으로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StockOrder o where o.id = :id")
    Optional<StockOrder> findByIdForUpdate(@Param("id") Long id);

    // 자동체결 대상 주문은 체결 중 중복 변경을 막기 위해 쓰기 락으로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StockOrder o where o.status in :statuses")
    List<StockOrder> findAllByStatusInForUpdate(@Param("statuses") Collection<StockOrderStatus> statuses);

    // 사용자의 미체결, 부분체결 주문 목록을 최신순으로 조회한다.
    List<StockOrder> findAllByUserAndStatusInOrderByCreatedAtDesc(
            User user,
            Collection<StockOrderStatus> statuses
    );

    // 특정 종목의 매칭 대상 주문을 접수 순서대로 조회한다.
    List<StockOrder> findAllByStockSymbolAndStatusInOrderByCreatedAtAsc(
            String symbol,
            Collection<StockOrderStatus> statuses
    );

    // 실시간 체결가 기준 자동체결 대상 주문을 쓰기 락으로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from StockOrder o
            where o.stock.symbol = :symbol
              and o.status in :statuses
            order by o.createdAt asc
            """)
    List<StockOrder> findAllByStockSymbolAndStatusInOrderByCreatedAtAscForUpdate(
            @Param("symbol") String symbol,
            @Param("statuses") Collection<StockOrderStatus> statuses
    );

    // 매수 주문은 높은 주문 가격, 빠른 접수 순서로 조회한다.
    List<StockOrder> findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceDescCreatedAtAsc(
            String symbol,
            OrderType orderType,
            Collection<StockOrderStatus> statuses
    );

    // 매수 자동체결은 높은 주문가, 빠른 접수 순서로 쓰기 락을 잡고 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from StockOrder o
            where o.stock.symbol = :symbol
              and o.orderType = :orderType
              and o.status in :statuses
            order by o.orderPrice desc, o.createdAt asc
            """)
    List<StockOrder> findBuyOrdersForMatching(
            @Param("symbol") String symbol,
            @Param("orderType") OrderType orderType,
            @Param("statuses") Collection<StockOrderStatus> statuses
    );

    // 매도 주문은 낮은 주문 가격, 빠른 접수 순서로 조회한다.
    List<StockOrder> findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceAscCreatedAtAsc(
            String symbol,
            OrderType orderType,
            Collection<StockOrderStatus> statuses
    );

    // 매도 자동체결은 낮은 주문가, 빠른 접수 순서로 쓰기 락을 잡고 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from StockOrder o
            where o.stock.symbol = :symbol
              and o.orderType = :orderType
              and o.status in :statuses
            order by o.orderPrice asc, o.createdAt asc
            """)
    List<StockOrder> findSellOrdersForMatching(
            @Param("symbol") String symbol,
            @Param("orderType") OrderType orderType,
            @Param("statuses") Collection<StockOrderStatus> statuses
    );
}
