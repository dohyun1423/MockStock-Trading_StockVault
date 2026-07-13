package com.stock.mockstock.domain.user.entity;

import com.stock.mockstock.domain.user.enumtype.Role;
import com.stock.mockstock.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 동시에 같은 사용자의 현금이 변경될 때 충돌을 감지한다.
    @Builder.Default
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    private Role role;

    // 사용자의 전체 보유 현금이다.
    @Column(nullable = false)
    private Long cash;

    // 미체결 매수 주문에 묶여 있는 현금이다.
    @Builder.Default
    @Column(nullable = false)
    private Long reservedCash = 0L;

    // 매도 체결 또는 주문 취소 환급으로 현금을 증가시킨다.
    public void increaseCash(Long amount) {
        this.cash += amount;
    }

    // 사용자의 닉네임을 새 값으로 변경한다.
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    // 사용자의 암호화된 비밀번호를 새 값으로 변경한다.
    public void updatePassword(String password) {
        this.password = password;
    }

    // 전체 현금 중 미체결 주문에 묶이지 않은 주문 가능 현금을 계산한다.
    public Long getAvailableCash() {
        return cash - reservedCash;
    }

    // 매수 주문 접수 시 주문 금액을 예약 현금으로 묶는다.
    public void reserveCash(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("예약 금액은 0보다 커야 합니다.");
        }

        if (getAvailableCash() < amount) {
            throw new IllegalArgumentException("주문 가능 현금이 부족합니다.");
        }

        this.reservedCash += amount;
    }

    // 주문 취소 또는 체결 실패 시 남은 예약 현금을 해제한다.
    public void releaseReservedCash(Long amount) {
        if (amount == null || amount <= 0) {
            return;
        }

        this.reservedCash = Math.max(0L, this.reservedCash - amount);
    }

    // 예약된 매수 주문이 체결되면 예약 현금을 풀고 실제 현금을 차감한다.
    public void executeReservedBuy(Long reservedAmount, Long executedAmount) {
        if (reservedAmount == null || reservedAmount <= 0) {
            throw new IllegalArgumentException("예약 금액이 올바르지 않습니다.");
        }

        if (executedAmount == null || executedAmount <= 0) {
            throw new IllegalArgumentException("체결 금액이 올바르지 않습니다.");
        }

        if (reservedCash < reservedAmount) {
            throw new IllegalArgumentException("예약 현금이 부족합니다.");
        }

        if (cash < executedAmount) {
            throw new IllegalArgumentException("보유 현금이 부족합니다.");
        }

        this.reservedCash -= reservedAmount;
        this.cash -= executedAmount;
    }
}
