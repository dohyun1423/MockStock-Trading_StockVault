// 호가 한 단계의 매도/매수 가격, 잔량, 등락률을 전달하는 응답 DTO다.
package com.stock.mockstock.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderbookLevelResponse {

    private Integer level;
    private Long askPrice;
    private Long askQuantity;
    private Double askRate;
    private Long bidPrice;
    private Long bidQuantity;
    private Double bidRate;
}
