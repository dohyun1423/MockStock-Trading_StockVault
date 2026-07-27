// 일회용 토큰과 새 비밀번호를 검증해 비밀번호 재설정을 완료하는 요청 DTO다.
package com.stock.mockstock.domain.user.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmRequest {

    @NotBlank(message = "비밀번호 재설정 토큰은 필수입니다.")
    @Size(max = 128, message = "비밀번호 재설정 토큰이 너무 깁니다.")
    private String token;

    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Size(min = 8, max = ApplicationPolicy.MAX_PASSWORD_LENGTH, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    private String newPassword;
}
