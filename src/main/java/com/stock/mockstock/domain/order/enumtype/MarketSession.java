// 국내 주식 거래 시간대를 주문 처리 정책에 맞게 구분하는 enum이다.
package com.stock.mockstock.domain.order.enumtype;

// 현재 시간이 어떤 거래 세션에 속하는지 구분한다.
public enum MarketSession {

    // 장전 주문 접수 시간
    PRE_MARKET,

    // 장 시작 전 동시호가 시간
    OPENING_AUCTION,

    // 정규장 연속매매 시간
    REGULAR,

    // 장 마감 전 동시호가 시간
    CLOSING_AUCTION,

    // 장후 시간외 전 대기 시간
    AFTER_MARKET_WAIT,

    // 장후 시간외 종가 거래 시간
    AFTER_MARKET_CLOSING_PRICE,

    // 시간외 단일가 거래 시간
    AFTER_HOURS_SINGLE_PRICE,

    // 다음 거래 세션을 위한 예약 주문 시간
    RESERVATION,

    // 주문을 받을 수 없는 시간
    CLOSED
}
