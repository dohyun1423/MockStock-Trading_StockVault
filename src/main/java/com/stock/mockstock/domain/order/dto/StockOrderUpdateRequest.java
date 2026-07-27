// 미체결·부분체결 주문의 가격과 남은 수량을 검증하는 수정 요청 DTO다.
package com.stock.mockstock.domain.order.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class StockOrderUpdateRequest {

    @NotNull(message = "주문 가격은 필수입니다.")
    @Min(value = 1, message = "주문 가격은 1원 이상이어야 합니다.")
    @Max(value = ApplicationPolicy.MAX_ORDER_PRICE, message = "한 번에 입력할 수 있는 주문 가격을 초과했습니다.")
    private Long orderPrice;

    @NotNull(message = "미체결 수량은 필수입니다.")
    @Min(value = 1, message = "미체결 수량은 1주 이상이어야 합니다.")
    @Max(value = ApplicationPolicy.MAX_ORDER_QUANTITY, message = "한 번에 주문할 수 있는 수량을 초과했습니다.")
    private Integer remainingQuantity;
}
