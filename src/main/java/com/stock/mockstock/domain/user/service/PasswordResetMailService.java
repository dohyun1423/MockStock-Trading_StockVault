// 비밀번호 재설정 일회용 링크를 사용자 이메일로 발송하는 서비스다.
package com.stock.mockstock.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender mailSender;

    @Value("${app.password-reset-url:http://localhost:8080/reset-password}")
    private String passwordResetUrl;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    // 원본 재설정 토큰이 포함된 일회용 링크를 요청한 이메일로 발송한다.
    public void sendPasswordResetMail(String email, String rawToken) {
        String resetLink = UriComponentsBuilder
                .fromUriString(passwordResetUrl)
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();

        if (senderEmail != null && !senderEmail.isBlank()) {
            message.setFrom(senderEmail);
        }

        message.setTo(email);
        message.setSubject("[StockVault] 비밀번호 재설정 안내");
        message.setText("""
                StockVault 비밀번호 재설정 요청이 접수되었습니다.

                아래 링크에서 15분 안에 새 비밀번호를 설정해 주세요.
                %s

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(resetLink));

        mailSender.send(message);
    }
}
