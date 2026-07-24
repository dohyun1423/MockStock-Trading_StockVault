// 회원가입, 로그인, 내정보 조회, 닉네임 변경, 비밀번호 변경을 처리하는 서비스
package com.stock.mockstock.domain.user.service;

import com.stock.mockstock.domain.user.dto.LoginRequest;
import com.stock.mockstock.domain.user.dto.NicknameUpdateRequest;
import com.stock.mockstock.domain.user.dto.PasswordUpdateRequest;
import com.stock.mockstock.domain.user.dto.SignupRequest;
import com.stock.mockstock.domain.user.dto.UserInfoResponse;
import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.user.entity.UserPasswordHistory;
import com.stock.mockstock.domain.user.enumtype.Role;
import com.stock.mockstock.domain.user.repository.UserPasswordHistoryRepository;
import com.stock.mockstock.domain.user.repository.UserRepository;
import com.stock.mockstock.global.audit.AuditLogService;
import com.stock.mockstock.global.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserPasswordHistoryRepository userPasswordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;

    // 신규 회원을 생성하고 초기 비밀번호도 재사용 방지 이력에 저장한다.
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 존재하는 닉네임입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(Role.USER)
                .cash(10000000L)
                .build();

        // save가 반환한 영속 상태의 사용자를 비밀번호 이력과 연결한다.
        User savedUser = userRepository.save(user);
        savePasswordHistory(savedUser, encodedPassword);
    }

    // 이메일과 비밀번호를 검증하고 JWT를 발급한다.
    public String login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return jwtUtil.generateToken(user.getEmail(), user.getTokenVersion());
    }

    // 현재 사용자 인증 버전을 담은 새 JWT를 발급한다.
    @Transactional(readOnly = true)
    public String refreshToken(String email) {
        User user = getUser(email);

        return jwtUtil.generateToken(user.getEmail(), user.getTokenVersion());
    }

    // 현재 로그인한 사용자의 기본 정보를 조회한다.
    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(String email) {
        User user = getUser(email);

        return UserInfoResponse.from(user);
    }

    // 현재 로그인한 사용자의 닉네임을 중복 검증 후 변경한다.
    public UserInfoResponse updateNickname(String email, NicknameUpdateRequest request) {
        User user = getUser(email);
        String nickname = normalizeNickname(request.getNickname());

        if (user.getNickname().equals(nickname)) {
            return UserInfoResponse.from(user);
        }

        if (userRepository.existsByNicknameAndEmailNot(nickname, email)) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        user.updateNickname(nickname);
        auditLogService.record(
                email,
                "NICKNAME_UPDATED",
                "USER",
                user.getId() == null ? null : String.valueOf(user.getId()),
                "사용자 닉네임 변경",
                "nickname=" + nickname
        );

        return UserInfoResponse.from(user);
    }

    // 현재 비밀번호를 확인하고 이전에 사용한 적 없는 새 비밀번호로 변경한다.
    public void updatePassword(String email, PasswordUpdateRequest request) {
        User user = getUser(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        changePassword(user, request.getNewPassword());
        auditLogService.record(
                email,
                "PASSWORD_UPDATED",
                "USER",
                user.getId() == null ? null : String.valueOf(user.getId()),
                "사용자 비밀번호 변경",
                null
        );
    }

    // 유효한 이메일 재설정 토큰을 확인한 사용자의 비밀번호를 현재 비밀번호 없이 변경한다.
    public void resetPassword(String email, String newPassword) {
        User user = getUser(email);
        changePassword(user, newPassword);
    }

    // 새 비밀번호의 현재·과거 사용 여부를 검사하고 암호화, 이력 저장, JWT 무효화를 수행한다.
    private void changePassword(User user, String newPassword) {
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 사용 중인 비밀번호로는 변경할 수 없습니다.");
        }

        if (isPreviouslyUsedPassword(user, newPassword)) {
            throw new IllegalArgumentException("이전에 사용한 비밀번호로는 변경할 수 없습니다.");
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);
        user.updatePassword(encodedNewPassword);
        user.increaseTokenVersion();
        savePasswordHistory(user, encodedNewPassword);
    }

    // email을 기준으로 사용자를 조회한다.
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    // 닉네임 비교와 저장을 위해 앞뒤 공백을 제거한다.
    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임을 입력해 주세요.");
        }

        return nickname.trim();
    }

    // 새 비밀번호가 기존 비밀번호 이력에 포함되는지 확인한다.
    private boolean isPreviouslyUsedPassword(User user, String rawPassword) {
        List<UserPasswordHistory> histories = userPasswordHistoryRepository.findAllByUser(user);

        return histories.stream()
                .anyMatch(history -> passwordEncoder.matches(rawPassword, history.getPassword()));
    }

    // 암호화된 비밀번호를 재사용 방지 이력에 저장한다.
    private void savePasswordHistory(User user, String encodedPassword) {
        UserPasswordHistory history = UserPasswordHistory.builder()
                .user(user)
                .password(encodedPassword)
                .build();

        userPasswordHistoryRepository.save(history);
    }
}
