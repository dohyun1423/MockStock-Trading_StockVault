// Spring Boot 애플리케이션 컨텍스트가 정상 로드되는지 확인하는 기본 테스트다.
package com.stock.mockstock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "kis.provider=dummy")
class MockstockApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 전체 Spring 컨텍스트가 예외 없이 시작되는지 검증한다.
    @Test
    void contextLoads() {
    }

    // 애플리케이션 초기화 후 주문 시장 세션 컬럼이 신규 enum 값을 저장할 수 있는지 확인한다.
    @Test
    void marketSessionColumnSupportsNewSessionValues() {
        Integer compatibleColumnCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from information_schema.columns
                 where table_schema = database()
                   and table_name = 'stock_orders'
                   and column_name = 'market_session'
                   and lower(data_type) = 'varchar'
                   and character_maximum_length >= 32
                """,
                Integer.class
        );

        assertThat(compatibleColumnCount).isEqualTo(1);
    }

}
