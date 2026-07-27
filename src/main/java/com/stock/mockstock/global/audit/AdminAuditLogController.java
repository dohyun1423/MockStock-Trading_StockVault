// ADMIN 권한 사용자가 운영 감사 로그를 조건별로 조회하는 API를 제공한다.
package com.stock.mockstock.global.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    // 관리자 검색 조건과 페이지 정보를 받아 감사 로그 목록을 반환한다.
    @GetMapping
    public Page<AuditLogResponse> search(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminAuditLogService.search(email, action, requestId, page, size);
    }
}
