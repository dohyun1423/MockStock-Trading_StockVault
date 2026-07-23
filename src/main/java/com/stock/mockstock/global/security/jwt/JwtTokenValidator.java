// JWT 서명과 사용자 인증 버전을 함께 검사해 현재 유효한 사용자만 반환한다.
package com.stock.mockstock.global.security.jwt;

import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtTokenValidator {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    // JWT의 서명, 만료시간, subject, 사용자 tokenVersion이 모두 유효한지 확인한다.
    public Optional<User> getValidUser(String token) {
        if (token == null || token.isBlank() || !jwtUtil.validateToken(token)) {
            return Optional.empty();
        }

        try {
            String email = jwtUtil.getEmailFromToken(token);
            Long tokenVersion = jwtUtil.getTokenVersionFromToken(token);

            return userRepository.findByEmail(email)
                    .filter(user -> resolveTokenVersion(user).equals(tokenVersion));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    // 기존 사용자 데이터의 null 인증 버전을 초기 버전 0으로 처리한다.
    private Long resolveTokenVersion(User user) {
        return user.getTokenVersion() == null ? 0L : user.getTokenVersion();
    }
}
