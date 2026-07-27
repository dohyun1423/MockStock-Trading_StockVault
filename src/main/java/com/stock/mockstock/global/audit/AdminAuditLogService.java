// 관리자용 감사 로그 검색 조건을 정규화하고 페이지 단위 조회를 제공한다.
package com.stock.mockstock.global.audit;

import com.stock.mockstock.global.policy.ApplicationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final AuditLogRepository auditLogRepository;

    // 이메일, 작업 유형, 요청 ID를 사용해 최신 감사 로그부터 제한된 크기로 조회한다.
    public Page<AuditLogResponse> search(
            String email,
            String action,
            String requestId,
            int page,
            int size
    ) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(
                Math.max(1, size),
                ApplicationPolicy.MAX_ADMIN_PAGE_SIZE
        );

        return auditLogRepository.search(
                        normalize(email),
                        normalizeUpperCase(action),
                        normalize(requestId),
                        PageRequest.of(normalizedPage, normalizedSize)
                )
                .map(AuditLogResponse::from);
    }

    // 빈 검색어는 선택 조건이 없는 null로 정규화한다.
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // 감사 작업 유형은 저장 형식과 동일하게 대문자로 정규화한다.
    private String normalizeUpperCase(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }
}
