// 매수/매도 주문 요청에 필요한 종목코드, 수량, 주문가를 담는 DTO다.
package com.stock.mockstock.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class OrderRequest {

    @NotBlank(message = "종목코드는 필수입니다.")
    private String symbol;

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 1주 이상이어야 합니다.")
    private Integer quantity;

    // 사용자가 지정한 주문 가격이다. 비어 있으면 서버에서 현재가를 기본 주문가로 사용한다.
    private Long orderPrice;
}
