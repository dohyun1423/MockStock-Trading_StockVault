// 로그인 이메일과 비밀번호의 필수값 및 최대 길이를 검증하는 요청 DTO다.
package com.stock.mockstock.domain.user.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class LoginRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Size(max = ApplicationPolicy.MAX_EMAIL_LENGTH, message = "이메일이 너무 깁니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(max = ApplicationPolicy.MAX_PASSWORD_LENGTH, message = "비밀번호가 너무 깁니다.")
    private String password;
}
