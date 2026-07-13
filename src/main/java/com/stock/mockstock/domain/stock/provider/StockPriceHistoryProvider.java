// 차트 가격 이력 데이터 제공 구현체가 공통으로 따라야 하는 인터페이스다.
package com.stock.mockstock.domain.stock.provider;

import com.stock.mockstock.domain.stock.dto.StockPriceHistoryResponse;

import java.util.List;

public interface StockPriceHistoryProvider {

    // 종목코드와 기간을 기준으로 차트 가격 이력을 조회한다.
    List<StockPriceHistoryResponse> getPriceHistories(String symbol, String period);
}
