// 종목별 마지막 정상 체결가와 호가를 보관해 잘못된 0 데이터가 화면을 덮어쓰지 않도록 관리한다.
package com.stock.mockstock.domain.stock.realtime;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StockRealtimeSnapshotCache {

    private final ConcurrentHashMap<String, KisRealtimeTradeMessage> tradesBySymbol =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KisRealtimeOrderbookMessage> orderbooksBySymbol =
            new ConcurrentHashMap<>();

    // 현재가가 유효한 체결가만 저장하고 일부 값이 0이면 이전 정상값으로 보완한다.
    public Optional<KisRealtimeTradeMessage> storeTrade(KisRealtimeTradeMessage tradeMessage) {
        if (!isValidTrade(tradeMessage)) {
            return Optional.empty();
        }

        String symbol = normalizeSymbol(tradeMessage.getSymbol());
        KisRealtimeTradeMessage storedMessage = tradesBySymbol.compute(
                symbol,
                (key, previousMessage) -> mergeTrade(previousMessage, tradeMessage, symbol)
        );

        return Optional.of(storedMessage);
    }

    // 가격이나 잔량이 하나라도 존재하는 정상 호가만 마지막 스냅샷으로 저장한다.
    public Optional<KisRealtimeOrderbookMessage> storeOrderbook(
            KisRealtimeOrderbookMessage orderbookMessage
    ) {
        if (!isValidOrderbook(orderbookMessage)) {
            return Optional.empty();
        }

        String symbol = normalizeSymbol(orderbookMessage.getSymbol());
        KisRealtimeOrderbookMessage storedMessage = copyOrderbook(orderbookMessage, symbol);
        orderbooksBySymbol.put(symbol, storedMessage);

        return Optional.of(storedMessage);
    }

    // 지정한 종목의 마지막 정상 체결가를 반환한다.
    public Optional<KisRealtimeTradeMessage> getTrade(String symbol) {
        return Optional.ofNullable(tradesBySymbol.get(normalizeSymbol(symbol)));
    }

    // 지정한 종목의 마지막 정상 호가를 반환한다.
    public Optional<KisRealtimeOrderbookMessage> getOrderbook(String symbol) {
        return Optional.ofNullable(orderbooksBySymbol.get(normalizeSymbol(symbol)));
    }

    // 체결가 메시지에 종목코드와 양수 현재가가 있는지 확인한다.
    private boolean isValidTrade(KisRealtimeTradeMessage tradeMessage) {
        return tradeMessage != null
                && !normalizeSymbol(tradeMessage.getSymbol()).isBlank()
                && tradeMessage.getCurrentPrice() > 0L;
    }

    // 모든 호가 가격과 수량이 0인 메시지가 정상 화면을 덮어쓰지 않도록 검사한다.
    private boolean isValidOrderbook(KisRealtimeOrderbookMessage orderbookMessage) {
        if (
                orderbookMessage == null
                || normalizeSymbol(orderbookMessage.getSymbol()).isBlank()
                || orderbookMessage.getLevels() == null
                || orderbookMessage.getLevels().isEmpty()
        ) {
            return false;
        }

        return orderbookMessage.getLevels().stream()
                .anyMatch(level ->
                        level.getAskPrice() > 0L
                                || level.getBidPrice() > 0L
                                || level.getAskQuantity() > 0L
                                || level.getBidQuantity() > 0L
                );
    }

    // 새 체결가의 비어 있는 가격 지표를 이전 정상 체결가로 보완한다.
    private KisRealtimeTradeMessage mergeTrade(
            KisRealtimeTradeMessage previousMessage,
            KisRealtimeTradeMessage currentMessage,
            String symbol
    ) {
        if (previousMessage == null) {
            return copyTrade(currentMessage, symbol);
        }

        return KisRealtimeTradeMessage.builder()
                .symbol(symbol)
                .tradeTime(currentMessage.getTradeTime())
                .currentPrice(currentMessage.getCurrentPrice())
                .changeSign(currentMessage.getChangeSign())
                .changePrice(currentMessage.getChangePrice())
                .changeRate(currentMessage.getChangeRate())
                .openPrice(positiveOrPrevious(
                        currentMessage.getOpenPrice(),
                        previousMessage.getOpenPrice()
                ))
                .highPrice(positiveOrPrevious(
                        currentMessage.getHighPrice(),
                        previousMessage.getHighPrice()
                ))
                .lowPrice(positiveOrPrevious(
                        currentMessage.getLowPrice(),
                        previousMessage.getLowPrice()
                ))
                .askPrice(positiveOrPrevious(
                        currentMessage.getAskPrice(),
                        previousMessage.getAskPrice()
                ))
                .bidPrice(positiveOrPrevious(
                        currentMessage.getBidPrice(),
                        previousMessage.getBidPrice()
                ))
                .tradeVolume(currentMessage.getTradeVolume())
                .accumulatedVolume(positiveOrPrevious(
                        currentMessage.getAccumulatedVolume(),
                        previousMessage.getAccumulatedVolume()
                ))
                .marketSession(
                        currentMessage.getMarketSession() != null
                                ? currentMessage.getMarketSession()
                                : previousMessage.getMarketSession()
                )
                .build();
    }

    // Normalizes the symbol while preserving every value in the first valid trade snapshot.
    private KisRealtimeTradeMessage copyTrade(
            KisRealtimeTradeMessage tradeMessage,
            String symbol
    ) {
        return KisRealtimeTradeMessage.builder()
                .symbol(symbol)
                .tradeTime(tradeMessage.getTradeTime())
                .currentPrice(tradeMessage.getCurrentPrice())
                .changeSign(tradeMessage.getChangeSign())
                .changePrice(tradeMessage.getChangePrice())
                .changeRate(tradeMessage.getChangeRate())
                .openPrice(tradeMessage.getOpenPrice())
                .highPrice(tradeMessage.getHighPrice())
                .lowPrice(tradeMessage.getLowPrice())
                .askPrice(tradeMessage.getAskPrice())
                .bidPrice(tradeMessage.getBidPrice())
                .tradeVolume(tradeMessage.getTradeVolume())
                .accumulatedVolume(tradeMessage.getAccumulatedVolume())
                .marketSession(tradeMessage.getMarketSession())
                .build();
    }

    // Normalizes the symbol while preserving the valid orderbook snapshot.
    private KisRealtimeOrderbookMessage copyOrderbook(
            KisRealtimeOrderbookMessage orderbookMessage,
            String symbol
    ) {
        return KisRealtimeOrderbookMessage.builder()
                .symbol(symbol)
                .businessTime(orderbookMessage.getBusinessTime())
                .hourClassCode(orderbookMessage.getHourClassCode())
                .levels(orderbookMessage.getLevels())
                .totalAskQuantity(orderbookMessage.getTotalAskQuantity())
                .totalBidQuantity(orderbookMessage.getTotalBidQuantity())
                .marketSession(orderbookMessage.getMarketSession())
                .build();
    }

    // 새 값이 양수가 아니면 마지막 정상값을 유지한다.
    private long positiveOrPrevious(long currentValue, long previousValue) {
        return currentValue > 0L ? currentValue : previousValue;
    }

    // 종목별 캐시 키가 일관되도록 종목코드를 정규화한다.
    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }

        return symbol
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase();
    }
}
