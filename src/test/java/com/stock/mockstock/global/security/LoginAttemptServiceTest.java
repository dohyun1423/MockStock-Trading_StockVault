// 반복 로그인 실패 잠금과 성공 후 실패 기록 초기화 정책을 검증한다.
package com.stock.mockstock.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private final LoginAttemptService loginAttemptService = new LoginAttemptService();

    // 다섯 번 연속 실패한 이메일은 잠금 시간 동안 로그인을 차단해야 한다.
    @Test
    @DisplayName("로그인 5회 연속 실패 시 계정을 5분 동안 잠근다")
    void locksEmailAfterFiveFailures() {
        String email = "locked@test.com";

        for (int index = 0; index < 5; index++) {
            loginAttemptService.recordFailure(email);
        }

        assertThatThrownBy(() -> loginAttemptService.validateLoginAllowed(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    // 로그인 성공 후에는 이전 실패 횟수를 제거해 정상적인 재로그인을 허용해야 한다.
    @Test
    @DisplayName("로그인 성공 시 기존 실패 기록을 초기화한다")
    void clearsFailuresAfterSuccessfulLogin() {
        String email = "success@test.com";

        for (int index = 0; index < 4; index++) {
            loginAttemptService.recordFailure(email);
        }
        loginAttemptService.recordSuccess(email);

        assertThatCode(() -> loginAttemptService.validateLoginAllowed(email))
                .doesNotThrowAnyException();
    }
}
