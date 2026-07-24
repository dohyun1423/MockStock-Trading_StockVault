// 비밀번호 재설정 요청과 완료 결과 메시지를 전달하는 DTO다.
package com.stock.mockstock.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordResetResponse {

    private String message;
}
