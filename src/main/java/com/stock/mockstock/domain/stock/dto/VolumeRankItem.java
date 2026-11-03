package com.stock.mockstock.domain.stock.dto;

import java.math.BigDecimal;

public record VolumeRankItem(
        int rank,
        String symbol,
        String name,
        long currentPrice,
        BigDecimal changeRate,
        long volume,
        boolean available
) {
}
