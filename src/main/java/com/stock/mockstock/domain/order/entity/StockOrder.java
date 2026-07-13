// 미체결, 부분체결, 체결, 취소 상태를 관리하는 예약 주문 엔티티다.
package com.stock.mockstock.domain.order.entity;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "stock_orders")
public class StockOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 자동체결, 주문수정, 주문취소가 동시에 같은 주문을 바꾸는 상황을 감지한다.
    @Builder.Default
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    // 주문을 넣은 사용자다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 주문 대상 종목이다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    // 매수 또는 매도 구분이다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    // 사용자가 지정한 주문 가격이다.
    @Column(nullable = false)
    private Long orderPrice;

    // 최초 주문 수량이다.
    @Column(nullable = false)
    private Integer quantity;

    // 지금까지 체결된 수량이다.
    @Column(nullable = false)
    private Integer executedQuantity;

    // 아직 체결되지 않은 수량이다.
    @Column(nullable = false)
    private Integer remainingQuantity;

    // 매수 주문에서 묶어둔 현금이다.
    @Column(nullable = false)
    private Long reservedAmount;

    // 매도 주문에서 묶어둔 주식 수량이다.
    @Column(nullable = false)
    private Integer reservedQuantity;

    // 주문 접수 당시의 거래 세션이다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketSession marketSession;

    // 주문의 현재 처리 상태다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockOrderStatus status;

    private LocalDateTime executedAt;

    private LocalDateTime canceledAt;

    @Column(length = 500)
    private String failureReason;

    // 신규 주문을 생성한다.
    public static StockOrder create(
            User user,
            Stock stock,
            OrderType orderType,
            Long orderPrice,
            Integer quantity,
            MarketSession marketSession
    ) {
        validateCreateRequest(orderPrice, quantity);

        if (orderType == OrderType.BUY) {
            Long reservedAmount = Math.multiplyExact(orderPrice, quantity.longValue());

            return StockOrder.builder()
                    .user(user)
                    .stock(stock)
                    .orderType(orderType)
                    .orderPrice(orderPrice)
                    .quantity(quantity)
                    .executedQuantity(0)
                    .remainingQuantity(quantity)
                    .reservedAmount(reservedAmount)
                    .reservedQuantity(0)
                    .marketSession(marketSession)
                    .status(StockOrderStatus.PENDING)
                    .build();
        }

        return StockOrder.builder()
                .user(user)
                .stock(stock)
                .orderType(orderType)
                .orderPrice(orderPrice)
                .quantity(quantity)
                .executedQuantity(0)
                .remainingQuantity(quantity)
                .reservedAmount(0L)
                .reservedQuantity(quantity)
                .marketSession(marketSession)
                .status(StockOrderStatus.PENDING)
                .build();
    }

    // 주문 생성에 필요한 가격과 수량을 검증한다.
    private static void validateCreateRequest(Long orderPrice, Integer quantity) {
        if (orderPrice == null || orderPrice <= 0) {
            throw new IllegalArgumentException("주문 가격은 0보다 커야 합니다.");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1주 이상이어야 합니다.");
        }
    }

    // 주문이 아직 취소 가능한 상태인지 확인한다.
    public boolean isCancelable() {
        return status == StockOrderStatus.PENDING
                || status == StockOrderStatus.PARTIALLY_FILLED;
    }

    // 체결 수량을 반영하고 남은 수량에 따라 주문 상태를 갱신한다.
    public void fill(Integer executionQuantity) {
        if (executionQuantity == null || executionQuantity <= 0) {
            throw new IllegalArgumentException("체결 수량은 1주 이상이어야 합니다.");
        }

        if (executionQuantity > remainingQuantity) {
            throw new IllegalArgumentException("체결 수량이 남은 주문 수량보다 많습니다.");
        }

        this.executedQuantity += executionQuantity;
        this.remainingQuantity -= executionQuantity;

        if (this.remainingQuantity == 0) {
            this.status = StockOrderStatus.FILLED;
            this.executedAt = LocalDateTime.now();
            return;
        }

        this.status = StockOrderStatus.PARTIALLY_FILLED;
    }

    // 미체결 또는 부분체결 주문을 취소 상태로 바꾼다.
    public void cancel() {
        if (!isCancelable()) {
            throw new IllegalArgumentException("취소할 수 없는 주문입니다.");
        }

        this.status = StockOrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    // 미체결 또는 부분체결 주문의 주문가와 남은 수량을 수정한다.
    public void updateOpenOrder(Long orderPrice, Integer remainingQuantity) {
        if (!isCancelable()) {
            throw new IllegalArgumentException("수정할 수 없는 주문입니다.");
        }

        validateCreateRequest(orderPrice, remainingQuantity);

        this.orderPrice = orderPrice;
        this.remainingQuantity = remainingQuantity;
        this.quantity = this.executedQuantity + remainingQuantity;

        if (this.orderType == OrderType.BUY) {
            this.reservedAmount = Math.multiplyExact(orderPrice, remainingQuantity.longValue());
            this.reservedQuantity = 0;
            return;
        }

        this.reservedAmount = 0L;
        this.reservedQuantity = remainingQuantity;
    }

    // 체결 처리 중 실패한 주문으로 표시한다.
    public void markFailed(String reason) {
        this.status = StockOrderStatus.FAILED;
        this.failureReason = reason;
    }

    // 남은 매수 주문에 아직 묶여 있는 예약 현금을 계산한다.
    public Long getRemainingReservedAmount() {
        if (orderType != OrderType.BUY) {
            return 0L;
        }

        return Math.multiplyExact(orderPrice, remainingQuantity.longValue());
    }

    // 남은 매도 주문에 아직 묶여 있는 예약 수량을 계산한다.
    public Integer getRemainingReservedQuantity() {
        if (orderType != OrderType.SELL) {
            return 0;
        }

        return remainingQuantity;
    }

    // 현재가 기준으로 주문이 체결 가능한지 확인한다.
    public boolean isExecutableByPrice(Long currentPrice) {
        if (currentPrice == null || currentPrice <= 0) {
            return false;
        }

        if (orderType == OrderType.BUY) {
            return currentPrice <= orderPrice;
        }

        return currentPrice >= orderPrice;
    }
}
