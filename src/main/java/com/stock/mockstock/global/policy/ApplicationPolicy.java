// 입력값과 사용자별 데이터 증가를 제한하는 애플리케이션 공통 운영 정책을 관리한다.
package com.stock.mockstock.global.policy;

public final class ApplicationPolicy {

    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_NICKNAME_LENGTH = 16;
    public static final int MAX_SYMBOL_LENGTH = 12;
    public static final int MAX_STOCK_NAME_LENGTH = 100;
    public static final int MAX_SEARCH_KEYWORD_LENGTH = 50;
    public static final int MAX_SEARCH_RESULTS = 20;
    public static final int MAX_WATCHLIST_COUNT = 50;
    public static final int MAX_OPEN_ORDER_COUNT = 100;
    public static final int MAX_ORDER_QUANTITY = 1_000_000;
    public static final long MAX_ORDER_PRICE = 1_000_000_000L;
    public static final int MAX_TRADE_HISTORY_RESULTS = 100;
    public static final int MAX_CHART_CACHE_ENTRIES = 512;
    public static final int MAX_REALTIME_SNAPSHOT_ENTRIES = 5_000;
    public static final int MAX_WEBSOCKET_SESSIONS_PER_SUBJECT = 5;
    public static final int MAX_ADMIN_PAGE_SIZE = 100;

    // 상수 전용 클래스의 인스턴스 생성을 막는다.
    private ApplicationPolicy() {
    }
}
