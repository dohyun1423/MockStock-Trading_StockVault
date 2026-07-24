// 기존 주문 테이블의 시장 세션 컬럼을 새로운 세션 값도 저장할 수 있는 문자열 형식으로 보정한다.
package com.stock.mockstock.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSessionColumnInitializer implements ApplicationRunner {

    private static final int MARKET_SESSION_COLUMN_LENGTH = 32;

    private final JdbcTemplate jdbcTemplate;

    // 애플리케이션 시작 시 기존 MySQL ENUM 또는 짧은 문자열 컬럼을 VARCHAR(32)로 변환한다.
    @Override
    public void run(ApplicationArguments args) {
        if (isCompatibleColumn()) {
            return;
        }

        jdbcTemplate.execute(
                "alter table stock_orders "
                        + "modify column market_session varchar("
                        + MARKET_SESSION_COLUMN_LENGTH
                        + ") not null"
        );

        log.info(
                "Stock order market_session column migrated to varchar({}).",
                MARKET_SESSION_COLUMN_LENGTH
        );
    }

    // 현재 데이터베이스의 시장 세션 컬럼이 필요한 VARCHAR 길이를 이미 갖췄는지 확인한다.
    private boolean isCompatibleColumn() {
        Integer compatibleColumnCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from information_schema.columns
                 where table_schema = database()
                   and table_name = 'stock_orders'
                   and column_name = 'market_session'
                   and lower(data_type) = 'varchar'
                   and character_maximum_length >= ?
                """,
                Integer.class,
                MARKET_SESSION_COLUMN_LENGTH
        );

        return compatibleColumnCount != null && compatibleColumnCount > 0;
    }
}
