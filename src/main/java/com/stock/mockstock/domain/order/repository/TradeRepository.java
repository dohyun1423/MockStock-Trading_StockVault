// 거래내역 DB 저장과 조회를 담당하는 Repository
package com.stock.mockstock.domain.order.repository;

import com.stock.mockstock.domain.order.entity.Trade;
import com.stock.mockstock.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findTop100ByUserOrderByCreatedAtDesc(User user);

    List<Trade> findTop100ByUserAndStockSymbolOrderByCreatedAtDesc(User user, String symbol);
}
