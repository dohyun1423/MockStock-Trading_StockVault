// 미체결/부분체결 주문의 주문가와 남은 수량을 수정하기 위한 요청 DTO
package com.stock.mockstock.domain.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class StockOrderUpdateRequest {

    // 수정 후 적용할 주문 가격이다.
    @NotNull(message = "주문 가격은 필수입니다.")
    @Min(value = 1, message = "주문 가격은 1원 이상이어야 합니다.")
    private Long orderPrice;

    // 수정 후 남겨둘 미체결 수량이다.
    @NotNull(message = "미체결 수량은 필수입니다.")
    @Min(value = 1, message = "미체결 수량은 1주 이상이어야 합니다.")
    private Integer remainingQuantity;
}
