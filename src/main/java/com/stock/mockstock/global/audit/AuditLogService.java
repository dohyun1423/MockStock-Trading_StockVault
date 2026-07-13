// 주요 사용자 작업을 감사 로그로 저장하는 공통 서비스
package com.stock.mockstock.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
                    .build();

            auditLogRepository.save(auditLog);
        } catch (RuntimeException e) {
            log.warn("Audit log save failed. action={}, targetType={}, targetId={}", action, targetType, targetId, e);
        }
    }
}
