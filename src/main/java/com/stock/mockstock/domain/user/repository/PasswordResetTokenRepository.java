// 비밀번호 재설정 토큰의 조회, 발급 제한, 사용 처리를 담당하는 Repository다.
package com.stock.mockstock.domain.user.repository;

import com.stock.mockstock.domain.user.entity.PasswordResetToken;
import com.stock.mockstock.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // 전달받은 원본 토큰의 해시와 일치하는 재설정 토큰을 조회한다.
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // 새 토큰 발급 전에 사용되지 않은 기존 토큰 목록을 조회한다.
    List<PasswordResetToken> findAllByUserAndUsedAtIsNull(User user);

    // 같은 계정이 짧은 시간 안에 재설정 메일을 반복 요청했는지 확인한다.
    boolean existsByUserAndCreatedAtAfter(User user, LocalDateTime createdAt);
}
