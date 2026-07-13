// 사용자가 이전에 사용한 비밀번호 해시를 저장해 비밀번호 재사용을 막는 엔티티
package com.stock.mockstock.domain.user.entity;

import com.stock.mockstock.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "user_password_histories")
public class UserPasswordHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 비밀번호 이력이 속한 사용자다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // BCrypt로 암호화된 과거 비밀번호 값이다.
    @Column(nullable = false)
    private String password;
}
