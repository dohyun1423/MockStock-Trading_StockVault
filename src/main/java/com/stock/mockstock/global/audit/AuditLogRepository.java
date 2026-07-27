// 감사 로그를 DB에 저장하고 조회하는 Repository
package com.stock.mockstock.global.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // 관리자 검색 조건이 비어 있으면 전체 로그를, 값이 있으면 일치하는 감사 로그만 조회한다.
    @Query("""
            select a
            from AuditLog a
            where (:email is null or a.userEmail = :email)
              and (:action is null or a.action = :action)
              and (:requestId is null or a.requestId = :requestId)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(
            @Param("email") String email,
            @Param("action") String action,
            @Param("requestId") String requestId,
            Pageable pageable
    );

    // 보존 기간이 지난 감사 로그를 일괄 삭제한다.
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
