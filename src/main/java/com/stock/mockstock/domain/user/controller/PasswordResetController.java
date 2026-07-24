// 비로그인 사용자의 비밀번호 재설정 메일 요청과 새 비밀번호 설정 API를 제공한다.
package com.stock.mockstock.domain.user.controller;

import com.stock.mockstock.domain.user.dto.PasswordResetConfirmRequest;
import com.stock.mockstock.domain.user.dto.PasswordResetRequest;
import com.stock.mockstock.domain.user.dto.PasswordResetResponse;
import com.stock.mockstock.domain.user.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/password-reset")
public class PasswordResetController {

    private static final String REQUEST_MESSAGE =
            "가입된 이메일이라면 비밀번호 재설정 링크를 전송했습니다.";

    private final PasswordResetService passwordResetService;

    // 가입 여부와 관계없이 동일한 응답을 반환하며 재설정 메일 발송을 요청한다.
    @PostMapping("/request")
    public PasswordResetResponse request(@RequestBody @Valid PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(request);

        return new PasswordResetResponse(REQUEST_MESSAGE);
    }

    // 유효한 일회용 토큰으로 새 비밀번호 설정을 완료한다.
    @PostMapping("/confirm")
    public PasswordResetResponse confirm(@RequestBody @Valid PasswordResetConfirmRequest request) {
        passwordResetService.confirmPasswordReset(request);

        return new PasswordResetResponse("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.");
    }
}
