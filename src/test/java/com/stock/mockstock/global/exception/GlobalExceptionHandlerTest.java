package com.stock.mockstock.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @Test
    void missingFaviconReturns404WhileUnexpectedErrorsStillReturn500() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ResourceProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.errorId").isNotEmpty());
        mvc.perform(get("/unexpected-error")).andExpect(status().isInternalServerError());
    }

    @RestController
    static class ResourceProbeController {
        @GetMapping("/favicon.ico")
        public void favicon() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "favicon.ico", "/favicon.ico");
        }

        @GetMapping("/unexpected-error")
        public void unexpected() {
            throw new IllegalStateException("Test failure");
        }
    }
}
