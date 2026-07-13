// 호가 provider를 통해 종목별 호가 데이터를 조회하는 서비스다.
package com.stock.mockstock.domain.stock.service;

import com.stock.mockstock.domain.stock.dto.OrderbookResponse;
import com.stock.mockstock.domain.stock.provider.OrderbookProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderbookService {

    private final OrderbookProvider orderbookProvider;

    // 호가 조회 실패 시 화면이 깨지지 않도록 예외를 로그로 남기고 다시 전달한다.
    public OrderbookResponse getOrderbook(String symbol) {
        try {
            return orderbookProvider.getOrderbook(symbol);
        } catch (RuntimeException e) {
            log.warn("Orderbook lookup failed. symbol={}", symbol, e);
            throw e;
        }
    }
}
