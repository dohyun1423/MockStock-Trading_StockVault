// 이메일별 로그인 실패 횟수를 추적해 반복 대입 공격을 일시적으로 차단한다.
package com.stock.mockstock.global.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURE_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private final Map<String, LoginAttempt> attempts = new ConcurrentHashMap<>();

    // 잠금 시간이 남아 있는 계정의 로그인 요청을 동일한 인증 실패 메시지로 차단한다.
    public void validateLoginAllowed(String email) {
        LoginAttempt attempt = attempts.get(email);
        if (attempt == null) {
            return;
        }

        if (attempt.lockedUntil() != null && attempt.lockedUntil().isAfter(Instant.now())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (attempt.lockedUntil() != null) {
            attempts.remove(email, attempt);
        }
    }

    // 로그인 실패를 누적하고 제한 횟수에 도달하면 계정을 짧은 시간 동안 잠근다.
    public void recordFailure(String email) {
        Instant now = Instant.now();
        attempts.compute(email, (key, previous) -> {
            int failureCount = previous == null ? 1 : previous.failureCount() + 1;
            Instant lockedUntil = failureCount >= MAX_FAILURE_COUNT ? now.plus(LOCK_DURATION) : null;
            return new LoginAttempt(failureCount, lockedUntil);
        });
    }

    // 로그인 성공 시 누적된 실패 기록을 제거한다.
    public void recordSuccess(String email) {
        attempts.remove(email);
    }

    // 이메일별 실패 횟수와 잠금 만료 시각을 보관한다.
    private record LoginAttempt(int failureCount, Instant lockedUntil) {
    }
}
