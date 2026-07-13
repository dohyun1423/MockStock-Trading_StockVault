// 관심종목 순서 변경 요청에서 정렬된 관심종목 id 목록을 받는 DTO다.
package com.stock.mockstock.domain.watchlist.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class WatchlistOrderUpdateRequest {

    private List<Long> watchlistIds;
}
