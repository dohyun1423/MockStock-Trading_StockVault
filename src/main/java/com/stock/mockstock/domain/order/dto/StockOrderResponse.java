// 사용자의 미체결/예약 주문을 화면에 보여주기 위한 응답 DTO
package com.stock.mockstock.domain.order.dto;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.MarketSession;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class StockOrderResponse {

    private Long id;
    private String stockName;
    private String symbol;
    private OrderType orderType;
    private Long orderPrice;
    private Integer quantity;
    private Integer executedQuantity;
    private Integer remainingQuantity;
    private Long reservedAmount;
    private Integer reservedQuantity;
    private MarketSession marketSession;
    private StockOrderStatus status;
    private LocalDateTime orderedAt;

    // StockOrder 엔티티를 화면 출력용 DTO로 변환한다.
    public static StockOrderResponse from(StockOrder stockOrder) {
        return new StockOrderResponse(
                stockOrder.getId(),
                stockOrder.getStock().getName(),
                stockOrder.getStock().getSymbol(),
                stockOrder.getOrderType(),
                stockOrder.getOrderPrice(),
                stockOrder.getQuantity(),
                stockOrder.getExecutedQuantity(),
                stockOrder.getRemainingQuantity(),
                stockOrder.getRemainingReservedAmount(),
                stockOrder.getRemainingReservedQuantity(),
                stockOrder.getMarketSession(),
                stockOrder.getStatus(),
                stockOrder.getCreatedAt()
        );
    }
}
