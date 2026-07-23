// UserController가 회원, 로그인, 내정보, 토큰 연장 요청을 올바른 서비스로 위임하는지 검증하는 테스트
package com.stock.mockstock.domain.user.controller;

import com.stock.mockstock.domain.user.dto.LoginRequest;
import com.stock.mockstock.domain.user.dto.LoginResponse;
import com.stock.mockstock.domain.user.dto.SignupRequest;
import com.stock.mockstock.domain.user.dto.TokenRefreshResponse;
import com.stock.mockstock.domain.user.dto.UserInfoResponse;
import com.stock.mockstock.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("회원가입 요청은 UserService로 전달한다")
    void signup() {
        // given: 회원가입 요청 DTO를 준비한다.
        SignupRequest request = new SignupRequest();

        // when: 회원가입 API 메서드를 호출한다.
        String response = userController.signup(request);

        // then: 회원가입 서비스가 호출되고 성공 문구가 반환된다.
        assertThat(response).isEqualTo("회원가입 성공");
        verify(userService).signup(request);
    }

    @Test
    @DisplayName("로그인 요청은 UserService 토큰 발급 결과를 반환한다")
    void login() {
        // given: 로그인 요청과 발급 토큰을 준비한다.
        LoginRequest request = new LoginRequest();

        when(userService.login(request)).thenReturn("access-token");

        // when: 로그인 API 메서드를 호출한다.
        LoginResponse response = userController.login(request);

        // then: 서비스에서 발급한 토큰이 응답으로 반환된다.
        assertThat(response.getToken()).isEqualTo("access-token");
        verify(userService).login(request);
    }

    @Test
    @DisplayName("내 정보 조회는 로그인 사용자 email을 서비스로 전달한다")
    void me() {
        // given: 로그인 사용자와 내 정보 응답을 준비한다.
        UserInfoResponse expected = new UserInfoResponse("test@example.com", "tester");

        when(authentication.getName()).thenReturn("test@example.com");
        when(userService.getMyInfo("test@example.com")).thenReturn(expected);

        // when: 내 정보 조회 API 메서드를 호출한다.
        UserInfoResponse response = userController.me(authentication);

        // then: 인증 email 기준으로 내 정보 서비스가 호출된다.
        assertThat(response).isSameAs(expected);
        verify(userService).getMyInfo("test@example.com");
    }

    @Test
    @DisplayName("토큰 연장 요청은 로그인 사용자 email로 새 JWT를 발급한다")
    void refresh() {
        // given: 로그인 사용자와 새 토큰을 준비한다.
        when(authentication.getName()).thenReturn("test@example.com");
        when(userService.refreshToken("test@example.com")).thenReturn("new-access-token");

        // when: 토큰 연장 API 메서드를 호출한다.
        TokenRefreshResponse response = userController.refresh(authentication);

        // then: 인증 email 기준으로 새 토큰이 발급된다.
        assertThat(response.getToken()).isEqualTo("new-access-token");
        verify(userService).refreshToken("test@example.com");
    }
}
