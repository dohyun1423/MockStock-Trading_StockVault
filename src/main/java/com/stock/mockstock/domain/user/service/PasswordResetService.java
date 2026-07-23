// 비밀번호 재설정 토큰 발급, 메일 전송, 토큰 검증과 비밀번호 변경을 처리하는 서비스다.
package com.stock.mockstock.domain.user.service;

import com.stock.mockstock.domain.user.dto.PasswordResetConfirmRequest;
import com.stock.mockstock.domain.user.dto.PasswordResetRequest;
import com.stock.mockstock.domain.user.entity.PasswordResetToken;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.repository.PasswordResetTokenRepository;
import com.stock.mockstock.domain.user.repository.UserRepository;
import com.stock.mockstock.global.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int TOKEN_EXPIRATION_MINUTES = 15;
    private static final int REQUEST_COOLDOWN_SECONDS = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailService passwordResetMailService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    // 가입 여부를 외부에 노출하지 않고 존재하는 계정에만 재설정 메일을 발송한다.
    public void requestPasswordReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || isRequestCooldownActive(user)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        invalidateActiveTokens(user, now);

        String rawToken = createRawToken();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(now.plusMinutes(TOKEN_EXPIRATION_MINUTES))
                .build();

        passwordResetTokenRepository.save(resetToken);

        try {
            passwordResetMailService.sendPasswordResetMail(user.getEmail(), rawToken);
            auditLogService.record(
                    user.getEmail(),
                    "PASSWORD_RESET_REQUESTED",
                    "USER",
                    String.valueOf(user.getId()),
                    "비밀번호 재설정 메일 발송",
                    null
            );
        } catch (RuntimeException e) {
            resetToken.markUsed(now);
            log.error("Password reset mail delivery failed. userId={}", user.getId(), e);
        }
    }

    // 일회용 토큰을 검증하고 비밀번호를 변경한 뒤 기존 JWT와 토큰을 무효화한다.
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(hashToken(request.getToken()))
                .orElseThrow(this::invalidTokenException);

        if (!resetToken.isUsable(now)) {
            throw invalidTokenException();
        }

        User user = resetToken.getUser();
        userService.resetPassword(user.getEmail(), request.getNewPassword());
        resetToken.markUsed(now);
        invalidateActiveTokens(user, now);

        auditLogService.record(
                user.getEmail(),
                "PASSWORD_RESET_COMPLETED",
                "USER",
                String.valueOf(user.getId()),
                "비밀번호 재설정 완료",
                null
        );
    }

    // 동일 계정의 재설정 메일 요청을 60초에 한 번으로 제한한다.
    private boolean isRequestCooldownActive(User user) {
        LocalDateTime cooldownStart = LocalDateTime.now().minusSeconds(REQUEST_COOLDOWN_SECONDS);

        return passwordResetTokenRepository.existsByUserAndCreatedAtAfter(user, cooldownStart);
    }

    // 같은 사용자의 이전 미사용 토큰을 모두 사용 처리한다.
    private void invalidateActiveTokens(User user, LocalDateTime usedAt) {
        passwordResetTokenRepository.findAllByUserAndUsedAtIsNull(user)
                .forEach(token -> token.markUsed(usedAt));
    }

    // 예측하기 어려운 256비트 URL-safe 원본 토큰을 생성한다.
    private String createRawToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    // 원본 토큰을 DB 저장과 조회에 사용할 SHA-256 해시 문자열로 변환한다.
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Password reset token hash algorithm is unavailable.", e);
        }
    }

    // 이메일 조회 기준을 맞추기 위해 앞뒤 공백을 제거한다.
    private String normalizeEmail(String email) {
        return String.valueOf(email).trim();
    }

    // 만료되거나 이미 사용된 토큰에 동일한 사용자 메시지를 제공한다.
    private IllegalArgumentException invalidTokenException() {
        return new IllegalArgumentException("비밀번호 재설정 링크가 유효하지 않거나 만료되었습니다.");
    }
}
