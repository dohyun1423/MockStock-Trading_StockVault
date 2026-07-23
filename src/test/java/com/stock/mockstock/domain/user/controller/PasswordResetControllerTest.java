// PasswordResetController가 재설정 요청과 확인을 서비스로 정확히 전달하는지 검증한다.
package com.stock.mockstock.domain.user.controller;

import com.stock.mockstock.domain.user.dto.PasswordResetConfirmRequest;
import com.stock.mockstock.domain.user.dto.PasswordResetRequest;
import com.stock.mockstock.domain.user.dto.PasswordResetResponse;
import com.stock.mockstock.domain.user.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetController passwordResetController;

    // 재설정 메일 요청은 가입 여부를 노출하지 않는 공통 메시지를 반환하는지 검증한다.
    @Test
    void requestPasswordReset() {
        PasswordResetRequest request = new PasswordResetRequest("test@example.com");

        PasswordResetResponse response = passwordResetController.request(request);

        verify(passwordResetService).requestPasswordReset(request);
        assertThat(response.getMessage()).contains("가입된 이메일이라면");
    }

    // 재설정 확인 요청은 서비스 처리 후 새 비밀번호 로그인 안내를 반환하는지 검증한다.
    @Test
    void confirmPasswordReset() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("token", "newPassword12!");

        PasswordResetResponse response = passwordResetController.confirm(request);

        verify(passwordResetService).confirmPasswordReset(request);
        assertThat(response.getMessage()).contains("새 비밀번호로 로그인");
    }
}
