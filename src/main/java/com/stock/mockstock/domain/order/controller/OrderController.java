// 주문 접수, 미체결 주문 조회, 주문 수정/취소 API를 제공하는 컨트롤러다.
package com.stock.mockstock.domain.order.controller;

import com.stock.mockstock.domain.order.dto.MarketSessionResponse;
import com.stock.mockstock.domain.order.dto.OrderRequest;
import com.stock.mockstock.domain.order.dto.OrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderResponse;
import com.stock.mockstock.domain.order.dto.StockOrderUpdateRequest;
import com.stock.mockstock.domain.order.service.MarketSessionService;
import com.stock.mockstock.domain.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final MarketSessionService marketSessionService;

    // 현재 거래 세션과 주문 가능 상태를 조회한다.
    @GetMapping("/session")
    public MarketSessionResponse getCurrentMarketSession() {
        return marketSessionService.getCurrentSessionResponse();
    }

    // 로그인 사용자의 미체결/부분체결 주문 목록을 조회한다.
    @GetMapping("/open")
    public List<StockOrderResponse> getOpenOrders(Authentication authentication) {
        return orderService.getMyOpenOrders(authentication.getName());
    }

    // 로그인 사용자의 매수 주문을 접수한다.
    @PostMapping("/buy")
    public OrderResponse buy(
            Authentication authentication,
            @RequestBody @Valid OrderRequest request
    ) {
        return orderService.buy(authentication.getName(), request);
    }

    // 로그인 사용자의 매도 주문을 접수한다.
    @PostMapping("/sell")
    public OrderResponse sell(
            Authentication authentication,
            @RequestBody @Valid OrderRequest request
    ) {
        return orderService.sell(authentication.getName(), request);
    }

    // 미체결/부분체결 주문의 주문가와 남은 수량을 수정한다.
    @PatchMapping("/{orderId}")
    public StockOrderResponse updateOrder(
            Authentication authentication,
            @PathVariable Long orderId,
            @RequestBody @Valid StockOrderUpdateRequest request
    ) {
        return orderService.updateOrder(authentication.getName(), orderId, request);
    }

    // 미체결/부분체결 주문을 취소하고 예약 현금 또는 예약 수량을 해제한다.
    @PatchMapping("/{orderId}/cancel")
    public StockOrderResponse cancelOrder(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        return orderService.cancelOrder(authentication.getName(), orderId);
    }
}
