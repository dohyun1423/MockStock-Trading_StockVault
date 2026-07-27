// 관심종목 추가, 조회, 삭제 비즈니스 로직을 처리하는 서비스
package com.stock.mockstock.domain.watchlist.service;

import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.repository.UserRepository;
import com.stock.mockstock.domain.watchlist.dto.WatchlistCreateRequest;
import com.stock.mockstock.domain.watchlist.dto.WatchlistResponse;
import com.stock.mockstock.domain.watchlist.entity.Watchlist;
import com.stock.mockstock.domain.watchlist.repository.WatchlistRepository;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.stock.repository.StockRepository;
import com.stock.mockstock.domain.watchlist.dto.WatchlistOrderUpdateRequest;
import com.stock.mockstock.global.policy.ApplicationPolicy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    // 로그인한 사용자의 관심종목 추가
    public void addWatchlist(String email, WatchlistCreateRequest request) {
        User user = getUser(email);
        Stock stock = stockRepository.findFirstByNameIgnoreCase(request.getStockName().trim())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다."));
        String stockName = stock.getName();

        if (watchlistRepository.existsByUserAndStockName(user, stockName)) {
            return;
        }

        if (watchlistRepository.countByUser(user) >= ApplicationPolicy.MAX_WATCHLIST_COUNT) {
            throw new IllegalArgumentException("관심종목은 최대 50개까지 저장할 수 있습니다.");
        }

        Watchlist watchlist = Watchlist.builder()
                .user(user)
                .stockName(stockName)
                .sortOrder(getNextSortOrder(user))
                .build();

        watchlistRepository.save(watchlist);
    }

    // 로그인한 사용자의 관심종목 목록 조회
    @Transactional(readOnly = true)
    public List<WatchlistResponse> getMyWatchlists(String email) {
        User user = getUser(email);

        return watchlistRepository.findAllByUserOrderBySortOrder(user)
                .stream()
                .map(this::toWatchlistResponse)
                .toList();
    }

    // 관심종목명으로 stock 테이블을 조회해서 symbol 정보를 함께 내려줌
    private WatchlistResponse toWatchlistResponse(Watchlist watchlist) {
        String stockName = watchlist.getStockName();

        Stock stock = stockRepository.findFirstByNameIgnoreCase(stockName)
                .or(() -> stockRepository.findFirstByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(stockName, stockName))
                .orElse(null);

        return WatchlistResponse.from(watchlist, stock);
    }

    // 로그인한 사용자의 관심종목 삭제
    public void removeWatchlist(String email, String stockName) {
        User user = getUser(email);

        watchlistRepository.deleteByUserAndStockName(user, stockName);
    }

    // 관심 종목 업데이트
    public void updateWatchlistOrder(String email, WatchlistOrderUpdateRequest request) {
        User user = getUser(email);
        List<Watchlist> watchlists = watchlistRepository.findAllByUserOrderBySortOrder(user);
        List<Long> requestedIds = request.getWatchlistIds();

        if (requestedIds.size() != watchlists.size()) {
            throw new IllegalArgumentException("관심종목 목록을 다시 조회한 뒤 정렬해 주세요.");
        }

        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new IllegalArgumentException("중복된 관심종목 정렬 정보가 포함되어 있습니다.");
        }

        Map<Long, Watchlist> watchlistById = new HashMap<>();
        for (Watchlist watchlist : watchlists) {
            watchlistById.put(watchlist.getId(), watchlist);
        }

        for (int index = 0; index < requestedIds.size(); index++) {
            Watchlist watchlist = watchlistById.get(requestedIds.get(index));
            if (watchlist == null) {
                throw new IllegalArgumentException("본인의 관심종목만 정렬할 수 있습니다.");
            }
            watchlist.updateSortOrder(index + 1);
        }
    }

    // 관심 종목 목록 순서 다음으로 변경
    private Integer getNextSortOrder(User user) {
        Integer maxSortOrder = watchlistRepository.findMaxSortOrderByUser(user);

        if (maxSortOrder == null) {
            return 1;
        }

        return maxSortOrder + 1;
    }

    // 이메일로 유저 확인
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }
}
