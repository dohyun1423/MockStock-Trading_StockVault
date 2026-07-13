// 보유 주식 DB 조회를 담당하는 Repository
package com.stock.mockstock.domain.portfolio.repository;

import com.stock.mockstock.domain.portfolio.entity.Holding;
import com.stock.mockstock.domain.stock.entity.Stock;
import com.stock.mockstock.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByUserAndStock(User user, Stock stock);

    // 매도 예약/체결처럼 보유 수량이 바뀌는 흐름에서는 보유 종목 행을 쓰기 락으로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.user = :user and h.stock = :stock")
    Optional<Holding> findByUserAndStockForUpdate(
            @Param("user") User user,
            @Param("stock") Stock stock
    );

    List<Holding> findAllByUserOrderByCreatedAtDesc(User user);
}
