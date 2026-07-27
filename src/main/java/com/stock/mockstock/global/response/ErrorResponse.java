// 사용자에게 안전한 오류 메시지와 추적 정보를 반환하는 공통 응답 객체다.
package com.stock.mockstock.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private String message;
    private String errorId;
    private String timestamp;

    // 사용자 메시지와 요청 추적 ID를 사용해 공통 오류 응답을 생성한다.
    public static ErrorResponse of(String message, String errorId) {
        return new ErrorResponse(
                message,
                errorId,
                ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toString()
        );
    }
}
