// 공개 로그인 API의 IP별 요청 제한과 429 응답을 검증한다.
package com.stock.mockstock.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimitFilterTest {

    // 같은 IP가 5분 안에 로그인 API를 10회 초과 호출하면 429 응답을 반환해야 한다.
    @Test
    @DisplayName("로그인 API는 같은 IP에서 5분당 10회까지만 허용한다")
    void rejectsLoginRequestAfterRateLimit() throws Exception {
        SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter(
                new ObjectMapper().findAndRegisterModules()
        );
        RequestRateLimitFilter filter = new RequestRateLimitFilter(writer);
        AtomicInteger passedRequestCount = new AtomicInteger();
        FilterChain filterChain = (request, response) -> passedRequestCount.incrementAndGet();

        MockHttpServletResponse lastResponse = null;
        for (int index = 0; index < 11; index++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/login");
            request.setRemoteAddr("127.0.0.1");
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(request, lastResponse, filterChain);
        }

        assertThat(passedRequestCount).hasValue(10);
        assertThat(lastResponse).isNotNull();
        assertThat(lastResponse.getStatus()).isEqualTo(429);
        assertThat(lastResponse.getContentAsString()).contains("요청이 너무 많습니다.");
    }
}
