// 회원가입 이메일, 비밀번호, 닉네임의 형식과 길이를 검증하는 요청 DTO다.
package com.stock.mockstock.domain.user.dto;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignupRequest {

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @NotBlank(message = "이메일은 필수입니다.")
    @Size(max = ApplicationPolicy.MAX_EMAIL_LENGTH, message = "이메일이 너무 깁니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = ApplicationPolicy.MAX_PASSWORD_LENGTH, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = ApplicationPolicy.MAX_NICKNAME_LENGTH, message = "닉네임은 2자 이상 16자 이하여야 합니다.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9_-]+$", message = "닉네임에는 한글, 영문, 숫자, _, -만 사용할 수 있습니다.")
    private String nickname;
}
