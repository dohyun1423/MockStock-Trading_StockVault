package com.stock.mockstock.domain.stock.controller;

import com.stock.mockstock.domain.stock.dto.StockDetailResponse;
import com.stock.mockstock.domain.stock.service.RankedStockCatalogService;
import com.stock.mockstock.domain.stock.service.VolumeRankingUnavailableException;
import com.stock.mockstock.global.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/rankings/volume")
public class RankedStockCatalogController {
    private final RankedStockCatalogService catalog;

    @PostMapping("/{symbol}/resolve")
    public StockDetailResponse resolve(@PathVariable String symbol) {
        return catalog.resolve(symbol);
    }

    @ExceptionHandler(VolumeRankingUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(VolumeRankingUnavailableException error) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(error.getMessage(), UUID.randomUUID().toString()));
    }
}
