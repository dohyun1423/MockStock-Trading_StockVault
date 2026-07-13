// KIS WebSocket 접속에 필요한 approval_key를 발급하고 만료 전까지 재사용하는 서비스
package com.stock.mockstock.domain.stock.kis;

import com.stock.mockstock.global.config.KisProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KisApprovalKeyService {

    private static final long APPROVAL_KEY_EXPIRE_HOURS = 23;

    private final KisProperties kisProperties;
    private final RestClient.Builder restClientBuilder;

    private String approvalKey;
    private LocalDateTime expiresAt;

    // 아직 유효한 웹소켓 접속키가 있으면 재사용하고, 없으면 KIS에서 새로 발급받는다.
    public synchronized String getApprovalKey() {
        if (isApprovalKeyUsable()) {
            return approvalKey;
        }

        KisApprovalKeyResponse response = requestApprovalKey();

        this.approvalKey = response.getApprovalKey();
        this.expiresAt = LocalDateTime.now().plusHours(APPROVAL_KEY_EXPIRE_HOURS);

        return approvalKey;
    }

    // KIS WebSocket 접속키 발급 API를 호출한다.
    private KisApprovalKeyResponse requestApprovalKey() {
        KisApprovalKeyRequest request = new KisApprovalKeyRequest(
                "client_credentials",
                kisProperties.getAppKey(),
                kisProperties.getAppSecret()
        );

        return restClientBuilder
                .baseUrl(kisProperties.getBaseUrl())
                .build()
                .post()
                .uri("/oauth2/Approval")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(request)
                .retrieve()
                .body(KisApprovalKeyResponse.class);
    }

    // 현재 저장된 approval_key가 아직 사용할 수 있는 상태인지 확인한다.
    private boolean isApprovalKeyUsable() {
        return approvalKey != null
                && expiresAt != null
                && LocalDateTime.now().isBefore(expiresAt);
    }

    private record KisApprovalKeyRequest(
            String grant_type,
            String appkey,
            String secretkey
    ) {
    }

    @Getter
    private static class KisApprovalKeyResponse {

        private String approval_key;

        public String getApprovalKey() {
            return approval_key;
        }
    }
}