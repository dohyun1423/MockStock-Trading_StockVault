// 공개 인증 API와 주요 변경 API의 IP별 요청 횟수를 제한하는 단일 서버용 필터다.
package com.stock.mockstock.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestRateLimitFilter extends OncePerRequestFilter {

    private static final int CLEANUP_INTERVAL = 1_000;
    private static final long STALE_ENTRY_MILLIS = Duration.ofHours(1).toMillis();

    private final SecurityErrorResponseWriter errorResponseWriter;
    private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestSequence = new AtomicLong();

    // 보호 대상 요청의 허용 횟수를 확인하고 초과하면 429 응답을 반환한다.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitRule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        String key = request.getRemoteAddr() + ':' + request.getMethod() + ':' + rule.name();
        RequestCounter counter = counters.compute(key, (ignored, previous) ->
                createNextCounter(previous, now, rule.windowMillis())
        );

        cleanupStaleCounters(now);

        if (counter.count() > rule.maxRequests()) {
            long retryAfterSeconds = Math.max(1L, (counter.startedAt() + rule.windowMillis() - now) / 1_000L);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            errorResponseWriter.write(
                    response,
                    429,
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    // 요청 경로와 메소드에 맞는 호출 제한 정책을 반환한다.
    private RateLimitRule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("POST".equals(method) && "/api/users/login".equals(path)) {
            return new RateLimitRule("login", 10, Duration.ofMinutes(5).toMillis());
        }
        if ("POST".equals(method) && "/api/users/signup".equals(path)) {
            return new RateLimitRule("signup", 5, Duration.ofMinutes(10).toMillis());
        }
        if ("POST".equals(method) && path.startsWith("/api/users/password-reset/")) {
            return new RateLimitRule("password-reset", 5, Duration.ofMinutes(10).toMillis());
        }
        if ("POST".equals(method) && "/api/users/refresh".equals(path)) {
            return new RateLimitRule("token-refresh", 10, Duration.ofMinutes(1).toMillis());
        }
        if (path.startsWith("/api/orders/")) {
            return new RateLimitRule("orders", 60, Duration.ofMinutes(1).toMillis());
        }
        if (path.startsWith("/api/stocks/")) {
            return new RateLimitRule("stocks", 180, Duration.ofMinutes(1).toMillis());
        }
        return null;
    }

    // 현재 창이 만료되었으면 새 카운터를 만들고 아니면 요청 횟수를 하나 증가시킨다.
    private RequestCounter createNextCounter(RequestCounter previous, long now, long windowMillis) {
        if (previous == null || now - previous.startedAt() >= windowMillis) {
            return new RequestCounter(now, 1);
        }
        return new RequestCounter(previous.startedAt(), previous.count() + 1);
    }

    // 일정 요청마다 오래된 IP 카운터를 제거해 메모리가 계속 증가하지 않도록 한다.
    private void cleanupStaleCounters(long now) {
        if (requestSequence.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        counters.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > STALE_ENTRY_MILLIS);
    }

    // 경로별 최대 요청 횟수와 제한 시간을 표현한다.
    private record RateLimitRule(String name, int maxRequests, long windowMillis) {
    }

    // IP별 제한 시간 시작 시각과 누적 요청 횟수를 표현한다.
    private record RequestCounter(long startedAt, int count) {
    }
}
