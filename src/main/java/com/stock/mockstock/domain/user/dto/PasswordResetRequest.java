// 비밀번호 재설정 메일을 요청하는 이메일의 형식과 길이를 검증하는 DTO다.
package com.stock.mockstock.domain.user.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = ApplicationPolicy.MAX_EMAIL_LENGTH, message = "이메일이 너무 깁니다.")
    private String email;
}
