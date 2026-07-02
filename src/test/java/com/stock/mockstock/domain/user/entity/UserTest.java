// User 엔티티의 현금, 예약 현금, 주문 가능 금액 계산이 정상 동작하는지 검증하는 테스트
package com.stock.mockstock.domain.user.entity;

import com.stock.mockstock.domain.user.enumtype.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("예약 현금이 없으면 사용 가능 현금은 전체 현금과 같다")
    void getAvailableCashWithoutReservedCash() {
        // given: 현금 1,000,000원을 가진 사용자를 생성한다.
        User user = createUser(1_000_000L, 0L);

        // when: 주문 가능 현금을 조회한다.
        Long availableCash = user.getAvailableCash();

        // then: 예약 현금이 없으므로 전체 현금이 주문 가능 현금이다.
        assertThat(availableCash).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("예약 현금이 있으면 사용 가능 현금은 전체 현금에서 예약 현금을 뺀 값이다")
    void getAvailableCashWithReservedCash() {
        // given: 현금 1,000,000원 중 300,000원이 예약된 사용자를 생성한다.
        User user = createUser(1_000_000L, 300_000L);

        // when: 주문 가능 현금을 조회한다.
        Long availableCash = user.getAvailableCash();

        // then: 전체 현금에서 예약 현금을 뺀 금액이 주문 가능 현금이다.
        assertThat(availableCash).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("매수 주문 금액을 예약 현금으로 묶을 수 있다")
    void reserveCash() {
        // given: 현금 1,000,000원을 가진 사용자를 생성한다.
        User user = createUser(1_000_000L, 0L);

        // when: 300,000원을 예약 현금으로 묶는다.
        user.reserveCash(300_000L);

        // then: 예약 현금이 증가하고 주문 가능 현금은 감소한다.
        assertThat(user.getReservedCash()).isEqualTo(300_000L);
        assertThat(user.getAvailableCash()).isEqualTo(700_000L);
    }

    @Test
    @DisplayName("주문 가능 현금보다 큰 금액은 예약할 수 없다")
    void reserveCashOverAvailableCash() {
        // given: 주문 가능 현금이 1,000,000원인 사용자를 생성한다.
        User user = createUser(1_000_000L, 0L);

        // then: 주문 가능 현금보다 큰 금액을 예약하면 예외가 발생한다.
        assertThatThrownBy(() -> user.reserveCash(1_000_001L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예약 현금을 해제하면 예약 현금이 감소한다")
    void releaseReservedCash() {
        // given: 300,000원이 예약된 사용자를 생성한다.
        User user = createUser(1_000_000L, 300_000L);

        // when: 예약 현금 중 100,000원을 해제한다.
        user.releaseReservedCash(100_000L);

        // then: 예약 현금이 200,000원으로 감소한다.
        assertThat(user.getReservedCash()).isEqualTo(200_000L);
        assertThat(user.getAvailableCash()).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("예약 현금보다 큰 금액을 해제해도 예약 현금은 0원 아래로 내려가지 않는다")
    void releaseReservedCashMoreThanReservedCash() {
        // given: 300,000원이 예약된 사용자를 생성한다.
        User user = createUser(1_000_000L, 300_000L);

        // when: 예약 현금보다 큰 금액을 해제한다.
        user.releaseReservedCash(500_000L);

        // then: 예약 현금은 최소값인 0원으로 보정된다.
        assertThat(user.getReservedCash()).isZero();
        assertThat(user.getAvailableCash()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("예약 매수 주문이 체결되면 예약 현금과 전체 현금이 함께 감소한다")
    void executeReservedBuy() {
        // given: 300,000원이 예약된 사용자를 생성한다.
        User user = createUser(1_000_000L, 300_000L);

        // when: 예약 금액 300,000원 중 실제 체결 금액 280,000원을 체결 처리한다.
        user.executeReservedBuy(300_000L, 280_000L);

        // then: 예약 현금은 해제되고 실제 체결 금액만큼 전체 현금이 감소한다.
        assertThat(user.getReservedCash()).isZero();
        assertThat(user.getCash()).isEqualTo(720_000L);
        assertThat(user.getAvailableCash()).isEqualTo(720_000L);
    }

    @Test
    @DisplayName("매도 체결 금액은 전체 현금에 더해진다")
    void increaseCash() {
        // given: 현금 1,000,000원을 가진 사용자를 생성한다.
        User user = createUser(1_000_000L, 0L);

        // when: 매도 체결 금액 200,000원을 현금에 더한다.
        user.increaseCash(200_000L);

        // then: 전체 현금이 매도 체결 금액만큼 증가한다.
        assertThat(user.getCash()).isEqualTo(1_200_000L);
        assertThat(user.getAvailableCash()).isEqualTo(1_200_000L);
    }

    // 테스트마다 사용할 기본 사용자 객체를 생성한다.
    private User createUser(Long cash, Long reservedCash) {
        return User.builder()
                .email("test@example.com")
                .password("password")
                .nickname("tester")
                .role(Role.USER)
                .cash(cash)
                .reservedCash(reservedCash)
                .build();
    }
}