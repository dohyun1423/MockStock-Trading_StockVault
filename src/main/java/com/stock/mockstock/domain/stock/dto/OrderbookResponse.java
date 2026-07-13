// 종목별 현재가와 호가 단계 목록을 전달하는 응답 DTO다.
package com.stock.mockstock.domain.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class OrderbookResponse {

    private String symbol;
    private Long currentPrice;
    private Long basePrice;
    private Long openPrice;
    private Long highPrice;
    private Long lowPrice;
    private Long volume;
    private List<OrderbookLevelResponse> levels;
    private Long totalAskQuantity;
    private Long totalBidQuantity;
}
