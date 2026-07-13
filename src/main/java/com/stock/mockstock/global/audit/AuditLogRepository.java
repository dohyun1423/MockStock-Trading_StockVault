// 감사 로그를 DB에 저장하고 조회하는 Repository
package com.stock.mockstock.global.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
