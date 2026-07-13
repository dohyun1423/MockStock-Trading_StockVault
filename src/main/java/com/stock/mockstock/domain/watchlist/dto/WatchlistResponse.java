// 관심종목 목록 화면에 필요한 종목명, 심볼, 시장, 정렬 순서를 전달하는 DTO다.
package com.stock.mockstock.domain.watchlist.dto;

import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.watchlist.entity.Watchlist;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WatchlistResponse {

    private Long id;
    private String stockName;
    private String symbol;
    private String market;
    private Integer sortOrder;

    // 관심종목 엔티티와 종목 엔티티를 화면 응답 DTO로 변환한다.
    public static WatchlistResponse from(Watchlist watchlist, Stock stock) {
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getStockName(),
                stock == null ? null : stock.getSymbol(),
                stock == null ? null : stock.getMarket(),
                watchlist.getSortOrder()
        );
    }
}
