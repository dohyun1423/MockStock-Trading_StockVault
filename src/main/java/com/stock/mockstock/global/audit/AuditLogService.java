// 주요 사용자 작업을 감사 로그로 저장하는 공통 서비스
package com.stock.mockstock.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // 주요 작업 성공 기록을 별도 트랜잭션으로 저장한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String userEmail,
            String action,
            String targetType,
            String targetId,
            String message,
            String metadata
    ) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userEmail(userEmail)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .message(message)
                    .metadata(metadata)
                    .requestId(MDC.get("requestId"))
                    .clientIp(resolveClientIp())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (RuntimeException e) {
            log.warn("Audit log save failed. action={}, targetType={}, targetId={}", action, targetType, targetId, e);
        }
    }

    // 현재 HTTP 요청의 실제 연결 IP를 가져오고 요청 밖의 작업이면 null을 반환한다.
    private String resolveClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
