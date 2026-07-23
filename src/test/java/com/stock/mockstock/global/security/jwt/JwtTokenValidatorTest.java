// JwtTokenValidator가 JWT 인증 버전과 DB 사용자 버전을 함께 검증하는지 확인한다.
package com.stock.mockstock.global.security.jwt;

import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenValidatorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtTokenValidator jwtTokenValidator;

    // JWT와 사용자 tokenVersion이 같으면 현재 유효한 사용자로 반환하는지 검증한다.
    @Test
    void returnUserWhenTokenVersionMatches() {
        User user = User.builder()
                .email("test@example.com")
                .password("password")
                .nickname("tester")
                .cash(1_000_000L)
                .tokenVersion(2L)
                .build();

        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("token")).thenReturn(user.getEmail());
        when(jwtUtil.getTokenVersionFromToken("token")).thenReturn(2L);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThat(jwtTokenValidator.getValidUser("token")).contains(user);
    }

    // 비밀번호 변경으로 사용자 tokenVersion이 증가했으면 기존 JWT를 거부하는지 검증한다.
    @Test
    void rejectTokenWhenTokenVersionDoesNotMatch() {
        User user = User.builder()
                .email("test@example.com")
                .password("password")
                .nickname("tester")
                .cash(1_000_000L)
                .tokenVersion(3L)
                .build();

        when(jwtUtil.validateToken("old-token")).thenReturn(true);
        when(jwtUtil.getEmailFromToken("old-token")).thenReturn(user.getEmail());
        when(jwtUtil.getTokenVersionFromToken("old-token")).thenReturn(2L);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThat(jwtTokenValidator.getValidUser("old-token")).isEmpty();
    }
}
