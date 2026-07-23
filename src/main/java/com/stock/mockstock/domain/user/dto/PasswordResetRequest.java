// 비밀번호 재설정 메일을 요청할 사용자 이메일을 받는 DTO다.
package com.stock.mockstock.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

    @NotBlank
    @Email
    private String email;
}
