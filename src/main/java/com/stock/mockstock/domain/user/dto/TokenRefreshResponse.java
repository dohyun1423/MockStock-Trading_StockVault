// JWT 수동 연장 요청 결과로 새 토큰을 전달하는 응답 DTO다.
package com.stock.mockstock.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenRefreshResponse {

    private String token;
}
