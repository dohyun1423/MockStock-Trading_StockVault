// PortfolioController가 로그인 사용자 email 기준으로 포트폴리오 서비스를 호출하는지 검증하는 테스트
package com.stock.mockstock.domain.portfolio.controller;

import com.stock.mockstock.domain.portfolio.dto.PortfolioResponse;
import com.stock.mockstock.domain.portfolio.service.PortfolioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PortfolioController portfolioController;

    @Test
    @DisplayName("내 포트폴리오 조회는 로그인 사용자 email을 서비스로 전달한다")
    void getMyPortfolio() {
        // given: 로그인 사용자와 포트폴리오 응답을 준비한다.
        PortfolioResponse expected = new PortfolioResponse(
                1_000_000L,
                200_000L,
                800_000L,
                1_500_000L,
                500_000L,
                450_000L,
                50_000L,
                BigDecimal.valueOf(11.11),
                List.of()
        );

        when(authentication.getName()).thenReturn("test@example.com");
        when(portfolioService.getMyPortfolio("test@example.com")).thenReturn(expected);

        // when: 포트폴리오 조회 API 메서드를 호출한다.
        PortfolioResponse response = portfolioController.getMyPortfolio(authentication);

        // then: 인증 email 기준으로 포트폴리오 서비스가 호출된다.
        assertThat(response).isSameAs(expected);
        verify(portfolioService).getMyPortfolio("test@example.com");
    }
}
