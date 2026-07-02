// 보유 주식 정보를 화면에 보여주기 위한 응답 DTO
package com.stock.mockstock.domain.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class HoldingResponse {

    private String stockName;
    private String symbol;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Long averagePrice;
    private Long currentPrice;
    private Long evaluationAmount;
    private Long profitLoss;
    private BigDecimal profitRate;
}
