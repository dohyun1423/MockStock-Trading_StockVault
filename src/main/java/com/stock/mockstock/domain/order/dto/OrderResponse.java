package com.stock.mockstock.domain.order.dto;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderResponse {

    private String stockName;
    private OrderType orderType;
    private Integer quantity;
    private Long price;
    private Long totalAmount;
    private Long cashBalance;

    // EXECUTED 또는 PENDING처럼 주문 처리 결과를 나타낸다.
    private String orderStatus;

    // 주문이 접수된 현재 거래 세션이다.
    private MarketSession marketSession;

    // 주문 생성 후 추적할 수 있는 StockOrder 식별자다.
    private Long stockOrderId;

    private String message;
}
