// application.yml의 KIS API 설정값을 읽어오는 클래스
package com.stock.mockstock.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kis")
public class KisProperties {

    private String baseUrl;
    private String appKey;
    private String appSecret;
    private String provider;
    private String websocketUrl;
}