package com.stock.mockstock.domain.stock.dto;

import java.util.List;

public record VolumeRankingResponse(
        List<VolumeRankItem> items,
        String fetchedAt,
        boolean stale,
        long refreshIntervalSeconds
) {
}
