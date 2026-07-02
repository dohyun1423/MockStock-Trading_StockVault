package com.stock.mockstock.domain.portfolio.entity;

import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "holdings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_holding_user_stock",
                        columnNames = {"user_id", "stock_id"}
                )
        }
)
public class Holding extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 보유 주식의 사용자다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 사용자가 보유한 종목이다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    // 실제 보유 수량이다.
    @Column(nullable = false)
    private Integer quantity;

    // 미체결 매도 주문에 묶여 있는 수량이다.
    @Builder.Default
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    // 평균 매수 가격이다.
    @Column(nullable = false)
    private Long averagePrice;

    // 매수 체결 시 보유 수량과 평균 매수 가격을 갱신한다.
    public void buy(Integer buyQuantity, Long buyPrice) {
        long currentTotalAmount = averagePrice * quantity;
        long buyTotalAmount = buyPrice * buyQuantity;

        this.quantity += buyQuantity;
        this.averagePrice = (currentTotalAmount + buyTotalAmount) / this.quantity;
    }

    // 보유 수량이 0주인지 확인한다.
    public boolean isEmpty() {
        return quantity == 0;
    }

    // 전체 보유 수량 중 미체결 매도 주문에 묶이지 않은 수량을 계산한다.
    public Integer getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    // 매도 주문 접수 시 주문 수량을 예약 수량으로 묶는다.
    public void reserveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("예약 수량은 1주 이상이어야 합니다.");
        }

        if (getAvailableQuantity() < quantity) {
            throw new IllegalArgumentException("주문 가능 수량이 부족합니다.");
        }

        this.reservedQuantity += quantity;
    }

    // 주문 취소 또는 체결 실패 시 남은 예약 수량을 해제한다.
    public void releaseReservedQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return;
        }

        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    // 예약된 매도 주문이 체결되면 예약 수량과 실제 보유 수량을 함께 차감한다.
    public void executeReservedSell(Integer sellQuantity) {
        if (sellQuantity == null || sellQuantity <= 0) {
            throw new IllegalArgumentException("매도 수량은 1주 이상이어야 합니다.");
        }

        if (reservedQuantity < sellQuantity) {
            throw new IllegalArgumentException("예약 매도 수량이 부족합니다.");
        }

        if (quantity < sellQuantity) {
            throw new IllegalArgumentException("보유 수량이 부족합니다.");
        }

        this.reservedQuantity -= sellQuantity;
        this.quantity -= sellQuantity;
    }
}
