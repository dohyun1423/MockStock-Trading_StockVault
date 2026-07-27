// 관심종목 API 요청을 받는 컨트롤러
package com.stock.mockstock.domain.watchlist.controller;

import com.stock.mockstock.domain.watchlist.dto.WatchlistCreateRequest;
import com.stock.mockstock.domain.watchlist.dto.WatchlistResponse;
import com.stock.mockstock.domain.watchlist.service.WatchlistService;
import com.stock.mockstock.domain.watchlist.dto.WatchlistOrderUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    // 관심종목 추가 요청 처리
    @PostMapping
    public void addWatchlist(
            Authentication authentication,
            @RequestBody @Valid WatchlistCreateRequest request
    ) {
        watchlistService.addWatchlist(authentication.getName(), request);
    }

    // 내 관심종목 목록 조회
    @GetMapping
    public List<WatchlistResponse> getMyWatchlists(Authentication authentication) {
        return watchlistService.getMyWatchlists(authentication.getName());
    }

    // 관심종목 목록의 사용자 지정 순서를 저장한다.
    @PatchMapping("/order")
    public void updateWatchlistOrder(
            Authentication authentication,
            @RequestBody @Valid WatchlistOrderUpdateRequest request
    ) {
        watchlistService.updateWatchlistOrder(authentication.getName(), request);
    }

    // 관심종목 삭제 요청 처리
    @DeleteMapping
    public void removeWatchlist(
            Authentication authentication,
            @RequestParam String stockName
    ) {
        watchlistService.removeWatchlist(authentication.getName(), stockName);
    }
}
