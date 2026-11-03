package com.stock.mockstock.domain.stock.controller;

import com.stock.mockstock.domain.stock.dto.VolumeRankingResponse;
import com.stock.mockstock.domain.stock.service.VolumeRankingService;
import com.stock.mockstock.domain.stock.service.VolumeRankingUnavailableException;
import com.stock.mockstock.global.response.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VolumeRankingController {
    private final VolumeRankingService volumeRankingService;

    @GetMapping("/api/stocks/rankings/volume")
    public ResponseEntity<VolumeRankingResponse> getVolumeRanking() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(volumeRankingService.getRanking());
    }

    @ExceptionHandler(VolumeRankingUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(VolumeRankingUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(ErrorResponse.of(e.getMessage(), UUID.randomUUID().toString()));
    }
}
