// 회원가입, 로그인, 내정보 조회, 토큰 연장, 개인정보 수정을 처리하는 API 컨트롤러
package com.stock.mockstock.domain.user.controller;

import com.stock.mockstock.domain.user.dto.LoginRequest;
import com.stock.mockstock.domain.user.dto.LoginResponse;
import com.stock.mockstock.domain.user.dto.NicknameUpdateRequest;
import com.stock.mockstock.domain.user.dto.PasswordUpdateRequest;
import com.stock.mockstock.domain.user.dto.SignupRequest;
import com.stock.mockstock.domain.user.dto.TokenRefreshResponse;
import com.stock.mockstock.domain.user.dto.UserInfoResponse;
import com.stock.mockstock.domain.user.service.UserService;
import com.stock.mockstock.global.security.jwt.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    // 신규 회원가입 요청을 처리한다.
    @PostMapping("/signup")
    public String signup(@RequestBody @Valid SignupRequest request) {
        userService.signup(request);

        return "회원가입 성공";
    }

    // 로그인 성공 시 JWT를 반환한다.
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        String token = userService.login(request);

        return new LoginResponse(token);
    }

    // 현재 로그인한 사용자의 정보를 조회한다.
    @GetMapping("/me")
    public UserInfoResponse me(Authentication authentication) {
        return userService.getMyInfo(authentication.getName());
    }

    // 현재 로그인한 사용자의 닉네임을 변경한다.
    @PatchMapping("/me/nickname")
    public UserInfoResponse updateNickname(
            Authentication authentication,
            @RequestBody @Valid NicknameUpdateRequest request
    ) {
        return userService.updateNickname(authentication.getName(), request);
    }

    // 현재 로그인한 사용자의 비밀번호를 변경한다.
    @PatchMapping("/me/password")
    public void updatePassword(
            Authentication authentication,
            @RequestBody @Valid PasswordUpdateRequest request
    ) {
        userService.updatePassword(authentication.getName(), request);
    }

    // 로그인 사용자가 직접 연장 버튼을 눌렀을 때 새 JWT를 발급한다.
    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(Authentication authentication) {
        String token = jwtUtil.generateToken(authentication.getName());

        return new TokenRefreshResponse(token);
    }
}
