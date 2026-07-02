// OrderMatchingService가 실시간 체결가, 실시간 호가, 현재가 기준으로 미체결 주문을 자동 매칭하는지 검증하는 테스트
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.repository.StockOrderRepository;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeOrderbookLevel;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeOrderbookMessage;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeTradeMessage;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.enumtype.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMatchingServiceTest {

    @Mock
    private StockOrderRepository stockOrderRepository;

    @Mock
    private OrderExecutionService orderExecutionService;

    @Mock
    private StockQuoteService stockQuoteService;

    @InjectMocks
    private OrderMatchingService orderMatchingService;

    @Test
    @DisplayName("실시간 체결가가 매수 주문가 이하이면 남은 수량 전체를 체결 요청한다")
    void matchBuyOrderByRealtimeTrade() {
        // given: 70,000원 이하에서 체결 가능한 매수 주문을 준비한다.
        StockOrder stockOrder = createStockOrder(OrderType.BUY, 70_000L, 3);
        KisRealtimeTradeMessage tradeMessage = createTradeMessage("005930", 69_500L);

        when(stockOrderRepository.findAllByStockSymbolAndStatusInOrderByCreatedAtAsc(
                "005930",
                List.of(StockOrderStatus.PENDING, StockOrderStatus.PARTIALLY_FILLED)
        )).thenReturn(List.of(stockOrder));
        when(orderExecutionService.executeStockOrder(stockOrder, 69_500L, 3)).thenReturn(208_500L);

        // when: 실시간 체결가 매칭을 수행한다.
        orderMatchingService.matchByRealtimeTrade(tradeMessage);

        // then: 주문 조건에 맞는 가격과 남은 수량으로 체결 서비스가 호출된다.
        verify(orderExecutionService).executeStockOrder(stockOrder, 69_500L, 3);
    }

    @Test
    @DisplayName("실시간 체결가가 주문 조건에 맞지 않으면 체결하지 않는다")
    void doesNotMatchWhenRealtimeTradePriceIsNotExecutable() {
        // given: 70,000원 매수 주문보다 높은 실시간 체결가를 준비한다.
        StockOrder stockOrder = createStockOrder(OrderType.BUY, 70_000L, 3);
        KisRealtimeTradeMessage tradeMessage = createTradeMessage("005930", 71_000L);

        when(stockOrderRepository.findAllByStockSymbolAndStatusInOrderByCreatedAtAsc(
                "005930",
                List.of(StockOrderStatus.PENDING, StockOrderStatus.PARTIALLY_FILLED)
        )).thenReturn(List.of(stockOrder));

        // when: 실시간 체결가 매칭을 수행한다.
        orderMatchingService.matchByRealtimeTrade(tradeMessage);

        // then: 주문 조건이 맞지 않으므로 체결 서비스가 호출되지 않는다.
        verify(orderExecutionService, never()).executeStockOrder(stockOrder, 71_000L, 3);
    }

    @Test
    @DisplayName("매수 주문은 매도 호가 잔량 기준으로 체결 가능한 수량만 체결 요청한다")
    void matchBuyOrderByOrderbookAskLiquidity() {
        // given: 매도 1호가 잔량이 2주인 호가와 5주 매수 주문을 준비한다.
        StockOrder stockOrder = createStockOrder(OrderType.BUY, 70_000L, 5);
        KisRealtimeOrderbookMessage orderbookMessage = createOrderbookMessage(69_900L, 2L, 69_800L, 10L);

        when(stockOrderRepository.findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceDescCreatedAtAsc(
                "005930",
                OrderType.BUY,
                List.of(StockOrderStatus.PENDING, StockOrderStatus.PARTIALLY_FILLED)
        )).thenReturn(List.of(stockOrder));
        when(stockOrderRepository.findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceAscCreatedAtAsc(
                "005930",
                OrderType.SELL,
                List.of(StockOrderStatus.PENDING, StockOrderStatus.PARTIALLY_FILLED)
        )).thenReturn(List.of());
        when(orderExecutionService.executeStockOrder(stockOrder, 69_900L, 2)).thenReturn(139_800L);

        // when: 실시간 호가 매칭을 수행한다.
        orderMatchingService.matchByRealtimeOrderbook(orderbookMessage);

        // then: 매도 호가 잔량인 2주만 체결 서비스로 넘긴다.
        verify(orderExecutionService).executeStockOrder(stockOrder, 69_900L, 2);
    }

    @Test
    @DisplayName("매도 주문은 매수 호가 잔량 기준으로 체결 가능한 수량만 체결 요청한다")
    void matchSellOrderByOrderbookBidLiquidity() {
        // given: 매수 1호가 잔량이 4주인 호가와 6주 매도 주문을 준비한다.
        StockOrder stockOrder = createStockOrder(OrderType.SELL, 70_000L, 6);
        KisRealtimeOrderbookMessage orderbookMessage = createOrderbookMessage(70_100L, 10L, 70_000L, 4L);

        when(stockOrderRepository.findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceDescCreatedAtAsc(
                "005930",
                OrderType.BUY,
                List.of(StockOrderStatus.PENDING, StockOrderStatus.PARTIALLY_FILLED)
        )).thenReturn(List.of());
        when(stockOrderRepository.findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceAscCreatedAtAsc(
                "005930",
                OrderType.SELL,
                List.of(StockOrderStatus.PENDING, StockOrderStatus.PARTIALLY_FILLED)
        )).thenReturn(List.of(stockOrder));
        when(orderExecutionService.executeStockOrder(stockOrder, 70_000L, 4)).thenReturn(280_000L);

        // when: 실시간 호가 매칭을 수행한다.
        orderMatchingService.matchByRealtimeOrderbook(orderbookMessage);

        // then: 매수 호가 잔량인 4주만 체결 서비스로 넘긴다.
        verify(orderExecutionService).executeStockOrder(stockOrder, 70_000L, 4);
    }

    @Test
    @DisplayName("서버 시작 시 현재가가 주문 조건에 맞으면 미체결 주문을 체결 요청한다")
    void matchOpenOrdersByCurrentQuotes() {
        // given: 서버 시작 시 조회할 미체결 매수 주문과 현재가를 준비한다.
        StockOrder stockOrder = createStockOrder(OrderType.BUY, 70_000L, 3);

        when(stockOrderRepository.findAllByStatusIn(anyCollection())).thenReturn(List.of(stockOrder));
        when(stockQuoteService.getQuote("005930")).thenReturn(createQuote(69_500L));
        when(orderExecutionService.executeStockOrder(stockOrder, 69_500L, 3)).thenReturn(208_500L);

        // when: 현재가 기반 시작 시점 매칭을 수행한다.
        orderMatchingService.matchOpenOrdersByCurrentQuotes();

        // then: 현재가가 주문가 이하이므로 남은 수량 전체를 체결 요청한다.
        verify(orderExecutionService).executeStockOrder(stockOrder, 69_500L, 3);
    }

    // 테스트마다 사용할 기본 미체결 주문을 생성한다.
    private StockOrder createStockOrder(OrderType orderType, Long orderPrice, Integer quantity) {
        return StockOrder.create(
                createUser(),
                createStock(),
                orderType,
                orderPrice,
                quantity,
                MarketSession.REGULAR
        );
    }

    // 테스트용 실시간 체결가 메시지를 생성한다.
    private KisRealtimeTradeMessage createTradeMessage(String symbol, Long currentPrice) {
        return KisRealtimeTradeMessage.builder()
                .symbol(symbol)
                .tradeTime("093000")
                .currentPrice(currentPrice)
                .build();
    }

    // 테스트용 실시간 호가 메시지를 생성한다.
    private KisRealtimeOrderbookMessage createOrderbookMessage(
            Long askPrice,
            Long askQuantity,
            Long bidPrice,
            Long bidQuantity
    ) {
        return KisRealtimeOrderbookMessage.builder()
                .symbol("005930")
                .businessTime("093000")
                .hourClassCode("0")
                .levels(List.of(KisRealtimeOrderbookLevel.builder()
                        .level(1)
                        .askPrice(askPrice)
                        .askQuantity(askQuantity)
                        .bidPrice(bidPrice)
                        .bidQuantity(bidQuantity)
                        .build()))
                .totalAskQuantity(askQuantity)
                .totalBidQuantity(bidQuantity)
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
