// 호가 데이터 제공 구현체가 공통으로 따라야 하는 인터페이스다.
package com.stock.mockstock.domain.stock.provider;

import com.stock.mockstock.domain.stock.dto.OrderbookResponse;

public interface OrderbookProvider {

    // 종목코드 기준으로 호가 데이터를 조회한다.
    OrderbookResponse getOrderbook(String symbol);
}
