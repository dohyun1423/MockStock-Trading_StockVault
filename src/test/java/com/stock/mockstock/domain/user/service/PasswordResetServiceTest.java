// PasswordResetService의 토큰 발급, 계정 노출 방지, 만료 검증과 비밀번호 변경을 검증한다.
package com.stock.mockstock.domain.user.service;

import com.stock.mockstock.domain.user.dto.PasswordResetConfirmRequest;
import com.stock.mockstock.domain.user.dto.PasswordResetRequest;
import com.stock.mockstock.domain.user.entity.PasswordResetToken;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.enumtype.Role;
import com.stock.mockstock.domain.user.repository.PasswordResetTokenRepository;
import com.stock.mockstock.domain.user.repository.UserRepository;
import com.stock.mockstock.global.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordResetMailService passwordResetMailService;

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    // 가입된 계정은 원본 토큰을 메일로 보내고 DB에는 해시만 저장하는지 검증한다.
    @Test
    void requestPasswordResetStoresOnlyTokenHash() throws Exception {
        User user = createUser();
        PasswordResetRequest request = new PasswordResetRequest(user.getEmail());

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.existsByUserAndCreatedAtAfter(any(), any())).thenReturn(false);
        when(passwordResetTokenRepository.findAllByUserAndUsedAtIsNull(user)).thenReturn(List.of());

        passwordResetService.requestPasswordReset(request);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        verify(passwordResetMailService).sendPasswordResetMail(
                org.mockito.ArgumentMatchers.eq(user.getEmail()),
                rawTokenCaptor.capture()
        );

        String rawToken = rawTokenCaptor.getValue();
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(hash(rawToken));
        assertThat(tokenCaptor.getValue().getTokenHash()).doesNotContain(rawToken);
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    // 가입되지 않은 이메일도 예외 없이 처리하고 메일은 발송하지 않는지 검증한다.
    @Test
    void requestPasswordResetDoesNotRevealUnknownEmail() {
        PasswordResetRequest request = new PasswordResetRequest("unknown@example.com");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset(request);

        verify(passwordResetMailService, never()).sendPasswordResetMail(anyString(), anyString());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    // 유효한 토큰이면 새 비밀번호를 적용하고 토큰을 사용 처리하는지 검증한다.
    @Test
    void confirmPasswordResetChangesPasswordAndConsumesToken() {
        User user = createUser();
        String rawToken = "valid-reset-token";
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(rawToken, "newPassword12!");

        when(passwordResetTokenRepository.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(resetToken));
        when(passwordResetTokenRepository.findAllByUserAndUsedAtIsNull(user)).thenReturn(List.of(resetToken));

        passwordResetService.confirmPasswordReset(request);

        verify(userService).resetPassword(user.getEmail(), request.getNewPassword());
        assertThat(resetToken.getUsedAt()).isNotNull();
    }

    // 만료된 토큰은 비밀번호를 변경하지 않고 동일한 만료 오류를 반환하는지 검증한다.
    @Test
    void rejectExpiredPasswordResetToken() {
        User user = createUser();
        String rawToken = "expired-reset-token";
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(rawToken, "newPassword12!");

        when(passwordResetTokenRepository.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않거나 만료");
        verify(userService, never()).resetPassword(anyString(), anyString());
    }

    // 테스트에 사용할 기본 사용자 계정을 생성한다.
    private User createUser() {
        return User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded-password")
                .nickname("tester")
                .role(Role.USER)
                .cash(10_000_000L)
                .build();
    }

    // 테스트 원본 토큰을 서비스와 같은 SHA-256 해시로 변환한다.
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
