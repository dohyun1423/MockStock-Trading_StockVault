// 기존 시장 세션 컬럼의 형식에 따라 필요한 DB 보정만 실행되는지 검증하는 테스트다.
package com.stock.mockstock.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketSessionColumnInitializerTest {

    // 기존 ENUM 컬럼이면 VARCHAR(32) 변경 SQL을 실행하는지 검증한다.
    @Test
    void migrateLegacyMarketSessionColumn() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MarketSessionColumnInitializer initializer =
                new MarketSessionColumnInitializer(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(32)
        )).thenReturn(0);

        initializer.run(new DefaultApplicationArguments());

        verify(jdbcTemplate).execute(
                "alter table stock_orders modify column market_session varchar(32) not null"
        );
    }

    // 이미 충분한 길이의 VARCHAR 컬럼이면 불필요한 ALTER TABLE을 실행하지 않는지 검증한다.
    @Test
    void skipCompatibleMarketSessionColumn() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MarketSessionColumnInitializer initializer =
                new MarketSessionColumnInitializer(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(32)
        )).thenReturn(1);

        initializer.run(new DefaultApplicationArguments());

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
