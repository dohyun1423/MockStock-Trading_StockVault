// StockOrder 엔티티의 주문 생성, 체결, 취소, 가격 조건 판단 로직을 검증하는 테스트
package com.stock.mockstock.domain.order.entity;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockOrderTest {

    @Test
    @DisplayName("매수 주문을 생성하면 주문 금액만큼 예약 현금이 설정된다")
    void createBuyOrder() {
        // given: 매수 주문을 넣을 사용자와 종목을 준비한다.
        User user = createUser();
        Stock stock = createStock();

        // when: 70,000원에 3주 매수 주문을 생성한다.
        StockOrder stockOrder = StockOrder.create(
                user,
                stock,
                OrderType.BUY,
                70_000L,
                3,
                MarketSession.REGULAR
        );

        // then: 매수 주문 금액만큼 예약 현금이 설정되고 주문은 미체결 상태가 된다.
        assertThat(stockOrder.getOrderType()).isEqualTo(OrderType.BUY);
        assertThat(stockOrder.getOrderPrice()).isEqualTo(70_000L);
        assertThat(stockOrder.getQuantity()).isEqualTo(3);
        assertThat(stockOrder.getExecutedQuantity()).isZero();
        assertThat(stockOrder.getRemainingQuantity()).isEqualTo(3);
        assertThat(stockOrder.getReservedAmount()).isEqualTo(210_000L);
        assertThat(stockOrder.getReservedQuantity()).isZero();
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.PENDING);
    }

    @Test
    @DisplayName("매도 주문을 생성하면 주문 수량만큼 예약 수량이 설정된다")
    void createSellOrder() {
        // given: 매도 주문을 넣을 사용자와 종목을 준비한다.
        User user = createUser();
        Stock stock = createStock();

        // when: 72,000원에 5주 매도 주문을 생성한다.
        StockOrder stockOrder = StockOrder.create(
                user,
                stock,
                OrderType.SELL,
                72_000L,
                5,
                MarketSession.REGULAR
        );

        // then: 매도 주문 수량만큼 예약 수량이 설정되고 주문은 미체결 상태가 된다.
        assertThat(stockOrder.getOrderType()).isEqualTo(OrderType.SELL);
        assertThat(stockOrder.getOrderPrice()).isEqualTo(72_000L);
        assertThat(stockOrder.getQuantity()).isEqualTo(5);
        assertThat(stockOrder.getExecutedQuantity()).isZero();
        assertThat(stockOrder.getRemainingQuantity()).isEqualTo(5);
        assertThat(stockOrder.getReservedAmount()).isZero();
        assertThat(stockOrder.getReservedQuantity()).isEqualTo(5);
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.PENDING);
    }

    @Test
    @DisplayName("주문 일부가 체결되면 부분 체결 상태가 된다")
    void fillPartially() {
        // given: 10주 매수 주문을 생성한다.
        StockOrder stockOrder = StockOrder.create(
                createUser(),
                createStock(),
                OrderType.BUY,
                70_000L,
                10,
                MarketSession.REGULAR
        );

        // when: 주문 중 4주만 체결한다.
        stockOrder.fill(4);

        // then: 체결 수량과 남은 수량이 갱신되고 상태는 부분 체결이 된다.
        assertThat(stockOrder.getExecutedQuantity()).isEqualTo(4);
        assertThat(stockOrder.getRemainingQuantity()).isEqualTo(6);
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.PARTIALLY_FILLED);
        assertThat(stockOrder.getExecutedAt()).isNull();
    }

    @Test
    @DisplayName("주문 전체가 체결되면 체결 완료 상태가 된다")
    void fillCompletely() {
        // given: 10주 매수 주문을 생성한다.
        StockOrder stockOrder = StockOrder.create(
                createUser(),
                createStock(),
                OrderType.BUY,
                70_000L,
                10,
                MarketSession.REGULAR
        );

        // when: 주문 수량 전체를 체결한다.
        stockOrder.fill(10);

        // then: 남은 수량이 0이 되고 상태는 체결 완료가 된다.
        assertThat(stockOrder.getExecutedQuantity()).isEqualTo(10);
        assertThat(stockOrder.getRemainingQuantity()).isZero();
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.FILLED);
        assertThat(stockOrder.getExecutedAt()).isNotNull();
    }

    @Test
    @DisplayName("미체결 주문은 취소할 수 있다")
    void cancelPendingOrder() {
        // given: 아직 체결되지 않은 매수 주문을 생성한다.
        StockOrder stockOrder = StockOrder.create(
                createUser(),
                createStock(),
                OrderType.BUY,
                70_000L,
                10,
                MarketSession.REGULAR
        );

        // when: 미체결 주문을 취소한다.
        stockOrder.cancel();

        // then: 주문 상태가 취소로 바뀌고 취소 시간이 기록된다.
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.CANCELED);
        assertThat(stockOrder.getCanceledAt()).isNotNull();
    }

    @Test
    @DisplayName("매수 주문은 현재가가 주문가보다 낮거나 같으면 체결 가능하다")
    void buyOrderExecutableByPrice() {
        // given: 70,000원 매수 주문을 생성한다.
        StockOrder stockOrder = StockOrder.create(
                createUser(),
                createStock(),
                OrderType.BUY,
                70_000L,
                10,
                MarketSession.REGULAR
        );

        // then: 현재가가 주문가 이하일 때만 매수 체결 가능 상태로 판단한다.
        assertThat(stockOrder.isExecutableByPrice(69_000L)).isTrue();
        assertThat(stockOrder.isExecutableByPrice(70_000L)).isTrue();
        assertThat(stockOrder.isExecutableByPrice(71_000L)).isFalse();
    }

    @Test
    @DisplayName("매도 주문은 현재가가 주문가보다 높거나 같으면 체결 가능하다")
    void sellOrderExecutableByPrice() {
        // given: 70,000원 매도 주문을 생성한다.
        StockOrder stockOrder = StockOrder.create(
                createUser(),
                createStock(),
                OrderType.SELL,
                70_000L,
                10,
                MarketSession.REGULAR
        );

        // then: 현재가가 주문가 이상일 때만 매도 체결 가능 상태로 판단한다.
        assertThat(stockOrder.isExecutableByPrice(69_000L)).isFalse();
        assertThat(stockOrder.isExecutableByPrice(70_000L)).isTrue();
        assertThat(stockOrder.isExecutableByPrice(71_000L)).isTrue();
    }

    // 테스트마다 사용할 기본 사용자 객체를 생성한다.
    private User createUser() {
        return User.builder()
                .email("test@example.com")
                .password("password")
                .nickname("tester")
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