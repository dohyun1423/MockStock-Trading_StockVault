// OrderExecutionService가 예약 주문을 실제 체결 처리하고 보유 종목, 현금, 거래내역, 알림을 갱신하는지 검증하는 테스트
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.entity.Trade;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.repository.TradeRepository;
import com.stock.mockstock.domain.portfolio.entity.Holding;
import com.stock.mockstock.domain.portfolio.repository.HoldingRepository;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.realtime.StockRealtimeBroadcaster;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.enumtype.Role;
import com.stock.mockstock.global.audit.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceTest {

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private StockRealtimeBroadcaster stockRealtimeBroadcaster;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private OrderExecutionService orderExecutionService;

    @Test
    @DisplayName("예약 매수 주문이 체결되면 현금이 차감되고 보유 종목과 거래내역이 생성된다")
    void executeBuyOrder() {
        // given: 70,000원에 3주 매수 주문을 넣고 주문 금액을 예약한다.
        User user = createUser();
        Stock stock = createStock();
        StockOrder stockOrder = StockOrder.create(
                user,
                stock,
                OrderType.BUY,
                70_000L,
                3,
                MarketSession.REGULAR
        );
        user.reserveCash(stockOrder.getReservedAmount());

        when(holdingRepository.findByUserAndStockForUpdate(user, stock)).thenReturn(Optional.empty());

        // when: 69,000원에 2주만 부분 체결한다.
        Long executedAmount = orderExecutionService.executeStockOrder(stockOrder, 69_000L, 2);

        // then: 실제 체결 금액만큼 현금이 차감되고 남은 주문 금액만 예약 현금으로 유지된다.
        assertThat(executedAmount).isEqualTo(138_000L);
        assertThat(user.getCash()).isEqualTo(862_000L);
        assertThat(user.getReservedCash()).isEqualTo(70_000L);
        assertThat(stockOrder.getExecutedQuantity()).isEqualTo(2);
        assertThat(stockOrder.getRemainingQuantity()).isEqualTo(1);
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.PARTIALLY_FILLED);

        ArgumentCaptor<Holding> holdingCaptor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(holdingCaptor.capture());
        assertThat(holdingCaptor.getValue().getQuantity()).isEqualTo(2);
        assertThat(holdingCaptor.getValue().getAveragePrice()).isEqualTo(69_000L);

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getOrderType()).isEqualTo(OrderType.BUY);
        assertThat(tradeCaptor.getValue().getQuantity()).isEqualTo(2);
        assertThat(tradeCaptor.getValue().getPrice()).isEqualTo(69_000L);
        assertThat(tradeCaptor.getValue().getTotalAmount()).isEqualTo(138_000L);

        verify(stockRealtimeBroadcaster).broadcastOrderExecution(eq(user.getEmail()), any());
    }

    @Test
    @DisplayName("예약 매도 주문이 체결되면 보유 수량이 줄고 현금과 거래내역이 증가한다")
    void executeSellOrder() {
        // given: 5주 보유 중 3주를 매도 예약한 주문과 보유 종목을 준비한다.
        User user = createUser();
        Stock stock = createStock();
        Holding holding = Holding.builder()
                .user(user)
                .stock(stock)
                .quantity(5)
                .reservedQuantity(3)
                .averagePrice(70_000L)
                .build();
        StockOrder stockOrder = StockOrder.create(
                user,
                stock,
                OrderType.SELL,
                72_000L,
                3,
                MarketSession.REGULAR
        );

        when(holdingRepository.findByUserAndStockForUpdate(user, stock)).thenReturn(Optional.of(holding));

        // when: 72,000원에 3주가 전량 체결된다.
        Long executedAmount = orderExecutionService.executeStockOrder(stockOrder, 72_000L, 3);

        // then: 보유 수량과 예약 수량이 줄고 매도 금액이 현금에 반영된다.
        assertThat(executedAmount).isEqualTo(216_000L);
        assertThat(user.getCash()).isEqualTo(1_216_000L);
        assertThat(holding.getQuantity()).isEqualTo(2);
        assertThat(holding.getReservedQuantity()).isZero();
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.FILLED);

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getOrderType()).isEqualTo(OrderType.SELL);
        assertThat(tradeCaptor.getValue().getQuantity()).isEqualTo(3);
        assertThat(tradeCaptor.getValue().getPrice()).isEqualTo(72_000L);
        assertThat(tradeCaptor.getValue().getTotalAmount()).isEqualTo(216_000L);

        verify(stockRealtimeBroadcaster).broadcastOrderExecution(eq(user.getEmail()), any());
    }

    @Test
    @DisplayName("매도 체결 후 보유 수량이 0주가 되면 보유 종목을 삭제한다")
    void executeSellOrderDeletesEmptyHolding() {
        // given: 3주 전부가 매도 예약된 보유 종목을 준비한다.
        User user = createUser();
        Stock stock = createStock();
        Holding holding = Holding.builder()
                .user(user)
                .stock(stock)
                .quantity(3)
                .reservedQuantity(3)
                .averagePrice(70_000L)
                .build();
        StockOrder stockOrder = StockOrder.create(
                user,
                stock,
                OrderType.SELL,
                72_000L,
                3,
                MarketSession.REGULAR
        );

        when(holdingRepository.findByUserAndStockForUpdate(user, stock)).thenReturn(Optional.of(holding));

        // when: 예약 매도 수량 전체가 체결된다.
        orderExecutionService.executeStockOrder(stockOrder, 72_000L, 3);

        // then: 보유 수량이 0주가 되어 보유 종목 삭제가 호출된다.
        assertThat(holding.isEmpty()).isTrue();
        verify(holdingRepository).delete(holding);
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
