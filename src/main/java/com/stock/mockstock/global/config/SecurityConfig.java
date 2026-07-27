// Spring Security와 JWT 인증 방식을 설정하는 파일
package com.stock.mockstock.global.config;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.stock.mockstock.global.security.jwt.JwtAuthenticationFilter;
import com.stock.mockstock.global.security.SecurityErrorResponseWriter;
import jakarta.servlet.http.HttpServletResponse;

@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    @Bean
    // 비밀번호 암호화를 위한 BCryptPasswordEncoder 등록
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    // URL 권한과 JWT 필터 적용 순서 설정
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        // 관리자 API는 ADMIN 권한을 가진 사용자만 접근할 수 있다.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/",
                                "/error",
                                "/actuator/health",
                                "/api/users/signup",
                                "/api/users/login",
                                "/api/users/password-reset/request",
                                "/api/users/password-reset/confirm",
                                "/login",
                                "/signup",
                                "/forgot-password",
                                "/reset-password",
                                "/main",
                                "/stocks/detail",
                                "/portfolio",
                                "/ws/stocks",
                                "/ws/stocks/**",
                                "/ws/orders",
                                "/ws/orders/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .exceptionHandling(exception -> exception
                        // 인증 자체가 없거나 만료된 경우에는 프론트가 명확히 재로그인 처리할 수 있도록 401을 반환한다.
                        .authenticationEntryPoint((request, response, authException) ->
                                securityErrorResponseWriter.write(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "로그인이 필요하거나 인증 시간이 만료되었습니다."
                                )
                        )
                        // 인증은 되었지만 접근할 수 없는 요청은 토큰 삭제 대상이 아니므로 403으로만 반환한다.
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                securityErrorResponseWriter.write(
                                        response,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "해당 요청을 수행할 권한이 없습니다."
                                )
                        )
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .formLogin(form -> form.disable());

        return http.build();
    }
}
