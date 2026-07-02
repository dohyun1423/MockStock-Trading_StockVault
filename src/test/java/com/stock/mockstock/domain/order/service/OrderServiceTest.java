// OrderService가 주문 접수, 예약 처리, 즉시 체결, 주문 취소, 주문 수정을 정상 처리하는지 검증하는 테스트
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.dto.OrderRequest;
import com.stock.mockstock.domain.order.dto.OrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderUpdateRequest;
import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.repository.StockOrderRepository;
import com.stock.mockstock.domain.portfolio.entity.Holding;
import com.stock.mockstock.domain.portfolio.repository.HoldingRepository;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.repository.StockRepository;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.enumtype.Role;
import com.stock.mockstock.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockOrderRepository stockOrderRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private StockQuoteService stockQuoteService;

    @Mock
    private MarketSessionService marketSessionService;

    @Mock
    private OrderExecutionService orderExecutionService;

    @Mock
    private OpenOrderRealtimeSubscriptionService openOrderRealtimeSubscriptionService;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("매수 주문이 즉시 체결 조건에 맞지 않으면 예약 현금을 묶고 미체결 주문으로 저장한다")
    void buyPendingOrder() {
        // given: 현재가보다 낮은 가격으로 매수 주문을 요청한다.
        User user = createUser(1L);
        Stock stock = createStock();
        OrderRequest request = createOrderRequest("005930", 3, 70_000L);

        mockDefaultOrderDependencies(user, stock, MarketSession.REGULAR, 72_000L);

        // when: 매수 주문을 접수한다.
        OrderResponse response = orderService.buy(user.getEmail(), request);

        // then: 주문 금액이 예약 현금으로 묶이고 미체결 주문으로 저장된다.
        assertThat(user.getReservedCash()).isEqualTo(210_000L);
        assertThat(user.getAvailableCash()).isEqualTo(790_000L);
        assertThat(response.getOrderStatus()).isEqualTo("PENDING");

        ArgumentCaptor<StockOrder> stockOrderCaptor = ArgumentCaptor.forClass(StockOrder.class);
        verify(stockOrderRepository).save(stockOrderCaptor.capture());
        assertThat(stockOrderCaptor.getValue().getOrderType()).isEqualTo(OrderType.BUY);
        assertThat(stockOrderCaptor.getValue().getStatus()).isEqualTo(StockOrderStatus.PENDING);
        verify(openOrderRealtimeSubscriptionService).subscribeTradeAfterCommit("005930");
    }

    @Test
    @DisplayName("매수 주문이 즉시 체결 조건에 맞으면 체결 서비스로 넘기고 실시간 구독은 하지 않는다")
    void buyImmediateOrder() {
        // given: 현재가보다 높은 가격으로 매수 주문을 요청한다.
        User user = createUser(1L);
        Stock stock = createStock();
        OrderRequest request = createOrderRequest("005930", 3, 70_000L);

        mockDefaultOrderDependencies(user, stock, MarketSession.REGULAR, 69_000L);
        when(orderExecutionService.executeStockOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(69_000L), org.mockito.ArgumentMatchers.eq(3)))
                .thenReturn(207_000L);

        // when: 매수 주문을 접수한다.
        OrderResponse response = orderService.buy(user.getEmail(), request);

        // then: 즉시 체결 응답을 반환하고 미체결 실시간 구독은 등록하지 않는다.
        assertThat(response.getOrderStatus()).isEqualTo("EXECUTED");
        verify(orderExecutionService).executeStockOrder(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(69_000L), org.mockito.ArgumentMatchers.eq(3));
        verify(openOrderRealtimeSubscriptionService, never()).subscribeTradeAfterCommit("005930");
    }

    @Test
    @DisplayName("매도 주문이 즉시 체결 조건에 맞지 않으면 보유 수량을 예약하고 미체결 주문으로 저장한다")
    void sellPendingOrder() {
        // given: 현재가보다 높은 가격으로 매도 주문을 요청한다.
        User user = createUser(1L);
        Stock stock = createStock();
        Holding holding = createHolding(user, stock, 10, 0);
        OrderRequest request = createOrderRequest("005930", 4, 72_000L);

        mockDefaultOrderDependencies(user, stock, MarketSession.REGULAR, 70_000L);
        when(holdingRepository.findByUserAndStock(user, stock)).thenReturn(Optional.of(holding));

        // when: 매도 주문을 접수한다.
        OrderResponse response = orderService.sell(user.getEmail(), request);

        // then: 매도 수량이 예약되고 미체결 주문으로 저장된다.
        assertThat(holding.getReservedQuantity()).isEqualTo(4);
        assertThat(holding.getAvailableQuantity()).isEqualTo(6);
        assertThat(response.getOrderStatus()).isEqualTo("PENDING");

        ArgumentCaptor<StockOrder> stockOrderCaptor = ArgumentCaptor.forClass(StockOrder.class);
        verify(stockOrderRepository).save(stockOrderCaptor.capture());
        assertThat(stockOrderCaptor.getValue().getOrderType()).isEqualTo(OrderType.SELL);
        verify(openOrderRealtimeSubscriptionService).subscribeTradeAfterCommit("005930");
    }

    @Test
    @DisplayName("미체결 매수 주문을 취소하면 예약 현금이 해제되고 주문 상태가 취소된다")
    void cancelBuyOrder() {
        // given: 예약 현금이 묶인 미체결 매수 주문을 준비한다.
        User user = createUser(1L);
        Stock stock = createStock();
        StockOrder stockOrder = createStockOrder(user, stock, OrderType.BUY, 70_000L, 3);
        user.reserveCash(stockOrder.getReservedAmount());

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(stockOrderRepository.findById(10L)).thenReturn(Optional.of(stockOrder));

        // when: 주문을 취소한다.
        StockOrderResponse response = orderService.cancelOrder(user.getEmail(), 10L);

        // then: 예약 현금이 해제되고 주문 상태가 취소된다.
        assertThat(user.getReservedCash()).isZero();
        assertThat(stockOrder.getStatus()).isEqualTo(StockOrderStatus.CANCELED);
        assertThat(response.getStatus()).isEqualTo(StockOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("미체결 매수 주문을 더 높은 금액으로 수정하면 추가 예약 현금을 묶는다")
    void updateBuyOrder() {
        // given: 70,000원 3주 매수 주문과 수정 요청을 준비한다.
        User user = createUser(1L);
        Stock stock = createStock();
        StockOrder stockOrder = createStockOrder(user, stock, OrderType.BUY, 70_000L, 3);
        StockOrderUpdateRequest request = createUpdateRequest(80_000L, 3);
        user.reserveCash(stockOrder.getReservedAmount());

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(stockOrderRepository.findById(10L)).thenReturn(Optional.of(stockOrder));

        // when: 주문가를 80,000원으로 수정한다.
        StockOrderResponse response = orderService.updateOrder(user.getEmail(), 10L, request);

        // then: 예약 현금이 차액만큼 증가하고 주문 가격이 변경된다.
        assertThat(user.getReservedCash()).isEqualTo(240_000L);
        assertThat(stockOrder.getOrderPrice()).isEqualTo(80_000L);
        assertThat(stockOrder.getRemainingQuantity()).isEqualTo(3);
        assertThat(response.getOrderPrice()).isEqualTo(80_000L);
        verify(openOrderRealtimeSubscriptionService).subscribeTradeAfterCommit("005930");
    }

    @Test
    @DisplayName("미체결 매도 주문 수량을 줄이면 예약 수량이 해제된다")
    void updateSellOrder() {
        // given: 5주 매도 주문과 보유 종목, 2주로 줄이는 수정 요청을 준비한다.
        User user = createUser(1L);
        Stock stock = createStock();
        Holding holding = createHolding(user, stock, 10, 5);
        StockOrder stockOrder = createStockOrder(user, stock, OrderType.SELL, 70_000L, 5);
        StockOrderUpdateRequest request = createUpdateRequest(71_000L, 2);

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(stockOrderRepository.findById(10L)).thenReturn(Optional.of(stockOrder));
        when(holdingRepository.findByUserAndStock(user, stock)).thenReturn(Optional.of(holding));

        // when: 매도 주문의 남은 수량을 2주로 수정한다.
        StockOrderResponse response = orderService.updateOrder(user.getEmail(), 10L, request);

        // then: 예약 수량이 2주로 줄고 주문 가격과 남은 수량이 변경된다.
        assertThat(holding.getReservedQuantity()).isEqualTo(2);
        assertThat(stockOrder.getOrderPrice()).isEqualTo(71_000L);
        assertThat(stockOrder.getRemainingQuantity()).isEqualTo(2);
        assertThat(response.getRemainingQuantity()).isEqualTo(2);
        verify(openOrderRealtimeSubscriptionService).subscribeTradeAfterCommit("005930");
    }

    // 주문 생성 테스트에서 공통으로 필요한 사용자, 종목, 세션, 현재가 조회를 준비한다.
    private void mockDefaultOrderDependencies(
            User user,
            Stock stock,
            MarketSession session,
            Long currentPrice
    ) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(stockRepository.findBySymbol("005930")).thenReturn(Optional.of(stock));
        when(marketSessionService.getCurrentSession()).thenReturn(session);
        when(marketSessionService.isOrderAvailable(session)).thenReturn(true);
        when(marketSessionService.isImmediateExecution(session)).thenReturn(session == MarketSession.REGULAR);
        when(stockQuoteService.getQuote("005930")).thenReturn(createQuote(currentPrice));
    }

    // 테스트용 주문 요청 DTO를 생성한다.
    private OrderRequest createOrderRequest(String symbol, Integer quantity, Long orderPrice) {
        OrderRequest request = new OrderRequest();
        ReflectionTestUtils.setField(request, "symbol", symbol);
        ReflectionTestUtils.setField(request, "quantity", quantity);
        ReflectionTestUtils.setField(request, "orderPrice", orderPrice);

        return request;
    }

    // 테스트용 주문 수정 요청 DTO를 생성한다.
    private StockOrderUpdateRequest createUpdateRequest(Long orderPrice, Integer remainingQuantity) {
        StockOrderUpdateRequest request = new StockOrderUpdateRequest();
        ReflectionTestUtils.setField(request, "orderPrice", orderPrice);
        ReflectionTestUtils.setField(request, "remainingQuantity", remainingQuantity);

        return request;
    }

    // 테스트용 미체결 주문을 생성하고 소유자 검증을 위해 id를 부여한다.
    private StockOrder createStockOrder(
            User user,
            Stock stock,
            OrderType orderType,
            Long orderPrice,
            Integer quantity
    ) {
        StockOrder stockOrder = StockOrder.create(
                user,
                stock,
                orderType,
                orderPrice,
                quantity,
                MarketSession.REGULAR
        );
        ReflectionTestUtils.setField(stockOrder, "id", 10L);

        return stockOrder;
    }

    // 테스트용 보유 종목을 생성한다.
    private Holding createHolding(User user, Stock stock, Integer quantity, Integer reservedQuantity) {
        return Holding.builder()
                .user(user)
                .stock(stock)
                .quantity(quantity)
                .reservedQuantity(reservedQuantity)
                .averagePrice(70_000L)
                .build();
    }

    // 테스트용 현재가 응답 DTO를 생성한다.
    private StockQuoteResponse createQuote(Long currentPrice) {
        return new StockQuoteResponse(
                "005930",
                "삼성전자",
                currentPrice,
                0L,
                BigDecimal.ZERO,
                1_000_000L,
                70_000_000_000L,
                currentPrice,
                currentPrice,
                currentPrice,
                currentPrice
        );
    }

    // 테스트마다 사용할 기본 사용자 객체를 생성하고 id를 부여한다.
    private User createUser(Long id) {
        User user = User.builder()
                .email("test@example.com")
                .password("password")
                .nickname("tester")
                .role(Role.USER)
                .cash(1_000_000L)
                .reservedCash(0L)
                .build();
        ReflectionTestUtils.setField(user, "id", id);

        return user;
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
