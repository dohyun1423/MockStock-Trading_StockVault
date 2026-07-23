// JWT 생성과 검증을 담당하는 유틸 클래스
package com.stock.mockstock.global.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long ACCESS_TOKEN_EXPIRATION_TIME = 60 * 60 * 1000L;
    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    @Value("${jwt.secret}")
    private String secretKey;

    private Key key;

    // secretKey로 JWT 서명에 사용할 Key 생성
    @PostConstruct
    public void init() {
        key = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    // 이메일과 현재 인증 버전을 담아 60분 동안 유효한 JWT를 생성한다.
    public String generateToken(String email, Long tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION_TIME);

        return Jwts.builder()
                .setSubject(email)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion == null ? 0L : tokenVersion)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 토큰에서 사용자 이메일 추출
    public String getEmailFromToken(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // 토큰에 저장된 사용자 인증 버전을 반환하며 이전 토큰은 초기 버전 0으로 처리한다.
    public Long getTokenVersionFromToken(String token) {
        Number tokenVersion = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get(TOKEN_VERSION_CLAIM, Number.class);

        return tokenVersion == null ? 0L : tokenVersion.longValue();
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {

        try {

            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}
