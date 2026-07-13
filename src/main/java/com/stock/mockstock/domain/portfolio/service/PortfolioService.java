// 사용자의 현금, 보유 주식, 평가금액, 수익률 정보를 계산하는 서비스
package com.stock.mockstock.domain.portfolio.service;

import com.stock.mockstock.domain.portfolio.dto.HoldingResponse;
import com.stock.mockstock.domain.portfolio.dto.PortfolioResponse;
import com.stock.mockstock.domain.portfolio.entity.Holding;
import com.stock.mockstock.domain.portfolio.repository.HoldingRepository;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final StockQuoteService stockQuoteService;

    // 내 현금, 보유 종목, 전체 평가금액과 전체 수익률 조회
    public PortfolioResponse getMyPortfolio(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        List<HoldingResponse> holdings = holdingRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toHoldingResponse)
                .toList();

        Long totalEvaluation = calculateTotalEvaluation(holdings);
        Long totalPurchaseAmount = calculateTotalPurchaseAmount(holdings);
        Long totalProfitLoss = calculateTotalProfitLoss(holdings);
        Long totalAsset = user.getCash() + totalEvaluation;
        BigDecimal totalProfitRate = calculateProfitRate(totalProfitLoss, totalPurchaseAmount);

        return new PortfolioResponse(
                user.getCash(),
                user.getReservedCash(),
                user.getAvailableCash(),
                totalAsset,
                totalEvaluation,
                totalPurchaseAmount,
                totalProfitLoss,
                totalProfitRate,
                holdings
        );
    }

    // 보유 종목 하나의 평가금액, 손익, 수익률 계산
    private HoldingResponse toHoldingResponse(Holding holding) {
        Long currentPrice = getCurrentPrice(holding);
        Long evaluationAmount = currentPrice * holding.getQuantity();
        Long purchaseAmount = holding.getAveragePrice() * holding.getQuantity();
        Long profitLoss = evaluationAmount - purchaseAmount;
        BigDecimal profitRate = calculateProfitRate(profitLoss, purchaseAmount);

        return new HoldingResponse(
                holding.getStock().getName(),
                holding.getStock().getSymbol(),
                holding.getQuantity(),
                holding.getReservedQuantity(),
                holding.getAvailableQuantity(),
                holding.getAveragePrice(),
                currentPrice,
                evaluationAmount,
                profitLoss,
                profitRate
        );
    }

    private Long getCurrentPrice(Holding holding) {
        StockQuoteResponse quote = stockQuoteService.getQuote(holding.getStock().getSymbol());

        if (quote != null && quote.getCurrentPrice() != null && quote.getCurrentPrice() > 0) {
            return quote.getCurrentPrice();
        }

        Long fallbackPrice = holding.getStock().getCurrentPrice();

        if (fallbackPrice == null || fallbackPrice < 0) {
            return 0L;
        }

        return fallbackPrice;
    }

    private Long calculateTotalEvaluation(List<HoldingResponse> holdings) {
        return holdings.stream()
                .mapToLong((holding) -> holding.getEvaluationAmount() == null ? 0L : holding.getEvaluationAmount())
                .sum();
    }

    private Long calculateTotalPurchaseAmount(List<HoldingResponse> holdings) {
        return holdings.stream()
                .mapToLong((holding) -> {
                    long averagePrice = holding.getAveragePrice() == null ? 0L : holding.getAveragePrice();
                    long quantity = holding.getQuantity() == null ? 0L : holding.getQuantity();
                    return averagePrice * quantity;
                })
                .sum();
    }

    private Long calculateTotalProfitLoss(List<HoldingResponse> holdings) {
        return holdings.stream()
                .mapToLong((holding) -> holding.getProfitLoss() == null ? 0L : holding.getProfitLoss())
                .sum();
    }

    private BigDecimal calculateProfitRate(Long profitLoss, Long purchaseAmount) {
        if (purchaseAmount == null || purchaseAmount == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(profitLoss)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(purchaseAmount), 2, RoundingMode.HALF_UP);
    }
}
