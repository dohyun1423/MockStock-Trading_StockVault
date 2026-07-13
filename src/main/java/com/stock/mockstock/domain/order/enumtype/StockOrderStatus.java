// 예약 주문의 현재 처리 상태를 나타내는 enum이다.
package com.stock.mockstock.domain.order.enumtype;

// 사용자가 넣은 주문의 현재 처리 상태를 구분한다.
public enum StockOrderStatus {

    // 아직 체결되지 않고 대기 중인 주문
    PENDING,

    // 일부 수량만 체결되고 남은 수량이 있는 주문
    PARTIALLY_FILLED,

    // 주문 수량이 모두 체결된 주문
    FILLED,

    // 사용자가 취소한 주문
    CANCELED,

    // 체결 처리 중 현금/수량 부족 등으로 실패한 주문
    FAILED
}
