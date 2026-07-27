// 사용자별 관심종목 조회, 정렬, 중복 확인, 삭제를 담당하는 Repository다.
package com.stock.mockstock.domain.watchlist.repository;

import com.stock.mockstock.domain.user.entity.User;
import com.stock.mockstock.domain.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    // 사용자의 관심종목을 정렬 순서와 생성 순서 기준으로 조회한다.
    @Query("""
            select w
            from Watchlist w
            where w.user = :user
            order by coalesce(w.sortOrder, 999999), w.createdAt asc
            """)
    List<Watchlist> findAllByUserOrderBySortOrder(User user);

    // 사용자의 관심종목 중 가장 큰 정렬 순서를 조회한다.
    @Query("""
            select max(w.sortOrder)
            from Watchlist w
            where w.user = :user
            """)
    Integer findMaxSortOrderByUser(User user);

    // 사용자가 같은 종목명을 이미 관심종목으로 등록했는지 확인한다.
    boolean existsByUserAndStockName(User user, String stockName);

    // 사용자가 저장한 관심종목 개수를 조회한다.
    long countByUser(User user);

    // 사용자의 관심종목에서 지정한 종목명을 삭제한다.
    void deleteByUserAndStockName(User user, String stockName);
}
