// 주문 체결 사실을 로그인한 브라우저에 알려주기 위한 WebSocket 알림 DTO
package com.stock.mockstock.domain.order.dto;

import com.stock.mockstock.domain.order.enumtype.OrderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderExecutionNotification {

    private Long orderId;
    private String stockName;
    private String symbol;
    private OrderType orderType;
    private Integer quantity;
    private Long price;
    private Long totalAmount;
    private String message;
}
