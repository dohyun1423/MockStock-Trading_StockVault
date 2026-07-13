// KIS 실시간 호가 WebSocket 메시지의 한 단계 호가 정보를 담는 DTO
package com.stock.mockstock.domain.stock.realtime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KisRealtimeOrderbookLevel {

    private int level;
    private long askPrice;
    private long askQuantity;
    private long bidPrice;
    private long bidQuantity;
}