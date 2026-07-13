// KIS 실시간 호가 WebSocket 메시지를 화면 갱신에 필요한 값으로 변환한 DTO
package com.stock.mockstock.domain.stock.realtime;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KisRealtimeOrderbookMessage {

    private String symbol;
    private String businessTime;
    private String hourClassCode;
    private List<KisRealtimeOrderbookLevel> levels;
    private long totalAskQuantity;
    private long totalBidQuantity;
}