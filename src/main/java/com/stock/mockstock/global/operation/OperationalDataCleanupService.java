// 보존 기간이 지난 감사 로그와 비밀번호 재설정 토큰을 정기적으로 정리한다.
package com.stock.mockstock.global.operation;

import com.stock.mockstock.domain.user.repository.PasswordResetTokenRepository;
import com.stock.mockstock.global.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalDataCleanupService {

    private static final int AUDIT_LOG_RETENTION_DAYS = 180;
    private static final int RESET_TOKEN_RETENTION_DAYS = 1;

    private final AuditLogRepository auditLogRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    // 매일 새벽에 운영 추적 보존 기간이 지난 데이터만 삭제한다.
    @Scheduled(cron = "${app.cleanup.cron:0 20 3 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void cleanupExpiredOperationalData() {
        LocalDateTime now = LocalDateTime.now();
        long deletedAuditLogs = auditLogRepository.deleteByCreatedAtBefore(
                now.minusDays(AUDIT_LOG_RETENTION_DAYS)
        );
        long deletedResetTokens = passwordResetTokenRepository.deleteByExpiresAtBefore(
                now.minusDays(RESET_TOKEN_RETENTION_DAYS)
        );

        if (deletedAuditLogs > 0 || deletedResetTokens > 0) {
            log.info(
                    "Operational data cleanup completed. auditLogs={}, resetTokens={}",
                    deletedAuditLogs,
                    deletedResetTokens
            );
        }
    }
}
