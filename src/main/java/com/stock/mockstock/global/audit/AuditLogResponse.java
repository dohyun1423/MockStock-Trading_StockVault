// 관리자가 운영 이력을 조회할 때 반환하는 감사 로그 응답 DTO다.
package com.stock.mockstock.global.audit;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String userEmail,
        String action,
        String targetType,
        String targetId,
        String message,
        String metadata,
        String requestId,
        String clientIp,
        LocalDateTime createdAt
) {

    // 감사 로그 엔티티를 관리자 조회 응답으로 변환한다.
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getUserEmail(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getMessage(),
                auditLog.getMetadata(),
                auditLog.getRequestId(),
                auditLog.getClientIp(),
                auditLog.getCreatedAt()
        );
    }
}
