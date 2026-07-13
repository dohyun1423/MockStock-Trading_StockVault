// KIS 실시간 체결가 WebSocket 메시지를 화면 갱신에 필요한 값으로 변환한 DTO
package com.stock.mockstock.domain.stock.realtime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class KisRealtimeTradeMessage {

    private String symbol;
    private String tradeTime;
    private long currentPrice;
    private String changeSign;
    private long changePrice;
    private double changeRate;
    private long openPrice;
    private long highPrice;
    private long lowPrice;
    private long askPrice;
    private long bidPrice;
    private long tradeVolume;
    private long accumulatedVolume;
}