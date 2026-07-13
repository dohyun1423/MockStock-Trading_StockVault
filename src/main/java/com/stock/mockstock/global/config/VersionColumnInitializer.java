// 기존 DB 데이터에 새로 추가된 JPA @Version 컬럼의 null 값을 보정하는 초기화 컴포넌트
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
public class VersionColumnInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    // @Version 컬럼 추가 전에 생성된 기존 row는 version이 null일 수 있으므로 시작 시 0으로 보정한다.
    @Override
    public void run(ApplicationArguments args) {
        int updatedUsers = updateVersionColumn("users");
        int updatedHoldings = updateVersionColumn("holdings");
        int updatedStockOrders = updateVersionColumn("stock_orders");

        if (updatedUsers + updatedHoldings + updatedStockOrders > 0) {
            log.info(
                    "Version columns initialized. users={}, holdings={}, stockOrders={}",
                    updatedUsers,
                    updatedHoldings,
                    updatedStockOrders
            );
        }
    }

    // 테이블이 아직 없거나 version 컬럼이 없는 초기 상태에서는 앱 실행을 막지 않도록 건너뛴다.
    private int updateVersionColumn(String tableName) {
        try {
            return jdbcTemplate.update("update " + tableName + " set version = 0 where version is null");
        } catch (RuntimeException e) {
            log.debug("Version column initialization skipped. table={}", tableName, e);
            return 0;
        }
    }
}
