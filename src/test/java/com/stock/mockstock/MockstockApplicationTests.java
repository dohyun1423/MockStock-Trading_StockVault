// Spring Boot 애플리케이션 컨텍스트가 정상 로드되는지 확인하는 기본 테스트다.
package com.stock.mockstock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MockstockApplicationTests {

    // 전체 Spring 컨텍스트가 예외 없이 시작되는지 검증한다.
    @Test
    void contextLoads() {
    }

}
