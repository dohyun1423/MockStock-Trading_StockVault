// 현재 거래 세션과 주문 가능 여부를 프론트에 전달하는 응답 DTO다.
package com.stock.mockstock.domain.order.dto;

import com.stock.mockstock.domain.order.enumtype.MarketSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MarketSessionResponse {

    // 현재 거래 세션 코드
    private MarketSession marketSession;

    // 주문 모달에 보여줄 거래 세션 이름
    private String displayName;

    // 현재 시간에 주문 접수가 가능한지 여부
    private boolean orderAvailable;

    // 현재가 조건이 맞으면 즉시 체결을 시도할 수 있는지 여부
    private boolean immediateExecution;

    // 즉시 체결 대신 미체결/예약 주문으로 접수 가능한지 여부
    private boolean reservationAvailable;

    // 사용자에게 보여줄 거래 세션 안내 문구
    private String message;
}
