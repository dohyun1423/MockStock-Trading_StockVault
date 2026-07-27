// Spring Security에서 발생한 인증·인가 오류를 공통 JSON 응답으로 작성하는 도우미다.
package com.stock.mockstock.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.mockstock.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    // 인증 또는 권한 오류를 상세 내부 정보 없이 JSON 형태로 반환한다.
    public void write(HttpServletResponse response, int status, String message) throws IOException {
        String requestId = MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(message, requestId));
    }
}
