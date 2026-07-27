// 종목 DB 조회를 담당하는 Repository
package com.stock.mockstock.domain.stock.repository;

import com.stock.mockstock.domain.stock.entity.Stock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    List<Stock> findByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(
            String name,
            String symbol,
            Pageable pageable
    );

    Optional<Stock> findFirstByNameIgnoreCase(String name);

    Optional<Stock> findFirstByNameContainingIgnoreCaseOrSymbolContainingIgnoreCase(
            String name,
            String symbol
    );

    Optional<Stock> findByName(String name);

    Optional<Stock> findBySymbol(String symbol);
}
