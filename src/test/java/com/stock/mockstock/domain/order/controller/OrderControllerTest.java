// OrderController가 인증 사용자 email을 기준으로 주문 서비스에 요청을 위임하는지 검증하는 테스트
package com.stock.mockstock.domain.order.controller;

import com.stock.mockstock.domain.order.dto.MarketSessionResponse;
import com.stock.mockstock.domain.order.dto.OrderRequest;
import com.stock.mockstock.domain.order.dto.OrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderUpdateRequest;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private MarketSessionService marketSessionService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OrderController orderController;

    @Test
    @DisplayName("매수 요청은 로그인 사용자 email과 주문 요청을 서비스로 전달한다")
    void buy() {
        // given: 로그인 사용자와 매수 요청, 서비스 응답을 준비한다.
        OrderRequest request = createOrderRequest("005930", 3, 70_000L);
        OrderResponse expected = createOrderResponse(OrderType.BUY, "PENDING");

        when(authentication.getName()).thenReturn("test@example.com");
        when(orderService.buy("test@example.com", request)).thenReturn(expected);

        // when: 매수 API 메서드를 호출한다.
        OrderResponse response = orderController.buy(authentication, request);

        // then: 인증 email 기준으로 주문 서비스가 호출되고 응답이 반환된다.
        assertThat(response).isSameAs(expected);
        verify(orderService).buy("test@example.com", request);
    }

    @Test
    @DisplayName("매도 요청은 로그인 사용자 email과 주문 요청을 서비스로 전달한다")
    void sell() {
        // given: 로그인 사용자와 매도 요청, 서비스 응답을 준비한다.
        OrderRequest request = createOrderRequest("005930", 2, 72_000L);
        OrderResponse expected = createOrderResponse(OrderType.SELL, "PENDING");

        when(authentication.getName()).thenReturn("test@example.com");
        when(orderService.sell("test@example.com", request)).thenReturn(expected);

        // when: 매도 API 메서드를 호출한다.
        OrderResponse response = orderController.sell(authentication, request);

        // then: 인증 email 기준으로 주문 서비스가 호출되고 응답이 반환된다.
        assertThat(response).isSameAs(expected);
        verify(orderService).sell("test@example.com", request);
    }

    @Test
    @DisplayName("미체결 주문 조회는 로그인 사용자 email을 서비스로 전달한다")
    void getOpenOrders() {
        // given: 로그인 사용자와 미체결 주문 응답을 준비한다.
        List<StockOrderResponse> expected = List.of(createStockOrderResponse());

        when(authentication.getName()).thenReturn("test@example.com");
        when(orderService.getMyOpenOrders("test@example.com")).thenReturn(expected);

        // when: 미체결 주문 조회 API 메서드를 호출한다.
        List<StockOrderResponse> response = orderController.getOpenOrders(authentication);

        // then: 인증 email 기준으로 주문 목록이 조회된다.
        assertThat(response).isSameAs(expected);
        verify(orderService).getMyOpenOrders("test@example.com");
    }

    @Test
    @DisplayName("주문 수정 요청은 로그인 사용자 email과 주문 id를 서비스로 전달한다")
    void updateOrder() {
        // given: 로그인 사용자, 주문 id, 수정 요청, 수정 응답을 준비한다.
        StockOrderUpdateRequest request = createUpdateRequest(71_000L, 2);
        StockOrderResponse expected = createStockOrderResponse();

        when(authentication.getName()).thenReturn("test@example.com");
        when(orderService.updateOrder("test@example.com", 10L, request)).thenReturn(expected);

        // when: 주문 수정 API 메서드를 호출한다.
        StockOrderResponse response = orderController.updateOrder(authentication, 10L, request);

        // then: 인증 email 기준으로 주문 수정 서비스가 호출된다.
        assertThat(response).isSameAs(expected);
        verify(orderService).updateOrder("test@example.com", 10L, request);
    }

    @Test
    @DisplayName("주문 취소 요청은 로그인 사용자 email과 주문 id를 서비스로 전달한다")
    void cancelOrder() {
        // given: 로그인 사용자와 취소 응답을 준비한다.
        StockOrderResponse expected = createStockOrderResponse();

        when(authentication.getName()).thenReturn("test@example.com");
        when(orderService.cancelOrder("test@example.com", 10L)).thenReturn(expected);

        // when: 주문 취소 API 메서드를 호출한다.
        StockOrderResponse response = orderController.cancelOrder(authentication, 10L);

        // then: 인증 email 기준으로 주문 취소 서비스가 호출된다.
        assertThat(response).isSameAs(expected);
        verify(orderService).cancelOrder("test@example.com", 10L);
    }

    @Test
    @DisplayName("거래 세션 조회는 MarketSessionService 응답을 그대로 반환한다")
    void getCurrentMarketSession() {
        // given: 현재 거래 세션 응답을 준비한다.
        MarketSessionResponse expected = new MarketSessionResponse(
                MarketSession.REGULAR,
                "정규장",
                true,
                true,
                false,
                "주문 가능"
        );

        when(marketSessionService.getCurrentSessionResponse()).thenReturn(expected);

        // when: 세션 조회 API 메서드를 호출한다.
        MarketSessionResponse response = orderController.getCurrentMarketSession();

        // then: 서비스 응답이 그대로 반환된다.
        assertThat(response).isSameAs(expected);
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

    // 테스트용 주문 응답 DTO를 생성한다.
    private OrderResponse createOrderResponse(OrderType orderType, String orderStatus) {
        return new OrderResponse(
                "삼성전자",
                orderType,
                3,
                70_000L,
                210_000L,
                790_000L,
                orderStatus,
                MarketSession.REGULAR,
                10L,
                "주문 접수"
        );
    }

    // 테스트용 미체결 주문 응답 DTO를 생성한다.
    private StockOrderResponse createStockOrderResponse() {
        return new StockOrderResponse(
                10L,
                "삼성전자",
                "005930",
                OrderType.BUY,
                70_000L,
                3,
                0,
                3,
                210_000L,
                0,
                MarketSession.REGULAR,
                StockOrderStatus.PENDING,
                null
        );
    }
}
