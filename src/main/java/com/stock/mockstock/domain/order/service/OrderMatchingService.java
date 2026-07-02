// 실시간 체결가를 기준으로 미체결/부분체결 주문의 자동 체결을 처리하는 서비스
package com.stock.mockstock.domain.order.service;

import com.stock.mockstock.domain.order.entity.StockOrder;
import com.stock.mockstock.domain.order.enumtype.OrderType;
import com.stock.mockstock.domain.order.enumtype.StockOrderStatus;
import com.stock.mockstock.domain.order.repository.StockOrderRepository;
import com.stock.mockstock.domain.stock.dto.StockQuoteResponse;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeOrderbookLevel;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeOrderbookMessage;
import com.stock.mockstock.domain.stock.realtime.KisRealtimeTradeMessage;
import com.stock.mockstock.domain.stock.service.StockQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMatchingService {

    private static final List<StockOrderStatus> MATCHABLE_STATUSES = List.of(
            StockOrderStatus.PENDING,
            StockOrderStatus.PARTIALLY_FILLED
    );

    private final StockOrderRepository stockOrderRepository;
    private final OrderExecutionService orderExecutionService;
    private final StockQuoteService stockQuoteService;

    // KIS 실시간 체결가가 들어올 때 해당 종목의 미체결 주문을 검사하고 조건이 맞으면 체결한다.
    @Transactional
    public void matchByRealtimeTrade(KisRealtimeTradeMessage tradeMessage) {
        if (tradeMessage == null || tradeMessage.getSymbol() == null) {
            return;
        }

        Long currentPrice = tradeMessage.getCurrentPrice();

        if (currentPrice == null || currentPrice <= 0) {
            return;
        }

        List<StockOrder> stockOrders = stockOrderRepository.findAllByStockSymbolAndStatusInOrderByCreatedAtAsc(
                normalizeSymbol(tradeMessage.getSymbol()),
                MATCHABLE_STATUSES
        );

        for (StockOrder stockOrder : stockOrders) {
            matchOrder(stockOrder, currentPrice);
        }
    }

    // KIS 실시간 호가가 들어올 때 호가 잔량을 기준으로 미체결 주문을 부분 체결한다.
    @Transactional
    public void matchByRealtimeOrderbook(KisRealtimeOrderbookMessage orderbookMessage) {
        if (orderbookMessage == null || orderbookMessage.getSymbol() == null) {
            return;
        }

        List<OrderbookLiquidity> askLiquidity = createAskLiquidity(orderbookMessage);
        List<OrderbookLiquidity> bidLiquidity = createBidLiquidity(orderbookMessage);
        String symbol = normalizeSymbol(orderbookMessage.getSymbol());

        matchBuyOrdersByAskLiquidity(symbol, askLiquidity);
        matchSellOrdersByBidLiquidity(symbol, bidLiquidity);
    }

    // 서버 시작 또는 복구 시점에 현재가를 한 번 조회해서 이미 조건을 만족한 미체결 주문을 체결한다.
    @Transactional
    public void matchOpenOrdersByCurrentQuotes() {
        List<StockOrder> stockOrders = stockOrderRepository.findAllByStatusIn(MATCHABLE_STATUSES);
        Map<String, Long> currentPricesBySymbol = new HashMap<>();

        for (StockOrder stockOrder : stockOrders) {
            String symbol = normalizeSymbol(stockOrder.getStock().getSymbol());

            if (!currentPricesBySymbol.containsKey(symbol)) {
                currentPricesBySymbol.put(symbol, getCurrentPriceSafely(symbol));
            }

            Long currentPrice = currentPricesBySymbol.get(symbol);

            if (currentPrice == null || currentPrice <= 0) {
                continue;
            }

            matchOrder(stockOrder, currentPrice);
        }

        log.info(
                "Open order startup matching completed. orderCount={}, symbolCount={}",
                stockOrders.size(),
                currentPricesBySymbol.size()
        );
    }

    // 지정가 조건이 현재 체결가와 맞으면 남은 수량 전체를 현재가로 체결한다.
    private void matchOrder(StockOrder stockOrder, Long currentPrice) {
        if (!stockOrder.isExecutableByPrice(currentPrice)) {
            return;
        }

        try {
            executeAndLog(stockOrder, currentPrice, stockOrder.getRemainingQuantity(), "quote");
        } catch (RuntimeException e) {
            stockOrder.markFailed(e.getMessage());

            log.warn(
                    "Pending order matching failed. orderId={}, symbol={}, reason={}",
                    stockOrder.getId(),
                    stockOrder.getStock().getSymbol(),
                    e.getMessage(),
                    e
            );
        }
    }

    // 매수 주문을 높은 주문가, 빠른 접수 순서로 보면서 매도호가 잔량만큼 체결한다.
    private void matchBuyOrdersByAskLiquidity(String symbol, List<OrderbookLiquidity> askLiquidity) {
        if (askLiquidity.isEmpty()) {
            return;
        }

        List<StockOrder> buyOrders = stockOrderRepository
                .findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceDescCreatedAtAsc(
                        symbol,
                        OrderType.BUY,
                        MATCHABLE_STATUSES
                );

        for (StockOrder stockOrder : buyOrders) {
            matchOrderByLiquidity(stockOrder, askLiquidity, true);
        }
    }

    // 매도 주문을 낮은 주문가, 빠른 접수 순서로 보면서 매수호가 잔량만큼 체결한다.
    private void matchSellOrdersByBidLiquidity(String symbol, List<OrderbookLiquidity> bidLiquidity) {
        if (bidLiquidity.isEmpty()) {
            return;
        }

        List<StockOrder> sellOrders = stockOrderRepository
                .findAllByStockSymbolAndOrderTypeAndStatusInOrderByOrderPriceAscCreatedAtAsc(
                        symbol,
                        OrderType.SELL,
                        MATCHABLE_STATUSES
                );

        for (StockOrder stockOrder : sellOrders) {
            matchOrderByLiquidity(stockOrder, bidLiquidity, false);
        }
    }

    // 한 주문을 여러 호가 단계에 걸쳐 가능한 수량만큼 부분 체결한다.
    private void matchOrderByLiquidity(
            StockOrder stockOrder,
            List<OrderbookLiquidity> liquidityLevels,
            boolean buyOrder
    ) {
        for (OrderbookLiquidity liquidity : liquidityLevels) {
            if (stockOrder.getRemainingQuantity() <= 0) {
                return;
            }

            if (liquidity.quantity() <= 0) {
                continue;
            }

            if (!isExecutableByOrderbookPrice(stockOrder, liquidity.price(), buyOrder)) {
                continue;
            }

            int executionQuantity = (int) Math.min(
                    stockOrder.getRemainingQuantity().longValue(),
                    liquidity.quantity()
            );

            try {
                executeAndLog(stockOrder, liquidity.price(), executionQuantity, "orderbook");
                liquidity.decrease(executionQuantity);
            } catch (RuntimeException e) {
                stockOrder.markFailed(e.getMessage());

                log.warn(
                        "Orderbook matching failed. orderId={}, symbol={}, reason={}",
                        stockOrder.getId(),
                        stockOrder.getStock().getSymbol(),
                        e.getMessage(),
                        e
                );
                return;
            }
        }
    }

    // 주문 방향에 따라 지정가와 호가 가격이 체결 조건을 만족하는지 확인한다.
    private boolean isExecutableByOrderbookPrice(
            StockOrder stockOrder,
            Long orderbookPrice,
            boolean buyOrder
    ) {
        if (orderbookPrice == null || orderbookPrice <= 0) {
            return false;
        }

        if (buyOrder) {
            return orderbookPrice <= stockOrder.getOrderPrice();
        }

        return orderbookPrice >= stockOrder.getOrderPrice();
    }

    // 실제 체결을 수행하고 공통 로그를 남긴다.
    private void executeAndLog(
            StockOrder stockOrder,
            Long executionPrice,
            Integer executionQuantity,
            String source
    ) {
        Long executedAmount = orderExecutionService.executeStockOrder(
                stockOrder,
                executionPrice,
                executionQuantity
        );

        log.info(
                "Pending order matched. source={}, orderId={}, symbol={}, type={}, price={}, quantity={}, amount={}, remaining={}",
                source,
                stockOrder.getId(),
                stockOrder.getStock().getSymbol(),
                stockOrder.getOrderType(),
                executionPrice,
                executionQuantity,
                executedAmount,
                stockOrder.getRemainingQuantity()
        );
    }

    // 매도호가를 낮은 가격 우선으로 사용할 수 있게 변환한다.
    private List<OrderbookLiquidity> createAskLiquidity(KisRealtimeOrderbookMessage orderbookMessage) {
        List<OrderbookLiquidity> liquidity = new ArrayList<>();

        for (KisRealtimeOrderbookLevel level : orderbookMessage.getLevels()) {
            if (level.getAskPrice() > 0 && level.getAskQuantity() > 0) {
                liquidity.add(new OrderbookLiquidity(level.getAskPrice(), level.getAskQuantity()));
            }
        }

        return liquidity;
    }

    // 매수호가를 높은 가격 우선으로 사용할 수 있게 변환한다.
    private List<OrderbookLiquidity> createBidLiquidity(KisRealtimeOrderbookMessage orderbookMessage) {
        List<OrderbookLiquidity> liquidity = new ArrayList<>();

        for (KisRealtimeOrderbookLevel level : orderbookMessage.getLevels()) {
            if (level.getBidPrice() > 0 && level.getBidQuantity() > 0) {
                liquidity.add(new OrderbookLiquidity(level.getBidPrice(), level.getBidQuantity()));
            }
        }

        return liquidity;
    }

    // 현재가 조회 실패가 서버 시작 흐름 전체를 막지 않도록 안전하게 조회한다.
    private Long getCurrentPriceSafely(String symbol) {
        try {
            StockQuoteResponse quote = stockQuoteService.getQuote(symbol);

            if (quote == null || quote.getCurrentPrice() == null || quote.getCurrentPrice() <= 0) {
                return null;
            }

            return quote.getCurrentPrice();
        } catch (RuntimeException e) {
            log.warn("Open order startup quote lookup failed. symbol={}", symbol, e);
            return null;
        }
    }

    // KIS와 DB 조회 기준을 맞추기 위해 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        return String.valueOf(symbol)
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    // 호가 한 단계에서 아직 체결에 사용할 수 있는 잔량을 관리한다.
    private static class OrderbookLiquidity {

        private final Long price;
        private long quantity;

        private OrderbookLiquidity(Long price, long quantity) {
            this.price = price;
            this.quantity = quantity;
        }

        private Long price() {
            return price;
        }

        private long quantity() {
            return quantity;
        }

        private void decrease(long executionQuantity) {
            this.quantity = Math.max(0L, this.quantity - executionQuantity);
        }
    }
}
