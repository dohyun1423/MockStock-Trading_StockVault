// KIS 국내주식 KRX·NXT·통합 WebSocket TR ID와 세션 매핑을 관리한다.
package com.stock.mockstock.domain.stock.realtime;

import com.stock.mockstock.domain.order.enumtype.MarketSession;

public final class KisRealtimeTrId {

    public static final String REGULAR_TRADE = "H0STCNT0";
    public static final String REGULAR_ORDERBOOK = "H0STASP0";
    public static final String AFTER_HOURS_TRADE = "H0STOUP0";
    public static final String AFTER_HOURS_ORDERBOOK = "H0STOAA0";
    public static final String NXT_TRADE = "H0NXCNT0";
    public static final String NXT_ORDERBOOK = "H0NXASP0";
    public static final String UNIFIED_TRADE = "H0UNCNT0";
    public static final String UNIFIED_ORDERBOOK = "H0UNASP0";

    private KisRealtimeTrId() {
    }

    // 현재 거래 세션에 맞는 체결가 WebSocket TR ID를 반환한다.
    public static String resolveTradeTrId(MarketSession marketSession) {
        if (marketSession == MarketSession.AFTER_HOURS_SINGLE_PRICE) {
            return AFTER_HOURS_TRADE;
        }

        return UNIFIED_TRADE;
    }

    // 현재 거래 세션에 맞는 호가 WebSocket TR ID를 반환한다.
    public static String resolveOrderbookTrId(MarketSession marketSession) {
        if (marketSession == MarketSession.AFTER_HOURS_SINGLE_PRICE) {
            return AFTER_HOURS_ORDERBOOK;
        }

        return UNIFIED_ORDERBOOK;
    }

    // KIS payload에서 TR ID를 추출한다.
    public static String extractTrId(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }

        String[] pipeParts = payload.split("\\|", 4);

        if (pipeParts.length < 2) {
            return "";
        }

        return pipeParts[1];
    }

    // 전달받은 TR ID가 체결가 실시간 데이터인지 확인한다.
    public static boolean isTradeTrId(String trId) {
        return REGULAR_TRADE.equals(trId)
                || AFTER_HOURS_TRADE.equals(trId)
                || NXT_TRADE.equals(trId)
                || UNIFIED_TRADE.equals(trId);
    }

    // 전달받은 TR ID가 호가 실시간 데이터인지 확인한다.
    public static boolean isOrderbookTrId(String trId) {
        return REGULAR_ORDERBOOK.equals(trId)
                || AFTER_HOURS_ORDERBOOK.equals(trId)
                || NXT_ORDERBOOK.equals(trId)
                || UNIFIED_ORDERBOOK.equals(trId);
    }

    // TR ID가 시간외 단일가 데이터인지 확인한다.
    public static boolean isAfterHoursTrId(String trId) {
        return AFTER_HOURS_TRADE.equals(trId) || AFTER_HOURS_ORDERBOOK.equals(trId);
    }

    // 전달받은 TR ID가 NXT 단독 또는 KRX·NXT 통합 실시간 데이터인지 확인한다.
    public static boolean isNxtOrUnifiedTrId(String trId) {
        return NXT_TRADE.equals(trId)
                || NXT_ORDERBOOK.equals(trId)
                || UNIFIED_TRADE.equals(trId)
                || UNIFIED_ORDERBOOK.equals(trId);
    }

    // TR ID를 화면과 자동체결 로직에서 사용할 거래 세션 값으로 변환한다.
    public static MarketSession resolveMarketSession(String trId) {
        if (isAfterHoursTrId(trId)) {
            return MarketSession.AFTER_HOURS_SINGLE_PRICE;
        }

        return MarketSession.REGULAR;
    }
}
