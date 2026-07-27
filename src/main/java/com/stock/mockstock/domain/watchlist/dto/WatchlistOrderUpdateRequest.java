// 관심종목 정렬 순서에 사용할 관심종목 ID 목록을 검증하는 요청 DTO다.
package com.stock.mockstock.domain.watchlist.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class WatchlistOrderUpdateRequest {

    @NotEmpty(message = "정렬할 관심종목 목록이 필요합니다.")
    @Size(max = ApplicationPolicy.MAX_WATCHLIST_COUNT, message = "관심종목 정렬 한도를 초과했습니다.")
    private List<Long> watchlistIds;
}
