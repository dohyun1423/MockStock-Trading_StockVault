// 모든 HTTP 요청에 추적 ID와 기본 보안 헤더를 추가하고 처리 결과를 서버 로그에 남기는 필터다.
package com.stock.mockstock.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    // 요청마다 추적 ID를 발급하고 응답 상태와 처리 시간을 기록한다.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();

        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        applySecurityHeaders(response);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMillis = System.currentTimeMillis() - startedAt;
            if (shouldLogRequest(request, response, elapsedMillis)) {
                log.info(
                        "HTTP request completed. requestId={}, method={}, path={}, status={}, elapsedMs={}",
                        requestId,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsedMillis
                );
            }
            MDC.remove("requestId");
        }
    }

    // API 요청, 오류 응답, 지연 요청만 기록해 정적 파일 요청으로 로그가 과도하게 쌓이지 않게 한다.
    private boolean shouldLogRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            long elapsedMillis
    ) {
        return request.getRequestURI().startsWith("/api/")
                || response.getStatus() >= 400
                || elapsedMillis >= 2_000L;
    }

    // 브라우저가 불필요한 권한이나 외부 리소스를 사용하지 못하도록 기본 보안 헤더를 설정한다.
    private void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self' ws: wss:; "
                        + "object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
        );
    }
}
