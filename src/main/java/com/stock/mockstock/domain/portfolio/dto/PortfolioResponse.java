// 사용자의 모의투자 계좌 요약과 보유 주식 목록을 내려주는 응답 DTO
package com.stock.mockstock.domain.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class PortfolioResponse {

    private Long cashBalance;
    private Long reservedCash;
    private Long availableCash;
    private Long totalAsset;
    private Long totalEvaluation;
    private Long totalPurchaseAmount;
    private Long totalProfitLoss;
    private BigDecimal totalProfitRate;
    private List<HoldingResponse> holdings;
}
