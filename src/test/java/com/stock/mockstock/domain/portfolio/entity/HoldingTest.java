// Holding 엔티티의 보유 수량, 예약 수량, 평균 매수가 계산이 정상 동작하는지 검증하는 테스트
package com.stock.mockstock.domain.portfolio.entity;

import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.enumtype.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingTest {

    @Test
    @DisplayName("예약 수량이 없으면 매도 가능 수량은 전체 보유 수량과 같다")
    void getAvailableQuantityWithoutReservedQuantity() {
        // given: 10주를 보유하고 예약 수량이 없는 보유 종목을 생성한다.
        Holding holding = createHolding(10, 0, 70_000L);

        // when: 매도 가능 수량을 조회한다.
        Integer availableQuantity = holding.getAvailableQuantity();

        // then: 예약 수량이 없으므로 전체 보유 수량이 매도 가능 수량이다.
        assertThat(availableQuantity).isEqualTo(10);
    }

    @Test
    @DisplayName("예약 수량이 있으면 매도 가능 수량은 전체 수량에서 예약 수량을 뺀 값이다")
    void getAvailableQuantityWithReservedQuantity() {
        // given: 10주 중 4주가 매도 주문으로 예약된 보유 종목을 생성한다.
        Holding holding = createHolding(10, 4, 70_000L);

        // when: 매도 가능 수량을 조회한다.
        Integer availableQuantity = holding.getAvailableQuantity();

        // then: 전체 보유 수량에서 예약 수량을 뺀 값이 매도 가능 수량이다.
        assertThat(availableQuantity).isEqualTo(6);
    }

    @Test
    @DisplayName("추가 매수하면 보유 수량과 평균 매수가가 갱신된다")
    void buy() {
        // given: 70,000원에 10주를 보유 중인 종목을 생성한다.
        Holding holding = createHolding(10, 0, 70_000L);

        // when: 80,000원에 10주를 추가 매수한다.
        holding.buy(10, 80_000L);

        // then: 보유 수량은 20주가 되고 평균 매수가는 75,000원이 된다.
        assertThat(holding.getQuantity()).isEqualTo(20);
        assertThat(holding.getAveragePrice()).isEqualTo(75_000L);
    }

    @Test
    @DisplayName("매도 주문 수량을 예약 수량으로 묶을 수 있다")
    void reserveQuantity() {
        // given: 10주를 보유하고 예약 수량이 없는 보유 종목을 생성한다.
        Holding holding = createHolding(10, 0, 70_000L);

        // when: 3주를 매도 주문 예약 수량으로 묶는다.
        holding.reserveQuantity(3);

        // then: 예약 수량이 증가하고 매도 가능 수량은 감소한다.
        assertThat(holding.getReservedQuantity()).isEqualTo(3);
        assertThat(holding.getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("매도 가능 수량보다 큰 수량은 예약할 수 없다")
    void reserveQuantityOverAvailableQuantity() {
        // given: 10주 중 4주가 이미 예약된 보유 종목을 생성한다.
        Holding holding = createHolding(10, 4, 70_000L);

        // then: 매도 가능 수량인 6주보다 큰 수량을 예약하면 예외가 발생한다.
        assertThatThrownBy(() -> holding.reserveQuantity(7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예약 수량을 해제하면 예약 수량이 감소한다")
    void releaseReservedQuantity() {
        // given: 10주 중 4주가 예약된 보유 종목을 생성한다.
        Holding holding = createHolding(10, 4, 70_000L);

        // when: 예약 수량 중 2주를 해제한다.
        holding.releaseReservedQuantity(2);

        // then: 예약 수량이 2주로 감소하고 매도 가능 수량은 증가한다.
        assertThat(holding.getReservedQuantity()).isEqualTo(2);
        assertThat(holding.getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("예약 수량보다 큰 수량을 해제해도 예약 수량은 0주 아래로 내려가지 않는다")
    void releaseReservedQuantityMoreThanReservedQuantity() {
        // given: 10주 중 4주가 예약된 보유 종목을 생성한다.
        Holding holding = createHolding(10, 4, 70_000L);

        // when: 예약 수량보다 큰 10주를 해제한다.
        holding.releaseReservedQuantity(10);

        // then: 예약 수량은 최소값인 0주로 보정된다.
        assertThat(holding.getReservedQuantity()).isZero();
        assertThat(holding.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("예약 매도 주문이 체결되면 예약 수량과 보유 수량이 함께 감소한다")
    void executeReservedSell() {
        // given: 10주 중 4주가 매도 주문으로 예약된 보유 종목을 생성한다.
        Holding holding = createHolding(10, 4, 70_000L);

        // when: 예약 매도 수량 중 3주를 체결 처리한다.
        holding.executeReservedSell(3);

        // then: 예약 수량과 보유 수량이 함께 감소한다.
        assertThat(holding.getQuantity()).isEqualTo(7);
        assertThat(holding.getReservedQuantity()).isEqualTo(1);
        assertThat(holding.getAvailableQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("보유 수량이 0주이면 빈 보유 종목으로 판단한다")
    void isEmpty() {
        // given: 보유 수량이 0주인 보유 종목을 생성한다.
        Holding holding = createHolding(0, 0, 70_000L);

        // then: 빈 보유 종목으로 판단한다.
        assertThat(holding.isEmpty()).isTrue();
    }

    // 테스트마다 사용할 기본 보유 종목 객체를 생성한다.
    private Holding createHolding(Integer quantity, Integer reservedQuantity, Long averagePrice) {
        return Holding.builder()
                .user(createUser())
                .stock(createStock())
                .quantity(quantity)
                .reservedQuantity(reservedQuantity)
                .averagePrice(averagePrice)
                .build();
    }

    // 테스트마다 사용할 기본 사용자 객체를 생성한다.
    private User createUser() {
        return User.builder()
                .email("test@example.com")
                .password("password")
                .nickname("tester")
                .role(Role.USER)
                .cash(1_000_000L)
                .reservedCash(0L)
                .build();
    }

    // 테스트마다 사용할 기본 삼성전자 종목 객체를 생성한다.
    private Stock createStock() {
        return Stock.builder()
                .symbol("005930")
                .name("삼성전자")
                .market("KOSPI")
                .currentPrice(70_000L)
                .build();
    }
}
