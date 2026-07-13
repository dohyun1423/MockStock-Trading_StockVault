// 사용자의 주요 작업과 시스템 처리 결과를 복구 추적용으로 저장하는 감사 로그 엔티티
package com.stock.mockstock.global.audit;

import com.stock.mockstock.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_logs_user_email", columnList = "user_email"),
                @Index(name = "idx_audit_logs_target", columnList = "target_type,target_id")
        }
)
public class AuditLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작업을 수행한 사용자 email이며 시스템 작업이면 null일 수 있다.
    @Column(length = 120)
    private String userEmail;

    // ORDER_CREATED, ORDER_EXECUTED처럼 추적할 작업 유형이다.
    @Column(nullable = false, length = 60)
    private String action;

    // USER, ORDER, STOCK처럼 작업 대상의 도메인 유형이다.
    @Column(nullable = false, length = 60)
    private String targetType;

    // 작업 대상의 식별자이며 주문 id처럼 숫자 id가 있으면 문자열로 저장한다.
    @Column(length = 120)
    private String targetId;

    // 운영자가 빠르게 읽을 수 있는 요약 메시지다.
    @Column(nullable = false, length = 500)
    private String message;

    // 복구 판단에 필요한 부가 정보를 key=value 형태로 저장한다.
    @Column(length = 1000)
    private String metadata;
}
