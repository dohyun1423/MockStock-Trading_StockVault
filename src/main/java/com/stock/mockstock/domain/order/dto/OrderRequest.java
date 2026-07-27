// 매수·매도 주문에 필요한 종목코드, 수량, 주문 가격을 검증하는 요청 DTO다.
package com.stock.mockstock.domain.order.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class OrderRequest {

    @NotBlank(message = "종목코드는 필수입니다.")
    @Size(max = ApplicationPolicy.MAX_SYMBOL_LENGTH, message = "종목코드가 너무 깁니다.")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "종목코드 형식이 올바르지 않습니다.")
    private String symbol;

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 1주 이상이어야 합니다.")
    @Max(value = ApplicationPolicy.MAX_ORDER_QUANTITY, message = "한 번에 주문할 수 있는 수량을 초과했습니다.")
    private Integer quantity;

    // 값이 없으면 주문 서비스가 현재가를 기본 주문 가격으로 사용한다.
    @Min(value = 1, message = "주문 가격은 1원 이상이어야 합니다.")
    @Max(value = ApplicationPolicy.MAX_ORDER_PRICE, message = "한 번에 입력할 수 있는 주문 가격을 초과했습니다.")
    private Long orderPrice;
}
