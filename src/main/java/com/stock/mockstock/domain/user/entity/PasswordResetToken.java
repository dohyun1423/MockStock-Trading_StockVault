// 사용자의 비밀번호 재설정을 위한 일회용 토큰 해시와 만료 상태를 관리하는 엔티티다.
package com.stock.mockstock.domain.user.entity;

import com.stock.mockstock.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 이메일로 발송한 원본 토큰 대신 SHA-256 해시만 저장한다.
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    // 현재 시각을 기준으로 토큰이 만료되지 않았고 아직 사용되지 않았는지 확인한다.
    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    // 재설정 완료 또는 새 토큰 발급 시 기존 토큰을 다시 사용할 수 없게 처리한다.
    public void markUsed(LocalDateTime usedAt) {
        if (this.usedAt == null) {
            this.usedAt = usedAt;
        }
    }
}
